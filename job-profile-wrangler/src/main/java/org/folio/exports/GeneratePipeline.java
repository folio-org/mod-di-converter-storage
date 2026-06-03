package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.http.FolioClient;
import org.folio.foundation.FoundationSeedProfile;
import org.folio.profile.ProfileTree;
import org.folio.validation.ProfileShapeValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the generate command workflow behind one interface.
 */
public class GeneratePipeline {
  private static final Logger LOGGER = LogManager.getLogger(GeneratePipeline.class);

  private final FolioClient client;
  private final ReferenceDataProvider referenceDataProvider;
  private final Path outputBase;
  private final boolean verbose;
  private final GenerationReportWriter reportWriter;
  private final ProfilePathExtractor pathExtractor;
  private final StrictRecordWriter recordWriter;

  public GeneratePipeline(
      FolioClient client,
      ReferenceDataProvider referenceDataProvider,
      Path outputBase,
      boolean verbose) {
    this(client, referenceDataProvider, outputBase, verbose, new GenerationReportWriter(),
      new ProfilePathExtractor(verbose), new StrictRecordWriter());
  }

  GeneratePipeline(
      FolioClient client,
      ReferenceDataProvider referenceDataProvider,
      Path outputBase,
      boolean verbose,
      GenerationReportWriter reportWriter,
      ProfilePathExtractor pathExtractor,
      StrictRecordWriter recordWriter) {
    this.client = client;
    this.referenceDataProvider = referenceDataProvider;
    this.outputBase = outputBase;
    this.verbose = verbose;
    this.reportWriter = reportWriter;
    this.pathExtractor = pathExtractor;
    this.recordWriter = recordWriter;
  }

  public int generate(JsonNode snapshot, String fallbackProfileId) throws IOException {
    LOGGER.info("Generating minimal MARC records from scratch...");
    String runTimestamp = Instant.now().toString();
    ProfileTree tree = ProfileTree.fromSnapshot(snapshot);

    MappingRulesProcessor rulesProcessor = new MappingRulesProcessor(client);
    MappingRulesAnalysis analysis = rulesProcessor.fetchAndAnalyze("marc-bib");

    if (verbose) {
      LOGGER.info("Mapping analysis: {} mapped fields, {} required fields",
        analysis.getAllMappedInventoryFields().size(),
        analysis.getRequiredInventoryFields().size());
    }

    MinimalMarcRecordBuilder.ReferenceDataContext refData = referenceDataProvider.get();
    if (refData != null && verbose) {
      LOGGER.info("Reference data fetched for Holdings/Items: locationId={}, materialTypeId={}, loanTypeId={}",
        refData.locationId(), refData.materialTypeId(), refData.loanTypeId());
    }

    cleanupOutputFiles();

    if (analysis.getAllMappedInventoryFields().isEmpty()) {
      GeneratorGapException gap = GeneratorGapException.mappingRulesUnavailable("marc-bib");
      GenerationOutcome.GeneratorGap outcome = new GenerationOutcome.GeneratorGap(-1, null,
        gap.reason().name(), gap.getMessage());
      writeReport(tree, fallbackProfileId, runTimestamp, outcome, List.of(), refData);
      LOGGER.error("Generation outcome: {} - {}", GenerationOutcome.GENERATOR_GAP, gap.getMessage());
      return outcome.exitCode();
    }

    Optional<GenerationOutcome.BlockedUnsupportedWorkflow> blocked =
      ProfileShapeValidator.defaultValidator().validate(snapshot);
    if (blocked.isPresent()) {
      writeReport(tree, fallbackProfileId, runTimestamp, blocked.get(), List.of(), refData);
      LOGGER.error("Generation outcome: {} - {}", blocked.get().label(), blocked.get().message());
      return blocked.get().exitCode();
    }

    PathExtractionResult pathResult = pathExtractor.extractAllPaths(tree);

    if (!pathResult.unsupportedActionPaths().isEmpty()) {
      List<PathOutcome> unsupportedOutcomes = unsupportedActionOutcomes(pathResult.unsupportedActionPaths());
      GenerationOutcome.GeneratorGap outcome =
        (GenerationOutcome.GeneratorGap) unsupportedOutcomes.get(0).outcome();
      writeReport(tree, fallbackProfileId, runTimestamp, outcome, unsupportedOutcomes, refData);
      LOGGER.error("Generation outcome: {} - {}", outcome.label(), outcome.message());
      return outcome.exitCode();
    }

    if (pathResult.createPaths().isEmpty() && pathResult.updatePaths().isEmpty()
      && pathResult.deletePaths().isEmpty()) {
      GenerationOutcome.InvalidProfileShape outcome = new GenerationOutcome.InvalidProfileShape(
        "EMPTY_PATH", "No CREATE, UPDATE, or DELETE action paths found in job profile");
      writeReport(tree, fallbackProfileId, runTimestamp, outcome, List.of(), refData);
      LOGGER.warn("No CREATE or UPDATE action paths found in job profile");
      LOGGER.error("Generation outcome: {} - EMPTY_PATH", GenerationOutcome.INVALID_PROFILE_SHAPE);
      return outcome.exitCode();
    }

    LOGGER.info("Found {} CREATE path(s), {} UPDATE path(s), and {} DELETE path(s) in job profile",
      pathResult.createPaths().size(), pathResult.updatePaths().size(), pathResult.deletePaths().size());

    CategorizedPaths categorized = pathExtractor.categorizePaths(pathResult);

    LOGGER.info("Found {} matched path pair(s) and {} unpaired CREATE stack(s) after categorization",
      categorized.pairedPaths().size(), categorized.unpairedCreatePaths().size());

    List<CategorizedPath> allPaths = recordWriter.pathOrder(categorized);
    Map<Integer, GenerationOutcome.NeedsEnrichment> needsEnrichment =
      EnrichmentDetector.detect(allPaths, outputBase.toString());

    StrictRecordWriter.WriteResult result = recordWriter.write(categorized, refData, outputBase);
    List<PathOutcome> pathOutcomes = mergeEnrichmentOutcomes(result.pathOutcomes(), needsEnrichment);
    GenerationOutcome overallOutcome = mergedOverallOutcome(result.overallOutcome(), pathOutcomes);
    writeReport(tree, fallbackProfileId, runTimestamp, overallOutcome, pathOutcomes, refData);

    if (result.overallOutcome() instanceof GenerationOutcome.GeneratorGap gap) {
      LOGGER.error("Generation outcome: {} - {}", gap.label(), gap.message());
      return gap.exitCode();
    }

    if (!result.foundationRecords().isEmpty()) {
      LOGGER.info("Generated {} foundation record(s) written to {}",
        result.foundationRecords().size(),
        result.foundationFiles().stream()
          .map(Path::toString)
          .toList());
    }
    if (!result.importRecords().isEmpty()) {
      LOGGER.info("Generated {} record(s) for import written to {}",
        result.importRecords().size(), outputBase + "-import.mrc");
    }

    if (!needsEnrichment.isEmpty()) {
      LOGGER.warn("Generated pre-enrichment MARC records; {} path(s) require the enrich step before final import.",
        needsEnrichment.size());
      pathOutcomes.stream()
        .map(PathOutcome::outcome)
        .filter(GenerationOutcome.NeedsEnrichment.class::isInstance)
        .map(GenerationOutcome.NeedsEnrichment.class::cast)
        .map(GenerationOutcome.NeedsEnrichment::hint)
        .distinct()
        .forEach(hint -> LOGGER.warn("{}", hint));
    }

    return overallOutcome.exitCode();
  }

  private void writeReport(
      ProfileTree tree,
      String fallbackProfileId,
      String runTimestamp,
      GenerationOutcome overallOutcome,
      List<PathOutcome> paths,
      MinimalMarcRecordBuilder.ReferenceDataContext refData) throws IOException {
    reportWriter.write(outputBase, GenerationReport.of(
      tree.profileId(fallbackProfileId),
      tree.profileName(),
      runTimestamp,
      overallOutcome,
      paths,
      refData
    ), System.out, verbose);
  }

  private List<PathOutcome> mergeEnrichmentOutcomes(
      List<PathOutcome> pathOutcomes,
      Map<Integer, GenerationOutcome.NeedsEnrichment> needsEnrichment) {
    if (needsEnrichment.isEmpty()) {
      return pathOutcomes;
    }

    List<PathOutcome> merged = new ArrayList<>();
    for (PathOutcome pathOutcome : pathOutcomes) {
      GenerationOutcome outcome = pathOutcome.outcome();
      GenerationOutcome.NeedsEnrichment enrichment = needsEnrichment.get(pathOutcome.pathIndex());
      if (enrichment != null && !(outcome instanceof GenerationOutcome.GeneratorGap)) {
        outcome = enrichmentForImportRecord(enrichment, pathOutcome.importRecordNumber());
      }
      merged.add(new PathOutcome(
        pathOutcome.pathIndex(),
        pathOutcome.pathId(),
        pathOutcome.reactTo(),
        pathOutcome.matchProfileId(),
        pathOutcome.importRecordNumber(),
        pathOutcome.destinationFiles(),
        pathOutcome.fieldsWritten(),
        outcome
      ));
    }
    return merged;
  }

  private GenerationOutcome.NeedsEnrichment enrichmentForImportRecord(
      GenerationOutcome.NeedsEnrichment enrichment,
      Integer importRecordNumber) {
    if (importRecordNumber == null) {
      return enrichment;
    }
    return enrichment.withImportRecordNumber(importRecordNumber);
  }

  private GenerationOutcome mergedOverallOutcome(
      GenerationOutcome writerOutcome,
      List<PathOutcome> pathOutcomes) {
    if (writerOutcome instanceof GenerationOutcome.GeneratorGap) {
      return writerOutcome;
    }
    return pathOutcomes.stream()
      .map(PathOutcome::outcome)
      .filter(GenerationOutcome.NeedsEnrichment.class::isInstance)
      .findFirst()
      .orElse(writerOutcome);
  }

  private void cleanupOutputFiles() throws IOException {
    Files.deleteIfExists(Paths.get(outputBase + "-foundation.mrc"));
    Files.deleteIfExists(Paths.get(outputBase + "-import.mrc"));
    Files.deleteIfExists(Paths.get(outputBase + "-report.json"));
    for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
      Files.deleteIfExists(Paths.get(outputBase + "-foundation-" + seedProfile.name().toLowerCase() + ".mrc"));
    }
  }

  private List<PathOutcome> unsupportedActionOutcomes(List<CategorizedPath> unsupportedPaths) {
    List<PathOutcome> outcomes = new ArrayList<>();
    for (int pathIndex = 0; pathIndex < unsupportedPaths.size(); pathIndex++) {
      CategorizedPath path = unsupportedPaths.get(pathIndex);
      ActionProfileNode action = path.path().lastAction().orElseThrow();
      GeneratorGapException gap = GeneratorGapException.unsupportedAction(action.action(), action.folioRecord());
      GenerationOutcome.GeneratorGap outcome = new GenerationOutcome.GeneratorGap(
        pathIndex, path.path().getPathId(), gap.reason().name(), gap.getMessage());
      outcomes.add(new PathOutcome(
        pathIndex,
        path.path().getPathId(),
        path.reactTo().name(),
        path.matchProfileId(),
        List.of(),
        List.of(),
        outcome));
    }
    return outcomes;
  }

  @FunctionalInterface
  public interface ReferenceDataProvider {
    MinimalMarcRecordBuilder.ReferenceDataContext get();
  }
}
