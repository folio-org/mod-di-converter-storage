package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.Record;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds all MARC records in memory before writing any output files.
 */
public class StrictRecordWriter {
  private static final Map<String, Set<String>> ENTITY_PREREQUISITES = Map.of(
    "ITEM", Set.of("INSTANCE", "HOLDINGS"),
    "HOLDINGS", Set.of("INSTANCE"),
    "INSTANCE", Set.of()
  );

  private final MarcFileSink marcFileSink;

  public StrictRecordWriter() {
    this(StrictRecordWriter::writeMarcFile);
  }

  StrictRecordWriter(MarcFileSink marcFileSink) {
    this.marcFileSink = marcFileSink;
  }

  public WriteResult write(
      CategorizedPaths paths,
      MinimalMarcRecordBuilder.ReferenceDataContext refData,
      Path outputBase) throws IOException {
    Path foundationFile = outputFile(outputBase, "-foundation.mrc");
    Path importFile = outputFile(outputBase, "-import.mrc");
    GeneratedRecords generated = buildRecords(paths, refData, foundationFile, importFile);

    if (generated.overallOutcome() instanceof GenerationOutcome.GeneratorGap) {
      deleteIfExists(foundationFile);
      deleteIfExists(importFile);
      return new WriteResult(
        generated.foundationRecords(),
        generated.importRecords(),
        generated.pathOutcomes(),
        generated.overallOutcome());
    }

    List<Path> tempFiles = new ArrayList<>();
    try {
      Path foundationTemp = null;
      Path importTemp = null;
      if (!generated.foundationRecords().isEmpty()) {
        foundationTemp = tempPath(foundationFile);
        tempFiles.add(foundationTemp);
        marcFileSink.write(foundationTemp, generated.foundationRecords());
      }
      if (!generated.importRecords().isEmpty()) {
        importTemp = tempPath(importFile);
        tempFiles.add(importTemp);
        marcFileSink.write(importTemp, generated.importRecords());
      }

      if (foundationTemp != null) {
        moveToFinal(foundationTemp, foundationFile);
        tempFiles.remove(foundationTemp);
      } else {
        deleteIfExists(foundationFile);
      }

      if (importTemp != null) {
        moveToFinal(importTemp, importFile);
        tempFiles.remove(importTemp);
      } else {
        deleteIfExists(importFile);
      }
    } catch (IOException | RuntimeException e) {
      cleanup(tempFiles, foundationFile, importFile);
      throw e;
    }

    return new WriteResult(
      generated.foundationRecords(),
      generated.importRecords(),
      generated.pathOutcomes(),
      generated.overallOutcome());
  }

  public List<CategorizedPath> pathOrder(CategorizedPaths paths) {
    List<CategorizedPath> ordered = new ArrayList<>();
    boolean isCreateOnlyProfile = paths.pairedPaths().isEmpty()
      && paths.unpairedUpdatePaths().isEmpty()
      && paths.deletePaths().isEmpty();

    for (MatchedPathPair pair : paths.pairedPaths()) {
      ordered.add(pair.updatePath());
      ordered.add(pair.createPath());
    }

    List<CategorizedPath> matchTriggered = paths.unpairedCreatePaths().stream()
      .filter(path -> path.reactTo() == ReactTo.MATCH)
      .toList();
    List<CategorizedPath> directCreate = paths.unpairedCreatePaths().stream()
      .filter(path -> path.reactTo() != ReactTo.MATCH)
      .toList();

    ordered.addAll(matchTriggered);
    if (isCreateOnlyProfile && matchTriggered.isEmpty()) {
      groupPathsByParentProfile(directCreate).values().forEach(ordered::addAll);
    } else {
      ordered.addAll(directCreate);
    }
    ordered.addAll(paths.unpairedUpdatePaths());
    ordered.addAll(paths.deletePaths());
    return ordered;
  }

  private GeneratedRecords buildRecords(
      CategorizedPaths paths,
      MinimalMarcRecordBuilder.ReferenceDataContext refData,
      Path foundationFile,
      Path importFile) {
    List<Record> foundationRecords = new ArrayList<>();
    List<Record> importRecords = new ArrayList<>();
    List<PathOutcome> outcomes = new ArrayList<>();
    int[] recordNumber = {0};
    int[] pathIndex = {0};
    boolean[] hasGap = {false};
    GenerationOutcome.GeneratorGap[] firstGap = {null};
    boolean isCreateOnlyProfile = paths.pairedPaths().isEmpty()
      && paths.unpairedUpdatePaths().isEmpty()
      && paths.deletePaths().isEmpty();
    List<CategorizedPath> allCreatePaths = allCreatePaths(paths);

    for (MatchedPathPair pair : paths.pairedPaths()) {
      attemptPath(pair.updatePath(), pathIndex[0]++, List.of(destination(foundationFile, "foundation"),
          destination(importFile, "import")), outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
        MatchCriteria matchCriteria = pair.updatePath().matchCriteria();
        Set<String> branchPrerequisites = getBranchPrerequisiteEntities(pair.updatePath(), allCreatePaths);
        MinimalMarcRecordBuilder.BuildResult foundation = MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(
          pair.updatePath().path(), ++recordNumber[0], null, refData, matchCriteria, branchPrerequisites);
        foundationRecords.add(foundation.record());
        MinimalMarcRecordBuilder.BuildResult update =
          MinimalMarcRecordBuilder.buildUpdateRecordFromBaseWithPrerequisites(
            foundation.record(), pair.updatePath().path(), ++recordNumber[0], null, refData, matchCriteria,
            branchPrerequisites);
        importRecords.add(update.record());
      });

      attemptPath(pair.createPath(), pathIndex[0]++, List.of(destination(importFile, "import")),
          outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
        MinimalMarcRecordBuilder.BuildResult create = MinimalMarcRecordBuilder.buildRecordForPath(
          pair.createPath().path(), ++recordNumber[0], null, refData, null);
        importRecords.add(create.record());
      });
    }

    List<CategorizedPath> matchTriggered = paths.unpairedCreatePaths().stream()
      .filter(path -> path.reactTo() == ReactTo.MATCH)
      .toList();
    List<CategorizedPath> directCreate = paths.unpairedCreatePaths().stream()
      .filter(path -> path.reactTo() != ReactTo.MATCH)
      .toList();

    for (CategorizedPath createPath : matchTriggered) {
      attemptPath(createPath, pathIndex[0]++, List.of(destination(foundationFile, "foundation"),
          destination(importFile, "import")), outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
        MatchCriteria matchCriteria = createPath.matchCriteria();
        String targetEntity = getTargetEntityFromPath(createPath.path());
        Set<String> prerequisites = new HashSet<>(getPrerequisiteEntities(targetEntity));
        prerequisites.addAll(getBranchPrerequisiteEntities(createPath, allCreatePaths));
        MinimalMarcRecordBuilder.BuildResult foundation = MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(
          createPath.path(), ++recordNumber[0], null, refData, matchCriteria, prerequisites);
        foundationRecords.add(foundation.record());
        MinimalMarcRecordBuilder.BuildResult update = MinimalMarcRecordBuilder.buildUpdateRecordFromBase(
          foundation.record(), createPath.path(), ++recordNumber[0], null, refData, matchCriteria);
        importRecords.add(update.record());
      });
    }

    if (isCreateOnlyProfile && matchTriggered.isEmpty()) {
      for (List<CategorizedPath> siblingPaths : groupPathsByParentProfile(directCreate).values()) {
        if (siblingPaths.isEmpty()) {
          continue;
        }
        attemptCreateOnlyGroup(siblingPaths, pathIndex, outcomes, hasGap, firstGap, recordNumber,
          importRecords, refData, importFile);
      }
    } else {
      for (CategorizedPath createPath : directCreate) {
        attemptPath(createPath, pathIndex[0]++, List.of(destination(importFile, "import")),
            outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
          Set<String> prerequisites = getBranchPrerequisiteEntities(createPath, allCreatePaths);
          MinimalMarcRecordBuilder.BuildResult result = MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(
            createPath.path(), ++recordNumber[0], null, refData, createPath.matchCriteria(), prerequisites);
          importRecords.add(result.record());
        });
      }
    }

    for (CategorizedPath updatePath : paths.unpairedUpdatePaths()) {
      attemptPath(updatePath, pathIndex[0]++, List.of(destination(foundationFile, "foundation"),
          destination(importFile, "import")), outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
        MatchCriteria matchCriteria = updatePath.matchCriteria();
        Set<String> branchPrerequisites = getBranchPrerequisiteEntities(updatePath, allCreatePaths);
        MinimalMarcRecordBuilder.BuildResult foundation = MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(
          updatePath.path(), ++recordNumber[0], null, refData, matchCriteria, branchPrerequisites);
        foundationRecords.add(foundation.record());
        MinimalMarcRecordBuilder.BuildResult update =
          MinimalMarcRecordBuilder.buildUpdateRecordFromBaseWithPrerequisites(
            foundation.record(), updatePath.path(), ++recordNumber[0], null, refData, matchCriteria,
            branchPrerequisites);
        importRecords.add(update.record());
      });
    }

    for (CategorizedPath deletePath : paths.deletePaths()) {
      attemptPath(deletePath, pathIndex[0]++, List.of(destination(foundationFile, "foundation"),
          destination(importFile, "import")), outcomes, hasGap, firstGap, foundationRecords, importRecords, () -> {
        MatchCriteria matchCriteria = deletePath.matchCriteria();
        MinimalMarcRecordBuilder.BuildResult foundation = MinimalMarcRecordBuilder.buildRecordForPath(
          deletePath.path(), ++recordNumber[0], null, refData, null);
        foundationRecords.add(foundation.record());
        MinimalMarcRecordBuilder.BuildResult delete = MinimalMarcRecordBuilder.buildDeleteRecordFromBase(
          foundation.record(), deletePath.path(), ++recordNumber[0], null, refData, matchCriteria);
        importRecords.add(delete.record());
      });
    }

    GenerationOutcome overall = hasGap[0] ? firstGap[0] : GenerationOutcome.Generated.INSTANCE;
    return new GeneratedRecords(foundationRecords, importRecords, outcomes, overall);
  }

  private void attemptCreateOnlyGroup(
      List<CategorizedPath> siblingPaths,
      int[] pathIndex,
      List<PathOutcome> outcomes,
      boolean[] hasGap,
      GenerationOutcome.GeneratorGap[] firstGap,
      int[] recordNumber,
      List<Record> importRecords,
      MinimalMarcRecordBuilder.ReferenceDataContext refData,
      Path importFile) {
    int firstIndex = pathIndex[0];
    for (int i = 0; i < siblingPaths.size(); i++) {
      pathIndex[0]++;
    }
    int importSize = importRecords.size();
    try {
      JobProfilePath consolidatedPath = consolidateCreatePaths(siblingPaths);
      MatchCriteria matchCriteria = siblingPaths.get(0).matchCriteria();
      MinimalMarcRecordBuilder.BuildResult result = MinimalMarcRecordBuilder.buildRecordForPath(
        consolidatedPath, ++recordNumber[0], null, refData, matchCriteria);
      importRecords.add(result.record());
      for (int i = 0; i < siblingPaths.size(); i++) {
        outcomes.add(pathOutcome(siblingPaths.get(i), firstIndex + i,
          List.of(destination(importFile, "import")), GenerationOutcome.Generated.INSTANCE));
      }
    } catch (GeneratorGapException e) {
      rollback(importRecords, importSize);
      hasGap[0] = true;
      for (int i = 0; i < siblingPaths.size(); i++) {
        CategorizedPath path = siblingPaths.get(i);
        GenerationOutcome.GeneratorGap gap = gapOutcome(path, firstIndex + i, e);
        if (firstGap[0] == null) {
          firstGap[0] = gap;
        }
        outcomes.add(pathOutcome(path, firstIndex + i, List.of(destination(importFile, "import")), gap));
      }
    }
  }

  private void attemptPath(
      CategorizedPath path,
      int pathIndex,
      List<PathOutcome.DestinationFile> destinationFiles,
      List<PathOutcome> outcomes,
      boolean[] hasGap,
      GenerationOutcome.GeneratorGap[] firstGap,
      List<Record> foundationRecords,
      List<Record> importRecords,
      RecordBuild build) {
    int foundationSize = foundationRecords.size();
    int importSize = importRecords.size();
    try {
      build.build();
      outcomes.add(pathOutcome(path, pathIndex, destinationFiles, GenerationOutcome.Generated.INSTANCE));
    } catch (GeneratorGapException e) {
      rollback(foundationRecords, foundationSize);
      rollback(importRecords, importSize);
      hasGap[0] = true;
      GenerationOutcome.GeneratorGap gap = gapOutcome(path, pathIndex, e);
      if (firstGap[0] == null) {
        firstGap[0] = gap;
      }
      outcomes.add(pathOutcome(path, pathIndex, destinationFiles, gap));
    }
  }

  private GenerationOutcome.GeneratorGap gapOutcome(CategorizedPath path, int pathIndex, GeneratorGapException e) {
    return new GenerationOutcome.GeneratorGap(pathIndex, path.path().getPathId(), e.reason().name(), e.getMessage());
  }

  private PathOutcome pathOutcome(
      CategorizedPath path,
      int pathIndex,
      List<PathOutcome.DestinationFile> destinationFiles,
      GenerationOutcome outcome) {
    return new PathOutcome(
      pathIndex,
      path.path().getPathId(),
      path.reactTo().name(),
      path.matchProfileId(),
      destinationFiles,
      List.of(),
      outcome);
  }

  private PathOutcome.DestinationFile destination(Path file, String role) {
    return new PathOutcome.DestinationFile(file.getFileName().toString(), role);
  }

  private Set<String> getPrerequisiteEntities(String targetEntity) {
    return ENTITY_PREREQUISITES.getOrDefault(targetEntity, Set.of());
  }

  private List<CategorizedPath> allCreatePaths(CategorizedPaths paths) {
    List<CategorizedPath> createPaths = new ArrayList<>(paths.unpairedCreatePaths());
    paths.pairedPaths().forEach(pair -> createPaths.add(pair.createPath()));
    return createPaths;
  }

  private Set<String> getBranchPrerequisiteEntities(
      CategorizedPath targetPath,
      List<CategorizedPath> createPaths) {
    Set<String> prerequisites = new HashSet<>();
    for (CategorizedPath createPath : createPaths) {
      if (createPath == targetPath) {
        continue;
      }
      if (createPath.reactTo() == ReactTo.NON_MATCH
          && pathCreatesRecordType(createPath.path(), "HOLDINGS")
          && !pathCreatesRecordType(targetPath.path(), "HOLDINGS")
          && sharesMatchAncestor(targetPath.path(), createPath.path())) {
        prerequisites.add("HOLDINGS");
      }
    }
    return prerequisites;
  }

  private boolean sharesMatchAncestor(JobProfilePath targetPath, JobProfilePath createPath) {
    List<Profile> targetProfiles = targetPath.getProfiles();
    List<Profile> createProfiles = createPath.getProfiles();
    int commonLength = Math.min(targetProfiles.size(), createProfiles.size());
    boolean hasCommonMatch = false;

    for (int i = 0; i < commonLength; i++) {
      if (!targetProfiles.get(i).equals(createProfiles.get(i))) {
        break;
      }
      if (targetProfiles.get(i) instanceof MatchProfileNode) {
        hasCommonMatch = true;
      }
    }
    return hasCommonMatch;
  }

  private String getTargetEntityFromPath(JobProfilePath path) {
    for (int i = path.getProfiles().size() - 1; i >= 0; i--) {
      Profile profile = path.getProfiles().get(i);
      if (profile instanceof ActionProfileNode actionProfile && "CREATE".equals(actionProfile.action())) {
        return actionProfile.folioRecord();
      }
    }
    return null;
  }

  private boolean pathCreatesRecordType(JobProfilePath path, String recordType) {
    return path.getProfiles().stream()
      .filter(ActionProfileNode.class::isInstance)
      .map(ActionProfileNode.class::cast)
      .anyMatch(actionProfile -> "CREATE".equals(actionProfile.action())
        && recordType.equals(actionProfile.folioRecord()));
  }

  private Map<String, List<CategorizedPath>> groupPathsByParentProfile(List<CategorizedPath> createPaths) {
    Map<String, List<CategorizedPath>> grouped = new LinkedHashMap<>();
    for (CategorizedPath catPath : createPaths) {
      String parentId = "unknown";
      if (catPath.path() != null && !catPath.path().getProfiles().isEmpty()) {
        Map<String, String> attributes = catPath.path().getProfiles().get(0).getAttributes();
        parentId = attributes.getOrDefault("id", "unknown");
      }
      grouped.computeIfAbsent(parentId, key -> new ArrayList<>()).add(catPath);
    }
    return grouped;
  }

  private JobProfilePath consolidateCreatePaths(List<CategorizedPath> createPaths) {
    List<Profile> consolidatedProfiles = new ArrayList<>();
    Set<String> seenProfileIds = new HashSet<>();
    for (CategorizedPath catPath : createPaths) {
      for (Profile profile : catPath.path().getProfiles()) {
        String profileKey = profile.getClass().getSimpleName() + "-" + profile.getName();
        if (profile instanceof ActionProfileNode actionProfile) {
          profileKey = "ActionProfile-" + actionProfile.action() + "-" + actionProfile.folioRecord();
        } else if (profile instanceof MappingProfileNode mappingProfile) {
          profileKey = "MappingProfile-" + mappingProfile.existingRecordType();
        }
        if (seenProfileIds.add(profileKey)) {
          consolidatedProfiles.add(profile);
        }
      }
    }
    return new JobProfilePath(consolidatedProfiles);
  }

  private static Path outputFile(Path outputBase, String suffix) {
    return outputBase.resolveSibling(outputBase.getFileName() + suffix);
  }

  private Path tempPath(Path finalPath) {
    return finalPath.resolveSibling(finalPath.getFileName() + ".tmp." + ProcessHandle.current().pid()
        + "." + System.nanoTime());
  }

  private static void writeMarcFile(Path path, List<Record> records) throws IOException {
    try (OutputStream out = Files.newOutputStream(path)) {
      MarcStreamWriter writer = new MarcStreamWriter(out, "UTF-8");
      for (Record record : records) {
        writer.write(record);
      }
      writer.close();
    }
  }

  private static void moveToFinal(Path temp, Path finalPath) throws IOException {
    try {
      Files.move(temp, finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(temp, finalPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static <T> void rollback(List<T> records, int size) {
    while (records.size() > size) {
      records.remove(records.size() - 1);
    }
  }

  private static void cleanup(List<Path> tempFiles, Path foundationFile, Path importFile) {
    tempFiles.forEach(StrictRecordWriter::deleteQuietly);
    deleteQuietly(foundationFile);
    deleteQuietly(importFile);
  }

  private static void deleteIfExists(Path path) throws IOException {
    Files.deleteIfExists(path);
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best-effort cleanup after a failed write.
    }
  }

  @FunctionalInterface
  interface MarcFileSink {
    void write(Path path, List<Record> records) throws IOException;
  }

  @FunctionalInterface
  private interface RecordBuild {
    void build();
  }

  public record WriteResult(
    List<Record> foundationRecords,
    List<Record> importRecords,
    List<PathOutcome> pathOutcomes,
    GenerationOutcome overallOutcome
  ) {}

  private record GeneratedRecords(
    List<Record> foundationRecords,
    List<Record> importRecords,
    List<PathOutcome> pathOutcomes,
    GenerationOutcome overallOutcome
  ) {}
}
