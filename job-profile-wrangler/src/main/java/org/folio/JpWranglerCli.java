package org.folio;

import java.io.Console;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exports.GenerationReport;
import org.folio.exports.JobProfileAnalyzer;
import org.folio.exports.JobProfilePath;
import org.folio.exports.MappingRulesAnalysis;
import org.folio.exports.MappingRulesProcessor;
import org.folio.exports.MinimalMarcRecordBuilder;
import org.folio.graph.GraphReader;
import org.folio.graph.GraphWriter;
import org.folio.graph.GraphWriterEnhanced;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.http.ReferenceDataManager;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.Record;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
  name = "jp-wrangler",
  description = "Job Profile Wrangler CLI - manage FOLIO job profiles",
  version = "1.0.0",
  mixinStandardHelpOptions = true,
  subcommands = {
    JpWranglerCli.ImportCommand.class,
    JpWranglerCli.ExportCommand.class,
    JpWranglerCli.ListCommand.class,
    JpWranglerCli.VisualizeCommand.class,
    JpWranglerCli.GenerateCommand.class,
    JpWranglerCli.DeleteCommand.class
  },
  footer = "Note: The 'visualize' command requires GraphViz to be installed (https://graphviz.org/).")
public class JpWranglerCli implements Callable<Integer> {
  private static final Logger LOGGER = LogManager.getLogger(JpWranglerCli.class);

  public static void main(String[] args) {
    int exitCode = new CommandLine(new JpWranglerCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public Integer call() {
    // Show help by default when no subcommand is provided
    CommandLine.usage(this, System.out);
    return 0;
  }

  // Common options for FOLIO connection
  private static class FolioConnectionOptions {
    @Option(names = {"-u", "--url"}, description = "FOLIO base URL")
    String baseUrl;

    @Option(names = {"-t", "--token"}, description = "FOLIO authentication token")
    String token;

    @Option(names = {"--tenant"}, description = "FOLIO tenant ID")
    String tenant;

    @Option(names = {"--username"}, description = "FOLIO username")
    String username;

    @Option(names = {"--password"}, description = "FOLIO password", interactive = true, arity = "0..1", showDefaultValue = Visibility.NEVER)
    String password;

    void ensurePassword() {
      if (password == null && token == null) {
        Console console = System.console();
        if (console != null) {
          char[] passwordChars = console.readPassword("Password: ");
          if (passwordChars != null) {
            password = new String(passwordChars);
          }
        } else {
          throw new IllegalStateException("No console available to read password");
        }
      }
    }

    /**
     * Get token either directly from options or by authenticating with credentials
     */
    public String getToken() {
      if (token != null) {
        return token;
      } else if (tenant != null && username != null && password != null && baseUrl != null) {
        Optional<String> okapiToken = FolioClient.getOkapiToken(
          new OkHttpClient(),
          HttpUrl.parse(baseUrl).newBuilder(),
          tenant,
          username,
          password
        );
        return okapiToken.orElseThrow(() -> new IllegalStateException("Could not get OKAPI token"));
      }
      return null;
    }

    FolioClient createFolioClient() {
      if (baseUrl == null) {
        throw new IllegalArgumentException("FOLIO base URL is required");
      }

      if (token != null) {
        return new FolioClient(() -> HttpUrl.parse(baseUrl).newBuilder(), token);
      } else if (tenant != null && username != null && password != null) {
        return new FolioClient(() -> HttpUrl.parse(baseUrl).newBuilder(), tenant, username, password);
      } else {
        throw new IllegalArgumentException("Either token or tenant, username, and password must be provided");
      }
    }
  }

  // Helper class for repository operations
  private static class RepositoryOptions {
    @Option(names = {"-r", "--repository"}, description = "Path to job profile repository", defaultValue = "./repository")
    String repoPath;

    void ensureRepositoryExists() {
      Path path = Paths.get(repoPath);
      if (!Files.exists(path)) {
        try {
          Files.createDirectories(path);
          LOGGER.info("Created repository directory: {}", path);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to create repository directory: " + e.getMessage(), e);
        }
      }
    }

    /**
     * Lists all available job profile IDs in the repository.
     *
     * @return List of available job profile IDs
     * @throws IOException If an error occurs reading the repository
     */
    java.util.List<Integer> listAvailableProfileIds() throws IOException {
      Path path = Paths.get(repoPath);
      if (!Files.exists(path)) {
        return java.util.Collections.emptyList();
      }

      java.util.List<Integer> profileIds = new java.util.ArrayList<>();
      try (java.util.stream.Stream<Path> stream = Files.list(path)) {
        stream.filter(Files::isRegularFile)
          .map(p -> p.getFileName().toString())
          .filter(name -> GraphWriter.DOT_FILE_PATTERN.matcher(name).matches())
          .forEach(name -> {
            java.util.regex.Matcher matcher = GraphWriter.DOT_FILE_PATTERN.matcher(name);
            if (matcher.matches()) {
              try {
                profileIds.add(Integer.parseInt(matcher.group(1)));
              } catch (NumberFormatException e) {
                // Skip files with invalid numbers
              }
            }
          });
      }

      java.util.Collections.sort(profileIds);
      return profileIds;
    }
  }

  @Command(name = "import", description = "Import job profiles from FOLIO to repository", mixinStandardHelpOptions = true)
  static class ImportCommand extends RepositoryOptions implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Override
    public Integer call() {
      ensureRepositoryExists();

      try {
        FolioClient client = folioOptions.createFolioClient();
        RepoImport importer = new RepoImport(client, repoPath);
        importer.run();
        LOGGER.info("Import completed successfully");
        return 0;
      } catch (Exception e) {
        LOGGER.error("Import failed: {}", e.getMessage(), e);
        return 1;
      }
    }
  }

  @Command(name = "export", description = "Export job profiles from repository to FOLIO", mixinStandardHelpOptions = true)
  static class ExportCommand extends RepositoryOptions implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Option(names = {"-i", "--id"}, description = "Repository ID of job profile to export")
    Integer repoId;

    @Option(names = {"--all"}, description = "Export all job profiles")
    boolean exportAll;

    @Override
    public Integer call() {
      if (repoId == null && !exportAll) {
        LOGGER.error("Either --id or --all must be specified");
        return 1;
      }

      try {
        ensureRepositoryExists();
        FolioClient client = folioOptions.createFolioClient();
        ProfileHydration hydration = new ProfileHydration(client);

        if (exportAll) {
          // Export all profiles
          java.util.List<Integer> profileIds = listAvailableProfileIds();
          if (profileIds.isEmpty()) {
            System.err.println("No job profiles found in repository: " + repoPath);
            return 1;
          }

          for (Integer id : profileIds) {
            try {
              Graph<Profile, RegularEdge> graph = GraphReader.read(repoPath, id);
              var result = hydration.hydrate(id, graph);
              if (result.isPresent()) {
                LOGGER.info("Exported job profile {}", id);
              } else {
                LOGGER.error("Failed to export job profile {}", id);
              }
            } catch (Exception e) {
              LOGGER.error("Error exporting job profile {}: {}", id, e.getMessage());
            }
          }
        } else {
          // Export specific profile
          String filename = GraphWriter.genGraphFileName(repoId);
          Path profilePath = Paths.get(repoPath, filename);

          if (!Files.exists(profilePath)) {
            System.err.println("Error: Job profile with ID " + repoId + " does not exist.");

            // List available profiles to help the user
            try {
              java.util.List<Integer> availableIds = listAvailableProfileIds();
              if (availableIds.isEmpty()) {
                System.err.println("No job profiles found in repository. Import profiles first using the 'import' command.");
              } else {
                System.err.println("\nAvailable job profile IDs:");
                for (Integer id : availableIds) {
                  System.err.println("  " + id);
                }
              }
            } catch (Exception e) {
              // Just ignore errors in listing available IDs
            }

            return 1;
          }

          Graph<Profile, RegularEdge> graph = GraphReader.read(repoPath, repoId);
          var result = hydration.hydrate(repoId, graph);
          if (result.isPresent()) {
            LOGGER.info("Exported job profile {}", repoId);
          } else {
            LOGGER.error("Failed to export job profile {}", repoId);
            return 1;
          }
        }

        return 0;
      } catch (Exception e) {
        LOGGER.error("Export failed: {}", e.getMessage(), e);
        return 1;
      }
    }
  }

  @Command(name = "list", description = "List job profiles in repository", mixinStandardHelpOptions = true)
  static class ListCommand extends RepositoryOptions implements Callable<Integer> {
    @Override
    public Integer call() {
      try {
        ensureRepositoryExists();

        java.util.List<Integer> profileIds = listAvailableProfileIds();

        if (profileIds.isEmpty()) {
          System.out.println("No job profiles found in repository: " + repoPath);
        } else {
          System.out.println("Job profiles in repository:");
          for (Integer id : profileIds) {
            System.out.printf("  ID: %d (file: %s)%n", id, GraphWriter.genGraphFileName(id));
          }
        }
        return 0;
      } catch (Exception e) {
        LOGGER.error("List operation failed: {}", e.getMessage(), e);
        return 1;
      }
    }
  }

  @Command(name = "visualize", description = "Visualize job profile as SVG", mixinStandardHelpOptions = true)
  static class VisualizeCommand extends RepositoryOptions implements Callable<Integer> {
    @Parameters(index = "0", description = "Repository ID of job profile to visualize")
    Integer repoId;

    @Option(names = {"-o", "--output"}, description = "Output file path (without .svg extension)")
    String outputPath;

    @Override
    public Integer call() {
      try {
        ensureRepositoryExists();

        // Check if the profile file exists before trying to read it
        String filename = GraphWriter.genGraphFileName(repoId);
        Path profilePath = Paths.get(repoPath, filename);

        if (!Files.exists(profilePath)) {
          System.err.println("Error: Job profile with ID " + repoId + " does not exist.");

          // List available profiles to help the user
          try {
            java.util.List<Integer> availableIds = listAvailableProfileIds();
            if (availableIds.isEmpty()) {
              System.err.println("No job profiles found in repository. Import profiles first using the 'import' command.");
            } else {
              System.err.println("\nAvailable job profile IDs:");
              for (Integer id : availableIds) {
                System.err.println("  " + id);
              }
            }
          } catch (Exception e) {
            // Just ignore errors in listing available IDs
          }

          return 1;
        }

        Graph<Profile, RegularEdge> graph = GraphReader.read(repoPath, repoId);

        if (outputPath == null) {
          outputPath = "jp-" + repoId;
        }

        // Use the enhanced, quiet renderer
        Optional<java.io.File> file = GraphWriterEnhanced.renderGraphQuietly(outputPath, graph);
        if (file.isEmpty()) {
          LOGGER.error("Failed to render or write graph");
          return 1;
        }

        // Provide additional instructions if we got a DOT file instead of SVG
        if (file.get().getName().endsWith(".dot")) {
          System.out.println("\nGraphViz rendering failed. A DOT file was created instead.");
          System.out.println("To render this file manually, install GraphViz and run:");
          System.out.println("  dot -Tsvg " + file.get().getName() + " -o " + outputPath + ".svg");
        }
        return 0;
      } catch (Exception e) {
        LOGGER.error("Visualization failed: {}", e.getMessage(), e);
        return 1;
      }
    }
  }

  @Command(name = "generate", description = "Generate test MARC records for FOLIO job profile", mixinStandardHelpOptions = true)
  static class GenerateCommand extends RepositoryOptions implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(index = "0", description = "FOLIO job profile UUID to use for test record generation")
    String jobProfileId;

    @Option(names = {"-o", "--output"}, description = "Base output path for MARC records (suffixes will be added)", required = true)
    String outputPath;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed field generation report")
    boolean verbose;

    /**
     * Enum to track the reaction type (MATCH vs NON_MATCH) for a path.
     */
    private enum ReactTo {
      MATCH,
      NON_MATCH,
      NONE // For paths without a match profile (direct CREATE)
    }

    /**
     * A path paired with its reaction type (MATCH/NON_MATCH).
     * This avoids map key collisions when paths have identical pathIds.
     */
    private record CategorizedPath(
      JobProfilePath path,
      ReactTo reactTo,
      String matchProfileId  // The match profile this path is under (if any)
    ) {}

    /**
     * Result of path extraction containing both CREATE and UPDATE paths.
     */
    private record PathExtractionResult(
      List<CategorizedPath> createPaths,
      List<CategorizedPath> updatePaths
    ) {}

    /**
     * Pairs a CREATE path with an UPDATE path that share the same match profile.
     * Used to generate records that will trigger both branches of a match.
     */
    private record MatchedPathPair(
      CategorizedPath createPath, // Path triggered on NON_MATCH
      CategorizedPath updatePath, // Path triggered on MATCH
      String matchProfileId       // The shared match profile ID
    ) {}

    /**
     * Result of categorizing paths into paired and unpaired groups.
     * This is an immutable data carrier for the path categorization step.
     */
    private record CategorizedPaths(
      List<MatchedPathPair> pairedPaths,
      List<CategorizedPath> unpairedCreatePaths,
      List<CategorizedPath> unpairedUpdatePaths
    ) {}

    /**
     * Result of record generation containing both file collections.
     * This is an immutable data carrier for the generation step.
     */
    private record GeneratedRecords(
      List<Record> foundationRecords,
      List<Record> updateFileRecords,
      int totalRecordsGenerated
    ) {}

    @Override
    public Integer call() {
      try {
        // Ensure password is available if needed
        folioOptions.ensurePassword();

        // Ensure repository exists
        ensureRepositoryExists();

        // Connect to FOLIO to get the job profile snapshot
        String token = folioOptions.getToken();
        if (token == null) {
          LOGGER.error("Authentication failed: could not obtain token");
          return 1;
        }

        FolioClient client = new FolioClient(
          () -> HttpUrl.parse(folioOptions.baseUrl).newBuilder(),
          token,
          folioOptions.tenant
        );

        // Get the job profile snapshot directly using the provided UUID
        Optional<JsonNode> snapshot = client.getJobProfileSnapshot(jobProfileId);

        if (snapshot.isEmpty()) {
          LOGGER.error("Failed to retrieve job profile snapshot for UUID: {}", jobProfileId);
          return 1;
        }

        LOGGER.info("Successfully retrieved job profile snapshot for UUID: {}", jobProfileId);

        return generateMinimalRecords(client, snapshot.get());

      } catch (Exception e) {
        LOGGER.error("Test data generation failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    /**
     * Generates minimal MARC records from scratch using mapping rules.
     * Creates two files:
     * - {outputPath}-create.mrc: Foundation records to seed the database
     * - {outputPath}-update.mrc: Records for both CREATE paths (new) and UPDATE paths (modify foundation)
     */
    private Integer generateMinimalRecords(FolioClient client, JsonNode snapshot) throws IOException {
      LOGGER.info("Generating minimal MARC records from scratch...");

      // Get mapping rules from tenant
      MappingRulesProcessor rulesProcessor = new MappingRulesProcessor(client);
      MappingRulesAnalysis analysis = rulesProcessor.fetchAndAnalyze("marc-bib");

      if (verbose) {
        LOGGER.info("Mapping analysis: {} mapped fields, {} required fields",
          analysis.getAllMappedInventoryFields().size(),
          analysis.getRequiredInventoryFields().size());
      }

      // Fetch reference data for Holdings and Items
      MinimalMarcRecordBuilder.ReferenceDataContext refData = fetchReferenceDataContext(client);
      if (refData != null && verbose) {
        LOGGER.info("Reference data fetched for Holdings/Items: locationId={}, materialTypeId={}, loanTypeId={}",
          refData.locationId(), refData.materialTypeId(), refData.loanTypeId());
      }

      // Extract all paths (CREATE and UPDATE)
      PathExtractionResult pathResult = extractAllPaths(snapshot);

      if (pathResult.createPaths().isEmpty() && pathResult.updatePaths().isEmpty()) {
        LOGGER.warn("No CREATE or UPDATE action paths found in job profile");
        return 1;
      }

      LOGGER.info("Found {} CREATE path(s) and {} UPDATE path(s) in job profile",
        pathResult.createPaths().size(), pathResult.updatePaths().size());

      // Categorize paths into paired and unpaired
      CategorizedPaths categorized = categorizePaths(pathResult);

      LOGGER.info("Found {} matched path pair(s) (CREATE + UPDATE sharing same match profile)",
        categorized.pairedPaths().size());

      // Generate records
      GenerationReport.Builder reportBuilder = verbose ? GenerationReport.builder() : null;
      GeneratedRecords generated = generateRecordsFromCategorizedPaths(
        categorized, refData, reportBuilder, pathResult.updatePaths().isEmpty());

      // Write records to output files - I/O at the boundary
      writeOutputFiles(generated, pathResult.updatePaths().isEmpty());

      // Print verbose report if requested
      if (verbose && reportBuilder != null) {
        GenerationReport report = reportBuilder
          .outputPath(outputPath + "-create.mrc")
          .build();
        report.print();
      }

      return 0;
    }

    /**
     * Categorizes paths into paired and unpaired groups.
     *
     * @param pathResult the extracted paths
     * @return categorized paths with paired and unpaired groups
     */
    private CategorizedPaths categorizePaths(PathExtractionResult pathResult) {
      List<MatchedPathPair> pairs = pairPaths(pathResult.createPaths(), pathResult.updatePaths());

      Set<CategorizedPath> pairedCreatePaths = new HashSet<>();
      Set<CategorizedPath> pairedUpdatePaths = new HashSet<>();

      for (MatchedPathPair pair : pairs) {
        pairedCreatePaths.add(pair.createPath());
        pairedUpdatePaths.add(pair.updatePath());
      }

      List<CategorizedPath> unpairedCreate = pathResult.createPaths().stream()
        .filter(p -> !pairedCreatePaths.contains(p))
        .toList();

      List<CategorizedPath> unpairedUpdate = pathResult.updatePaths().stream()
        .filter(p -> !pairedUpdatePaths.contains(p))
        .toList();

      return new CategorizedPaths(pairs, unpairedCreate, unpairedUpdate);
    }

    /**
     * Generates MARC records from categorized paths.
     * Note: This method has side effects on reportBuilder (if non-null) for verbose output tracking.
     *
     * @param categorized the categorized paths
     * @param refData reference data context for Holdings/Items
     * @param reportBuilder optional report builder for verbose output (modified via side effects)
     * @param isCreateOnlyProfile whether this is a CREATE-only profile
     * @return generated records for both output files
     */
    private GeneratedRecords generateRecordsFromCategorizedPaths(
        CategorizedPaths categorized,
        MinimalMarcRecordBuilder.ReferenceDataContext refData,
        GenerationReport.Builder reportBuilder,
        boolean isCreateOnlyProfile) {

      List<Record> foundationRecords = new ArrayList<>();
      List<Record> updateFileRecords = new ArrayList<>();
      int recordNumber = 0;

      // Process paired paths
      for (MatchedPathPair pair : categorized.pairedPaths()) {
        recordNumber = generatePairedPathRecords(
          pair, recordNumber, refData, reportBuilder, foundationRecords, updateFileRecords);
      }

      // Process unpaired CREATE paths
      recordNumber = generateUnpairedCreateRecords(
        categorized.unpairedCreatePaths(), recordNumber, refData, reportBuilder,
        foundationRecords, updateFileRecords, isCreateOnlyProfile);

      // Process unpaired UPDATE paths
      recordNumber = generateUnpairedUpdateRecords(
        categorized.unpairedUpdatePaths(), recordNumber, refData, reportBuilder,
        foundationRecords, updateFileRecords);

      return new GeneratedRecords(foundationRecords, updateFileRecords, recordNumber);
    }

    /**
     * Generates records for a paired CREATE/UPDATE path combination.
     *
     * @return the updated record number
     */
    private int generatePairedPathRecords(
        MatchedPathPair pair,
        int recordNumber,
        MinimalMarcRecordBuilder.ReferenceDataContext refData,
        GenerationReport.Builder reportBuilder,
        List<Record> foundationRecords,
        List<Record> updateFileRecords) {

      // Generate foundation record
      if (reportBuilder != null) reportBuilder.startNewRecord();
      recordNumber++;
      MinimalMarcRecordBuilder.BuildResult foundationResult = MinimalMarcRecordBuilder.buildRecordForPath(
        pair.updatePath().path(), recordNumber, reportBuilder, refData);
      Record foundationRecord = foundationResult.record();
      foundationRecords.add(foundationRecord);

      // Generate CREATE path record
      if (reportBuilder != null) reportBuilder.startNewRecord();
      recordNumber++;
      MinimalMarcRecordBuilder.BuildResult createResult = MinimalMarcRecordBuilder.buildRecordForPath(
        pair.createPath().path(), recordNumber, reportBuilder, refData);
      updateFileRecords.add(createResult.record());

      // Generate UPDATE record
      if (reportBuilder != null) reportBuilder.startNewRecord();
      recordNumber++;
      MinimalMarcRecordBuilder.BuildResult updateResult = MinimalMarcRecordBuilder.buildUpdateRecordFromBase(
        foundationRecord, pair.updatePath().path(), recordNumber, reportBuilder, refData);
      updateFileRecords.add(updateResult.record());

      if (verbose) {
        LOGGER.info("Generated records for paired paths:");
        LOGGER.info("  Foundation (001: {}) -> -create.mrc", foundationRecord.getControlNumber());
        LOGGER.info("  CREATE path (001: {}) -> -update.mrc", createResult.record().getControlNumber());
        LOGGER.info("  UPDATE path (001: {}) -> -update.mrc (matches foundation)", updateResult.record().getControlNumber());
      }

      return recordNumber;
    }

    /**
     * Generates records for unpaired CREATE paths.
     *
     * @return the updated record number
     */
    private int generateUnpairedCreateRecords(
        List<CategorizedPath> unpairedCreatePaths,
        int recordNumber,
        MinimalMarcRecordBuilder.ReferenceDataContext refData,
        GenerationReport.Builder reportBuilder,
        List<Record> foundationRecords,
        List<Record> updateFileRecords,
        boolean isCreateOnlyProfile) {

      if (unpairedCreatePaths.isEmpty()) {
        return recordNumber;
      }

      if (isCreateOnlyProfile) {
        // Consolidate sibling paths into single records
        Map<String, List<CategorizedPath>> pathsByParent = groupPathsByParentProfile(unpairedCreatePaths);

        for (Map.Entry<String, List<CategorizedPath>> entry : pathsByParent.entrySet()) {
          List<CategorizedPath> siblingPaths = entry.getValue();

          if (reportBuilder != null) reportBuilder.startNewRecord();
          recordNumber++;

          JobProfilePath consolidatedPath = consolidateCreatePaths(siblingPaths);
          MinimalMarcRecordBuilder.BuildResult result = MinimalMarcRecordBuilder.buildRecordForPath(
            consolidatedPath, recordNumber, reportBuilder, refData);
          foundationRecords.add(result.record());

          if (verbose) {
            LOGGER.info("Generated consolidated record for {} sibling CREATE paths -> -create.mrc",
              siblingPaths.size());
            for (CategorizedPath path : siblingPaths) {
              LOGGER.info("  - {}", path.path().getPathId());
            }
          }
        }
      } else {
        // Process individually for mixed profiles
        for (CategorizedPath createCatPath : unpairedCreatePaths) {
          if (reportBuilder != null) reportBuilder.startNewRecord();
          recordNumber++;
          MinimalMarcRecordBuilder.BuildResult result = MinimalMarcRecordBuilder.buildRecordForPath(
            createCatPath.path(), recordNumber, reportBuilder, refData);
          updateFileRecords.add(result.record());

          if (verbose) {
            LOGGER.info("Generated record for unpaired CREATE path (reactTo: {}) -> -update.mrc: {}",
              createCatPath.reactTo(), createCatPath.path().getPathId());
          }
        }
      }

      return recordNumber;
    }

    /**
     * Generates records for unpaired UPDATE paths.
     *
     * @return the updated record number
     */
    private int generateUnpairedUpdateRecords(
        List<CategorizedPath> unpairedUpdatePaths,
        int recordNumber,
        MinimalMarcRecordBuilder.ReferenceDataContext refData,
        GenerationReport.Builder reportBuilder,
        List<Record> foundationRecords,
        List<Record> updateFileRecords) {

      for (CategorizedPath updateCatPath : unpairedUpdatePaths) {
        // Generate foundation record
        if (reportBuilder != null) reportBuilder.startNewRecord();
        recordNumber++;
        MinimalMarcRecordBuilder.BuildResult foundationResult = MinimalMarcRecordBuilder.buildRecordForPath(
          updateCatPath.path(), recordNumber, reportBuilder, refData);
        Record foundationRecord = foundationResult.record();
        foundationRecords.add(foundationRecord);

        // Generate update record
        if (reportBuilder != null) reportBuilder.startNewRecord();
        recordNumber++;
        MinimalMarcRecordBuilder.BuildResult updateResult = MinimalMarcRecordBuilder.buildUpdateRecordFromBase(
          foundationRecord, updateCatPath.path(), recordNumber, reportBuilder, refData);
        updateFileRecords.add(updateResult.record());

        if (verbose) {
          LOGGER.info("Generated records for unpaired UPDATE path: {}", updateCatPath.path().getPathId());
        }
      }

      return recordNumber;
    }

    /**
     * Writes the generated records to output files.
     * This is the I/O boundary - all pure transformations happen before this.
     */
    private void writeOutputFiles(GeneratedRecords generated, boolean isCreateOnlyProfile) throws IOException {
      String createFilePath = outputPath + "-create.mrc";
      String updateFilePath = outputPath + "-update.mrc";

      // Write foundation/CREATE records to -create.mrc
      if (!generated.foundationRecords().isEmpty()) {
        try (FileOutputStream fos = new FileOutputStream(createFilePath)) {
          MarcStreamWriter writer = new MarcStreamWriter(fos, "UTF-8");
          for (Record record : generated.foundationRecords()) {
            writer.write(record);
          }
          writer.close();
        }
        LOGGER.info("Generated {} record(s) written to {}", generated.foundationRecords().size(), createFilePath);
      }

      // Write update file records to -update.mrc (only if there are records)
      if (!generated.updateFileRecords().isEmpty()) {
        try (FileOutputStream fos = new FileOutputStream(updateFilePath)) {
          MarcStreamWriter writer = new MarcStreamWriter(fos, "UTF-8");
          for (Record record : generated.updateFileRecords()) {
            writer.write(record);
          }
          writer.close();
        }
        LOGGER.info("Generated {} record(s) for import written to {}", generated.updateFileRecords().size(), updateFilePath);
      }

      // Log summary
      LOGGER.info("Generation complete:");
      if (isCreateOnlyProfile) {
        LOGGER.info("  {} - {} record(s) for CREATE paths", createFilePath, generated.foundationRecords().size());
        LOGGER.info("Workflow: Import {} to create new records", createFilePath);
      } else {
        LOGGER.info("  {} - {} foundation record(s) to seed database", createFilePath, generated.foundationRecords().size());
        LOGGER.info("  {} - {} record(s) to trigger both CREATE and UPDATE paths", updateFilePath, generated.updateFileRecords().size());
        LOGGER.info("Workflow: Import {} first, then import {}", createFilePath, updateFilePath);
      }
    }

    /**
     * Extracts all paths (CREATE and UPDATE) from the job profile snapshot.
     * Tracks the reactTo field to determine if paths are triggered by MATCH or NON_MATCH.
     */
    private PathExtractionResult extractAllPaths(JsonNode snapshot) {
      List<CategorizedPath> createPaths = new ArrayList<>();
      List<CategorizedPath> updatePaths = new ArrayList<>();

      extractPathsWithOutcome(snapshot, new ArrayList<>(), ReactTo.NONE, null,
        createPaths, updatePaths);

      return new PathExtractionResult(createPaths, updatePaths);
    }

    /**
     * Recursively extracts paths from the snapshot, tracking reactTo (MATCH/NON_MATCH) for categorization.
     * UPDATE actions are always under MATCH edges, CREATE actions can be under NON_MATCH or direct.
     */
    private void extractPathsWithOutcome(
        JsonNode node,
        List<Profile> currentPath,
        ReactTo currentReactTo,
        String currentMatchProfileId,
        List<CategorizedPath> createPaths,
        List<CategorizedPath> updatePaths) {

      String contentType = node.path("contentType").asText();
      JsonNode content = node.path("content");

      if (verbose) {
        LOGGER.info("Processing node - contentType: '{}', hasContent: {}, fields: {}",
          contentType, !content.isMissingNode(), node.fieldNames().hasNext() ? iteratorToString(node.fieldNames()) : "none");
      }

      // Create appropriate profile node based on content type
      Profile profile = createProfileFromNode(contentType, content);
      if (profile != null) {
        currentPath.add(profile);
        if (verbose) {
          LOGGER.info("Traversing profile: {} (type: {})", profile.getName(), profile.getClass().getSimpleName());
        }

        // Track match profile ID for pairing
        if ("MATCH_PROFILE".equals(contentType)) {
          currentMatchProfileId = content.path("id").asText();
        }
      }

      // Check children
      JsonNode children = node.path("childSnapshotWrappers");
      if (children.isArray() && !children.isEmpty()) {
        if (verbose) {
          LOGGER.info("Found {} children", children.size());
        }
        for (JsonNode child : children) {
          // Check reactTo field on child to determine branch type
          String reactToStr = child.path("reactTo").asText("");
          ReactTo childReactTo = switch (reactToStr) {
            case "MATCH" -> ReactTo.MATCH;
            case "NON_MATCH" -> ReactTo.NON_MATCH;
            default -> currentReactTo; // Inherit from parent if not specified
          };

          if (verbose && !reactToStr.isEmpty()) {
            LOGGER.info("Child has reactTo: {}", reactToStr);
          }

          extractPathsWithOutcome(child, new ArrayList<>(currentPath), childReactTo, currentMatchProfileId,
            createPaths, updatePaths);
        }
      } else {
        // Leaf node - categorize by action type
        Optional<ActionProfileNode> actionOpt = currentPath.stream()
          .filter(p -> p instanceof ActionProfileNode)
          .map(p -> (ActionProfileNode) p)
          .reduce((first, second) -> second); // Get last action profile

        if (actionOpt.isPresent() && !currentPath.isEmpty()) {
          ActionProfileNode action = actionOpt.get();
          JobProfilePath path = new JobProfilePath(new ArrayList<>(currentPath));
          CategorizedPath categorizedPath = new CategorizedPath(path, currentReactTo, currentMatchProfileId);

          if ("CREATE".equals(action.action())) {
            createPaths.add(categorizedPath);
            if (verbose) {
              LOGGER.info("Found CREATE path (reactTo: {}): {}", currentReactTo, path.getPathId());
            }
          } else if ("UPDATE".equals(action.action())) {
            updatePaths.add(categorizedPath);
            if (verbose) {
              LOGGER.info("Found UPDATE path (reactTo: {}): {}", currentReactTo, path.getPathId());
            }
          }
        }
      }
    }

    /**
     * Pairs CREATE and UPDATE paths that share the same match profile.
     * Returns pairs where:
     * - createPath is triggered on NON_MATCH
     * - updatePath is triggered on MATCH
     */
    private List<MatchedPathPair> pairPaths(
        List<CategorizedPath> createPaths,
        List<CategorizedPath> updatePaths) {

      List<MatchedPathPair> pairs = new ArrayList<>();

      // Find CREATE paths that are under NON_MATCH (paired with UPDATE paths under MATCH)
      for (CategorizedPath createCatPath : createPaths) {
        // Only pair CREATE paths that are triggered by NON_MATCH
        if (createCatPath.reactTo() == ReactTo.NON_MATCH && createCatPath.matchProfileId() != null) {
          String matchProfileId = createCatPath.matchProfileId();

          // Find UPDATE path with the same match profile under MATCH
          for (CategorizedPath updateCatPath : updatePaths) {
            if (updateCatPath.reactTo() == ReactTo.MATCH &&
                matchProfileId.equals(updateCatPath.matchProfileId())) {
              pairs.add(new MatchedPathPair(createCatPath, updateCatPath, matchProfileId));
              if (verbose) {
                LOGGER.info("Paired CREATE path {} with UPDATE path {} via match profile {}",
                  createCatPath.path().getPathId(), updateCatPath.path().getPathId(), matchProfileId);
              }
              break; // One pair per CREATE path
            }
          }
        }
      }

      return pairs;
    }

    private String iteratorToString(java.util.Iterator<String> iterator) {
      List<String> list = new ArrayList<>();
      iterator.forEachRemaining(list::add);
      return String.join(", ", list);
    }

    /**
     * Groups CREATE paths by their parent job profile ID.
     * Paths that share the same parent should be consolidated into a single MARC record.
     *
     * @param createPaths the CREATE paths to group
     * @return a map of parent profile ID to list of paths under that parent
     */
    private Map<String, List<CategorizedPath>> groupPathsByParentProfile(List<CategorizedPath> createPaths) {
      Map<String, List<CategorizedPath>> grouped = new LinkedHashMap<>();

      if (createPaths == null) {
        return grouped;
      }

      for (CategorizedPath catPath : createPaths) {
        // Get the job profile (first profile in the path) as the parent
        String parentId = "unknown";
        if (catPath != null && catPath.path() != null && !catPath.path().getProfiles().isEmpty()) {
          Profile firstProfile = catPath.path().getProfiles().get(0);
          Map<String, String> attributes = firstProfile.getAttributes();
          if (attributes != null) {
            parentId = attributes.getOrDefault("id", "unknown");
          }
        }

        grouped.computeIfAbsent(parentId, k -> new ArrayList<>()).add(catPath);
      }

      return grouped;
    }

    /**
     * Consolidates multiple CREATE paths into a single JobProfilePath that includes
     * all the action profiles. This allows generating a single MARC record that
     * contains fields for all record types (Instance, Holdings, Item).
     *
     * @param createPaths the CREATE paths to consolidate
     * @return a consolidated JobProfilePath containing all action profiles, or an empty path if input is null/empty
     */
    private JobProfilePath consolidateCreatePaths(List<CategorizedPath> createPaths) {
      if (createPaths == null || createPaths.isEmpty()) {
        return new JobProfilePath(java.util.Collections.emptyList());
      }

      // Collect all unique profiles from all paths, preserving order
      List<Profile> consolidatedProfiles = new ArrayList<>();
      Set<String> seenProfileIds = new HashSet<>();

      for (CategorizedPath catPath : createPaths) {
        if (catPath == null || catPath.path() == null) {
          continue;
        }
        for (Profile profile : catPath.path().getProfiles()) {
          if (profile == null) {
            continue;
          }
          String profileKey = profile.getClass().getSimpleName() + "-" + profile.getName();
          // For ActionProfileNode, use a more specific key
          if (profile instanceof ActionProfileNode actionProfile) {
            profileKey = "ActionProfile-" + actionProfile.action() + "-" + actionProfile.folioRecord();
          } else if (profile instanceof MappingProfileNode mappingProfile) {
            profileKey = "MappingProfile-" + mappingProfile.existingRecordType();
          }

          if (!seenProfileIds.contains(profileKey)) {
            seenProfileIds.add(profileKey);
            consolidatedProfiles.add(profile);
          }
        }
      }

      return new JobProfilePath(consolidatedProfiles);
    }

    /**
     * Creates a Profile object from a snapshot node.
     */
    private Profile createProfileFromNode(String contentType, JsonNode content) {
      String id = content.path("id").asText();
      String dataType = content.path("dataType").asText();
      int order = content.path("order").asInt(0);

      return switch (contentType) {
        case "JOB_PROFILE" -> new org.folio.graph.nodes.JobProfileNode(id, dataType, order);
        case "ACTION_PROFILE" -> {
          String action = content.path("action").asText();
          String folioRecord = content.path("folioRecord").asText();
          yield new ActionProfileNode(id, action, folioRecord, order);
        }
        case "MATCH_PROFILE" -> {
          String incomingRecordType = content.path("incomingRecordType").asText();
          String existingRecordType = content.path("existingRecordType").asText();
          yield new org.folio.graph.nodes.MatchProfileNode(id, incomingRecordType, existingRecordType, order);
        }
        case "MAPPING_PROFILE" -> {
          String incomingRecordType = content.path("incomingRecordType").asText();
          String existingRecordType = content.path("existingRecordType").asText();
          yield new org.folio.graph.nodes.MappingProfileNode(id, incomingRecordType, existingRecordType, order);
        }
        default -> null;
      };
    }

    /**
     * Fetches reference data from the FOLIO tenant to create a ReferenceDataContext
     * for generating Holdings and Item fields.
     *
     * @param client the FolioClient for API access
     * @return a ReferenceDataContext with valid UUIDs, or null if reference data cannot be fetched
     */
    private MinimalMarcRecordBuilder.ReferenceDataContext fetchReferenceDataContext(FolioClient client) {
      try {
        ReferenceDataManager refDataManager = new ReferenceDataManager(
          () -> okhttp3.HttpUrl.parse(folioOptions.baseUrl).newBuilder(),
          folioOptions.getToken(),
          folioOptions.tenant
        );

        Optional<String> locationId = refDataManager.getRandomValidId("locations");
        Optional<String> materialTypeId = refDataManager.getRandomValidId("material-types");
        Optional<String> loanTypeId = refDataManager.getRandomValidId("loan-types");

        if (locationId.isEmpty()) {
          LOGGER.warn("No locations found in tenant. Holdings/Item fields will not be generated.");
          return null;
        }
        if (materialTypeId.isEmpty()) {
          LOGGER.warn("No material types found in tenant. Item fields will not be generated.");
          return null;
        }
        if (loanTypeId.isEmpty()) {
          LOGGER.warn("No loan types found in tenant. Item fields will not be generated.");
          return null;
        }

        return new MinimalMarcRecordBuilder.ReferenceDataContext(
          locationId.get(),
          materialTypeId.get(),
          loanTypeId.get()
        );
      } catch (Exception e) {
        LOGGER.warn("Failed to fetch reference data: {}. Holdings/Item fields will not be generated.", e.getMessage());
        return null;
      }
    }
  }

  @Command(name = "delete", description = "Delete job profiles and all sub-profiles by name criteria", mixinStandardHelpOptions = true)
  static class DeleteCommand extends FolioConnectionOptions implements Callable<Integer> {
    @Option(names = {"-n", "--name-contains"}, description = "Delete profiles whose name contains this substring", required = true)
    String nameContains;

    @Option(names = {"--confirm"}, description = "Actually delete profiles (without this flag, only shows what would be deleted)")
    boolean confirm;

    @Override
    public Integer call() {
      try {
        ensurePassword();
        FolioClient client = createFolioClient();

        // Fetch all job profiles
        List<JsonNode> allProfiles = client.getJobProfiles().toList();
        LOGGER.info("Fetched {} total job profiles from FOLIO", allProfiles.size());

        // Filter by name containing substring (case-insensitive)
        List<JsonNode> matchingProfiles = allProfiles.stream()
          .filter(profile -> {
            String name = profile.path("name").asText("");
            return name.toLowerCase().contains(nameContains.toLowerCase());
          })
          .toList();

        if (matchingProfiles.isEmpty()) {
          System.out.println("No job profiles found matching: " + nameContains);
          return 0;
        }

        // Collect sub-profile counts for each matching job profile
        System.out.println("Found " + matchingProfiles.size() + " job profile(s) matching '" + nameContains + "':");

        int totalMatchProfiles = 0;
        int totalActionProfiles = 0;
        int totalMappingProfiles = 0;

        // Store profile data for deletion phase
        List<ProfileDeletionData> deletionDataList = new ArrayList<>();

        for (JsonNode profile : matchingProfiles) {
          String id = profile.path("id").asText();
          String name = profile.path("name").asText();

          // Fetch snapshot to get sub-profile counts
          Optional<JsonNode> snapshotOpt = client.getJobProfileSnapshot(id);

          Set<String> matchIds = new HashSet<>();
          Set<String> actionIds = new HashSet<>();
          Set<String> mappingIds = new HashSet<>();

          if (snapshotOpt.isPresent()) {
            collectProfileIds(snapshotOpt.get(), matchIds, actionIds, mappingIds);
          }

          System.out.println("  - " + name + " (ID: " + id + ")");
          System.out.println("    - " + matchIds.size() + " match profile(s)");
          System.out.println("    - " + actionIds.size() + " action profile(s)");
          System.out.println("    - " + mappingIds.size() + " mapping profile(s)");

          totalMatchProfiles += matchIds.size();
          totalActionProfiles += actionIds.size();
          totalMappingProfiles += mappingIds.size();

          deletionDataList.add(new ProfileDeletionData(id, name, matchIds, actionIds, mappingIds));
        }

        int totalSubProfiles = totalMatchProfiles + totalActionProfiles + totalMappingProfiles;

        if (!confirm) {
          System.out.println("\nDry-run mode: No profiles were deleted.");
          System.out.println("Run with --confirm to delete " + matchingProfiles.size() +
            " job profile(s) and " + totalSubProfiles + " sub-profile(s).");
          return 0;
        }

        // Actually delete profiles in reverse order: Mapping → Action → Match → Job Profile
        System.out.println("\nDeleting profiles...");

        DeletionResult totalResult = deletionDataList.stream()
          .map(data -> deleteProfileCascade(client, data))
          .reduce(DeletionResult.empty(), DeletionResult::combine);

        System.out.println("\nDeletion complete:");
        System.out.println("  Job profiles:     " + totalResult.jobSuccess() + " succeeded, " + totalResult.jobFail() + " failed");
        System.out.println("  Match profiles:   " + totalResult.matchSuccess() + " succeeded, " + totalResult.matchFail() + " failed");
        System.out.println("  Action profiles:  " + totalResult.actionSuccess() + " succeeded, " + totalResult.actionFail() + " failed");
        System.out.println("  Mapping profiles: " + totalResult.mappingSuccess() + " succeeded, " + totalResult.mappingFail() + " failed");

        return totalResult.totalFailed() > 0 ? 1 : 0;

      } catch (Exception e) {
        LOGGER.error("Delete operation failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    /**
     * Recursively collects profile IDs from a job profile snapshot.
     */
    private void collectProfileIds(JsonNode node, Set<String> matchIds,
                                   Set<String> actionIds, Set<String> mappingIds) {
      String contentType = node.path("contentType").asText();
      String id = node.path("content").path("id").asText();

      if (!id.isEmpty()) {
        switch (contentType) {
          case "MAPPING_PROFILE" -> mappingIds.add(id);
          case "ACTION_PROFILE" -> actionIds.add(id);
          case "MATCH_PROFILE" -> matchIds.add(id);
          // JOB_PROFILE is handled separately
        }
      }

      // Recurse into children
      JsonNode children = node.path("childSnapshotWrappers");
      if (children.isArray()) {
        for (JsonNode child : children) {
          collectProfileIds(child, matchIds, actionIds, mappingIds);
        }
      }
    }

    /**
     * Holds data needed for cascade deletion of a job profile and its sub-profiles.
     */
    private record ProfileDeletionData(
      String id,
      String name,
      Set<String> matchIds,
      Set<String> actionIds,
      Set<String> mappingIds
    ) {}

    /**
     * Holds the result of deletion operations with success/fail counts for each profile type.
     */
    private record DeletionResult(
      int jobSuccess,
      int jobFail,
      int matchSuccess,
      int matchFail,
      int actionSuccess,
      int actionFail,
      int mappingSuccess,
      int mappingFail
    ) {
      static DeletionResult empty() {
        return new DeletionResult(0, 0, 0, 0, 0, 0, 0, 0);
      }

      static DeletionResult combine(DeletionResult a, DeletionResult b) {
        return new DeletionResult(
          a.jobSuccess + b.jobSuccess,
          a.jobFail + b.jobFail,
          a.matchSuccess + b.matchSuccess,
          a.matchFail + b.matchFail,
          a.actionSuccess + b.actionSuccess,
          a.actionFail + b.actionFail,
          a.mappingSuccess + b.mappingSuccess,
          a.mappingFail + b.mappingFail
        );
      }

      int totalFailed() {
        return jobFail + matchFail + actionFail + mappingFail;
      }
    }

    /**
     * Deletes a job profile and all its sub-profiles, returning the result counts.
     * Deletion order: Job profile first (removes associations), then sub-profiles.
     */
    private DeletionResult deleteProfileCascade(FolioClient client, ProfileDeletionData data) {
      System.out.println("\nDeleting job profile: " + data.name());

      int mappingSuccess = 0, mappingFail = 0;
      int actionSuccess = 0, actionFail = 0;
      int matchSuccess = 0, matchFail = 0;
      int jobSuccess = 0, jobFail = 0;

      // Delete the job profile FIRST to remove associations
      if (client.deleteJobProfile(data.id())) {
        System.out.println("  Deleted: " + data.name());
        jobSuccess++;
      } else {
        System.out.println("  Failed to delete: " + data.name());
        jobFail++;
        // If job profile deletion fails, skip sub-profile deletion
        return new DeletionResult(
          jobSuccess, jobFail, matchSuccess, matchFail,
          actionSuccess, actionFail, mappingSuccess, mappingFail
        );
      }

      // Delete mapping profiles (may fail if shared with other job profiles)
      for (String mappingId : data.mappingIds()) {
        if (client.deleteMappingProfile(mappingId)) {
          mappingSuccess++;
          LOGGER.info("  Deleted mapping profile: {}", mappingId);
        } else {
          mappingFail++;
          LOGGER.warn("  Failed to delete mapping profile: {}", mappingId);
        }
      }

      // Delete action profiles (may fail if shared with other job profiles)
      for (String actionId : data.actionIds()) {
        if (client.deleteActionProfile(actionId)) {
          actionSuccess++;
          LOGGER.info("  Deleted action profile: {}", actionId);
        } else {
          actionFail++;
          LOGGER.warn("  Failed to delete action profile: {}", actionId);
        }
      }

      // Delete match profiles (may fail if shared with other job profiles)
      for (String matchId : data.matchIds()) {
        if (client.deleteMatchProfile(matchId)) {
          matchSuccess++;
          LOGGER.info("  Deleted match profile: {}", matchId);
        } else {
          matchFail++;
          LOGGER.warn("  Failed to delete match profile: {}", matchId);
        }
      }

      return new DeletionResult(
        jobSuccess, jobFail, matchSuccess, matchFail,
        actionSuccess, actionFail, mappingSuccess, mappingFail
      );
    }
  }
}
