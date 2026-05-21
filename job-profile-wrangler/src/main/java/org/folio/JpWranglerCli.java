package org.folio;

import java.io.Console;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
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
import org.folio.exports.GenerationReportWriter;
import org.folio.exports.GenerationOutcome;
import org.folio.exports.CategorizedPath;
import org.folio.exports.CategorizedPaths;
import org.folio.exports.EnrichmentDetector;
import org.folio.exports.GeneratorGapException;
import org.folio.exports.JobProfileAnalyzer;
import org.folio.exports.JobProfilePath;
import org.folio.exports.MatchedPathPair;
import org.folio.exports.MatchCriteria;
import org.folio.exports.MappingRulesAnalysis;
import org.folio.exports.MappingRulesProcessor;
import org.folio.exports.MinimalMarcRecordBuilder;
import org.folio.exports.PathExtractionResult;
import org.folio.exports.PathOutcome;
import org.folio.exports.ReactTo;
import org.folio.exports.StrictRecordWriter;
import org.folio.graph.GraphReader;
import org.folio.graph.GraphWriter;
import org.folio.graph.GraphWriterEnhanced;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.http.ReferenceDataManager;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.ImportReport;
import org.folio.imports.RepoImport;
import org.folio.validation.ProfileShapeValidator;
import org.jgrapht.Graph;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
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
    JpWranglerCli.DeleteCommand.class,
    JpWranglerCli.EnrichCommand.class
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

    @Option(names = {"--okapi-url"}, description = "Value for X-Okapi-Url header when modules need to call back through a gateway")
    String okapiUrl;

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
        return new FolioClient(new OkHttpClient(), () -> HttpUrl.parse(baseUrl).newBuilder(), token, tenant, okapiUrl);
      } else if (tenant != null && username != null && password != null) {
        return new FolioClient(new OkHttpClient(), () -> HttpUrl.parse(baseUrl).newBuilder(), getToken(), tenant, okapiUrl);
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

  @Command(name = "import", description = "Import job profiles from FOLIO to repository", mixinStandardHelpOptions = true,
    exitCodeOnInvalidInput = 1, exitCodeOnExecutionException = 1,
    footer = {
      "Writes import-report-<UTC timestamp>.json with per-profile outcomes:",
      "  added, duplicate, blocked-unsupported, import-error"
    })
  static class ImportCommand extends RepositoryOptions implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Override
    public Integer call() {
      ensureRepositoryExists();

      try {
        FolioClient client = folioOptions.createFolioClient();
        RepoImport importer = new RepoImport(client, repoPath);
        ImportReport report = importer.importProfiles();
        Path reportPath = writeImportReport(report);
        System.out.printf("Added: %d, Duplicate: %d, Blocked: %d, Errors: %d%n",
          report.addedCount(), report.duplicateCount(), report.blockedCount(), report.errorCount());
        System.out.println("Report: " + reportPath);
        return 0;
      } catch (Exception e) {
        LOGGER.error("Import failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    private Path writeImportReport(ImportReport report) throws IOException {
      String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)
        .format(Instant.parse(report.runTimestamp()));
      Path reportPath = Paths.get(repoPath, "import-report-" + timestamp + ".json");
      Constants.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
      return reportPath;
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

  @Command(name = "generate", description = "Generate test MARC records for FOLIO job profile", mixinStandardHelpOptions = true,
    exitCodeOnInvalidInput = 1, exitCodeOnExecutionException = 1,
    footer = {
      "Exit codes:",
      "  0 generated",
      "  1 setup, usage, or unexpected failure",
      "  2 needs-enrichment",
      "  3 blocked-unsupported-workflow",
      "  4 generator-gap",
      "  5 invalid-profile-shape"
    })
  static class GenerateCommand extends RepositoryOptions implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(index = "0", description = "FOLIO job profile UUID to use for test record generation")
    String jobProfileId;

    @Option(names = {"-o", "--output"}, description = "Base output path for MARC records (suffixes will be added)", required = true)
    String outputPath;

    @Option(names = {"-v", "--verbose"}, description = "Show detailed field generation report")
    boolean verbose;

    @Override
    public Integer call() {
      try {
        // Ensure password is available if needed
        folioOptions.ensurePassword();

        // Ensure repository exists
        ensureRepositoryExists();
        if (!isOutputParentWritable()) {
          return 1;
        }

        // Connect to FOLIO to get the job profile snapshot
        String token = folioOptions.getToken();
        if (token == null) {
          LOGGER.error("Authentication failed: could not obtain token");
          return 1;
        }

        FolioClient client = new FolioClient(
          new OkHttpClient(),
          () -> HttpUrl.parse(folioOptions.baseUrl).newBuilder(),
          token,
          folioOptions.tenant,
          folioOptions.okapiUrl
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
     * - {outputPath}-foundation.mrc: Foundation records to seed the database before testing
     * - {outputPath}-import.mrc: Test records for exercising both CREATE and UPDATE paths
     *
     * NOTE: The file names do NOT correspond to action types. Both CREATE and UPDATE
     * path records go to -import.mrc. The -foundation.mrc file contains pre-requisite
     * records that must exist in the database before running the test import.
     */
    private Integer generateMinimalRecords(FolioClient client, JsonNode snapshot) throws IOException {
      LOGGER.info("Generating minimal MARC records from scratch...");
      String runTimestamp = Instant.now().toString();
      GenerationReportWriter reportWriter = new GenerationReportWriter();
      Path outputBase = Paths.get(outputPath);

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

      cleanupOutputFiles();

      if (analysis.getAllMappedInventoryFields().isEmpty()) {
        GeneratorGapException gap = GeneratorGapException.mappingRulesUnavailable("marc-bib");
        GenerationOutcome.GeneratorGap outcome = new GenerationOutcome.GeneratorGap(-1, null,
          gap.reason().name(), gap.getMessage());
        writeReport(reportWriter, outputBase, snapshot, runTimestamp, outcome, List.of(), refData);
        LOGGER.error("Generation outcome: {} - {}", GenerationOutcome.GENERATOR_GAP, gap.getMessage());
        return outcome.exitCode();
      }

      Optional<GenerationOutcome.BlockedUnsupportedWorkflow> blocked =
        ProfileShapeValidator.defaultValidator().validate(snapshot);
      if (blocked.isPresent()) {
        writeReport(reportWriter, outputBase, snapshot, runTimestamp, blocked.get(), List.of(), refData);
        LOGGER.error("Generation outcome: {} - {}", blocked.get().label(), blocked.get().message());
        return blocked.get().exitCode();
      }

      // Extract all paths (CREATE and UPDATE)
      PathExtractionResult pathResult = extractAllPaths(snapshot);

      if (pathResult.createPaths().isEmpty() && pathResult.updatePaths().isEmpty()
        && pathResult.deletePaths().isEmpty()) {
        if (!pathResult.unsupportedActionPaths().isEmpty()) {
          List<PathOutcome> unsupportedOutcomes = unsupportedActionOutcomes(pathResult.unsupportedActionPaths());
          GenerationOutcome.GeneratorGap outcome =
            (GenerationOutcome.GeneratorGap) unsupportedOutcomes.get(0).outcome();
          writeReport(reportWriter, outputBase, snapshot, runTimestamp, outcome, unsupportedOutcomes, refData);
          LOGGER.error("Generation outcome: {} - {}", outcome.label(), outcome.message());
          return outcome.exitCode();
        }
        GenerationOutcome.InvalidProfileShape outcome = new GenerationOutcome.InvalidProfileShape(
          "EMPTY_PATH", "No CREATE, UPDATE, or DELETE action paths found in job profile");
        writeReport(reportWriter, outputBase, snapshot, runTimestamp, outcome, List.of(), refData);
        LOGGER.warn("No CREATE or UPDATE action paths found in job profile");
        LOGGER.error("Generation outcome: {} - EMPTY_PATH", GenerationOutcome.INVALID_PROFILE_SHAPE);
        return outcome.exitCode();
      }

      LOGGER.info("Found {} CREATE path(s), {} UPDATE path(s), and {} DELETE path(s) in job profile",
        pathResult.createPaths().size(), pathResult.updatePaths().size(), pathResult.deletePaths().size());

      // Categorize paths into paired and unpaired
      CategorizedPaths categorized = categorizePaths(pathResult);

      LOGGER.info("Found {} matched path pair(s) (CREATE + UPDATE sharing same match profile)",
        categorized.pairedPaths().size());

      StrictRecordWriter writer = new StrictRecordWriter();
      List<CategorizedPath> allPaths = writer.pathOrder(categorized);
      Map<Integer, GenerationOutcome.NeedsEnrichment> needsEnrichment =
        EnrichmentDetector.detect(allPaths, outputPath);

      StrictRecordWriter.WriteResult result = writer.write(categorized, refData, outputBase);
      List<PathOutcome> pathOutcomes = mergeEnrichmentOutcomes(result.pathOutcomes(), needsEnrichment);
      GenerationOutcome overallOutcome = mergedOverallOutcome(result.overallOutcome(), needsEnrichment);
      writeReport(reportWriter, outputBase, snapshot, runTimestamp, overallOutcome, pathOutcomes, refData);

      if (result.overallOutcome() instanceof GenerationOutcome.GeneratorGap gap) {
        LOGGER.error("Generation outcome: {} - {}", gap.label(), gap.message());
        return gap.exitCode();
      }

      if (!result.foundationRecords().isEmpty()) {
        LOGGER.info("Generated {} foundation record(s) written to {}",
          result.foundationRecords().size(), outputPath + "-foundation.mrc");
      }
      if (!result.importRecords().isEmpty()) {
        LOGGER.info("Generated {} record(s) for import written to {}",
          result.importRecords().size(), outputPath + "-import.mrc");
      }

      if (!needsEnrichment.isEmpty()) {
        LOGGER.warn("Generated pre-enrichment MARC records; {} path(s) require the enrich step before final import.",
          needsEnrichment.size());
        needsEnrichment.values().forEach(outcome -> LOGGER.warn("{}", outcome.hint()));
        return overallOutcome.exitCode();
      }

      return overallOutcome.exitCode();
    }

    private void writeReport(
        GenerationReportWriter reportWriter,
        Path outputBase,
        JsonNode snapshot,
        String runTimestamp,
        GenerationOutcome overallOutcome,
        List<PathOutcome> paths,
        MinimalMarcRecordBuilder.ReferenceDataContext refData) throws IOException {
      reportWriter.write(outputBase, GenerationReport.of(
        snapshotProfileId(snapshot),
        snapshotProfileName(snapshot),
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
          outcome = enrichment;
        }
        merged.add(new PathOutcome(
          pathOutcome.pathIndex(),
          pathOutcome.pathId(),
          pathOutcome.reactTo(),
          pathOutcome.matchProfileId(),
          pathOutcome.destinationFiles(),
          pathOutcome.fieldsWritten(),
          outcome
        ));
      }
      return merged;
    }

    private GenerationOutcome mergedOverallOutcome(
        GenerationOutcome writerOutcome,
        Map<Integer, GenerationOutcome.NeedsEnrichment> needsEnrichment) {
      if (writerOutcome instanceof GenerationOutcome.GeneratorGap || needsEnrichment.isEmpty()) {
        return writerOutcome;
      }
      return needsEnrichment.entrySet().stream()
        .min(Map.Entry.comparingByKey())
        .<GenerationOutcome>map(Map.Entry::getValue)
        .orElse(writerOutcome);
    }

    private String snapshotProfileId(JsonNode snapshot) {
      String contentId = snapshot.path("content").path("id").asText(null);
      if (contentId != null && !contentId.isBlank()) {
        return contentId;
      }
      return snapshot.path("profileId").asText(jobProfileId);
    }

    private String snapshotProfileName(JsonNode snapshot) {
      return snapshot.path("content").path("name").asText(null);
    }

    private boolean isOutputParentWritable() {
      Path outputBase = Paths.get(outputPath).toAbsolutePath();
      Path parent = outputBase.getParent();
      if (parent == null) {
        parent = Paths.get(".").toAbsolutePath();
      }
      if (!Files.exists(parent)) {
        LOGGER.error("Output parent directory does not exist: {}", parent);
        return false;
      }
      if (!Files.isDirectory(parent) || !Files.isWritable(parent)) {
        LOGGER.error("Output parent directory is not writable: {}", parent);
        return false;
      }
      return true;
    }

    private void cleanupOutputFiles() throws IOException {
      Files.deleteIfExists(Paths.get(outputPath + "-foundation.mrc"));
      Files.deleteIfExists(Paths.get(outputPath + "-import.mrc"));
      Files.deleteIfExists(Paths.get(outputPath + "-report.json"));
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

      return new CategorizedPaths(pairs, unpairedCreate, unpairedUpdate, pathResult.deletePaths());
    }


    /**
     * Extracts all paths (CREATE and UPDATE) from the job profile snapshot.
     * Tracks the reactTo field to determine if paths are triggered by MATCH or NON_MATCH.
     */
    private PathExtractionResult extractAllPaths(JsonNode snapshot) {
      List<CategorizedPath> createPaths = new ArrayList<>();
      List<CategorizedPath> updatePaths = new ArrayList<>();
      List<CategorizedPath> deletePaths = new ArrayList<>();
      List<CategorizedPath> unsupportedActionPaths = new ArrayList<>();

      extractPathsWithOutcome(snapshot, new ArrayList<>(), ReactTo.NONE, null, MatchCriteria.empty(),
        createPaths, updatePaths, deletePaths, unsupportedActionPaths);

      return new PathExtractionResult(createPaths, updatePaths, deletePaths, unsupportedActionPaths);
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
        MatchCriteria currentMatchCriteria,
        List<CategorizedPath> createPaths,
        List<CategorizedPath> updatePaths,
        List<CategorizedPath> deletePaths,
        List<CategorizedPath> unsupportedActionPaths) {

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

        // Track match profile ID and extract match criteria
        if ("MATCH_PROFILE".equals(contentType)) {
          currentMatchProfileId = content.path("id").asText();
          currentMatchCriteria = extractMatchCriteria(content);
          if (verbose && !currentMatchCriteria.isEmpty()) {
            LOGGER.info("Extracted match criteria from match profile {}: {} MARC field(s), {} non-MARC match(es)",
              currentMatchProfileId,
              currentMatchCriteria.matchFields().size(),
              currentMatchCriteria.nonMarcMatches().size());
          }
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
            currentMatchCriteria, createPaths, updatePaths, deletePaths, unsupportedActionPaths);
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
          CategorizedPath categorizedPath = new CategorizedPath(path, currentReactTo, currentMatchProfileId, currentMatchCriteria);

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
          } else if ("DELETE".equals(action.action()) && "MARC_AUTHORITY".equals(action.folioRecord())) {
            deletePaths.add(categorizedPath);
            if (verbose) {
              LOGGER.info("Found DELETE MARC_AUTHORITY path (reactTo: {}): {}", currentReactTo, path.getPathId());
            }
          } else {
            unsupportedActionPaths.add(categorizedPath);
            if (verbose) {
              LOGGER.info("Found unsupported action path ({} {}, reactTo: {}): {}",
                action.action(), action.folioRecord(), currentReactTo, path.getPathId());
            }
          }
        }
      }
    }

    private List<PathOutcome> unsupportedActionOutcomes(List<CategorizedPath> unsupportedPaths) {
      List<PathOutcome> outcomes = new ArrayList<>();
      for (int pathIndex = 0; pathIndex < unsupportedPaths.size(); pathIndex++) {
        CategorizedPath path = unsupportedPaths.get(pathIndex);
        ActionProfileNode action = lastAction(path.path()).orElseThrow();
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

    private Optional<ActionProfileNode> lastAction(JobProfilePath path) {
      return path.getProfiles().stream()
        .filter(ActionProfileNode.class::isInstance)
        .map(ActionProfileNode.class::cast)
        .reduce((first, second) -> second);
    }

    /**
     * Extracts match criteria from a match profile's content node.
     * Parses matchDetails to build MARC field specifications for matching.
     *
     * @param matchProfileContent the content node of a MATCH_PROFILE
     * @return MatchCriteria with extracted field specifications
     */
    private MatchCriteria extractMatchCriteria(JsonNode matchProfileContent) {
      String matchProfileId = matchProfileContent.path("id").asText();
      List<MatchCriteria.MatchFieldSpec> matchFields = new ArrayList<>();
      List<MatchCriteria.NonMarcMatchSpec> nonMarcMatches = new ArrayList<>();

      JsonNode matchDetails = matchProfileContent.path("matchDetails");
      if (!matchDetails.isArray()) {
        return MatchCriteria.empty();
      }

      for (JsonNode matchDetail : matchDetails) {
        // Parse incoming match expression (the MARC field in the incoming record)
        JsonNode incomingExpr = matchDetail.path("incomingMatchExpression");
        JsonNode existingExpr = matchDetail.path("existingMatchExpression");

        String incomingDataType = incomingExpr.path("dataValueType").asText();
        String existingDataType = existingExpr.path("dataValueType").asText();

        // Check if incoming expression targets a MARC field
        if ("VALUE_FROM_RECORD".equals(incomingDataType)) {
          JsonNode fields = incomingExpr.path("fields");
          if (fields.isArray() && !fields.isEmpty()) {
            MatchCriteria.MatchFieldSpec spec = parseFieldsToMatchSpec(fields, null);
            if (spec != null) {
              matchFields.add(spec);
              if (verbose) {
                LOGGER.info("  Extracted MARC match field: {} {} {} subfield {}",
                  spec.fieldTag(), spec.indicator1(), spec.indicator2(), spec.subfieldCode());
              }
            }
          }
        } else if ("STATIC_VALUE".equals(incomingDataType)) {
          // Static value match - extract the static value and any field spec
          String staticValue = incomingExpr.path("staticValueDetails").path("text").asText(null);
          JsonNode fields = incomingExpr.path("fields");
          if (fields.isArray() && !fields.isEmpty()) {
            MatchCriteria.MatchFieldSpec spec = parseFieldsToMatchSpec(fields, staticValue);
            if (spec != null) {
              matchFields.add(spec);
            }
          }
        }

        // Check if existing expression targets a non-MARC field (instance.*, holdings.*)
        if ("VALUE_FROM_RECORD".equals(existingDataType)) {
          JsonNode existingFields = existingExpr.path("fields");
          if (existingFields.isArray() && !existingFields.isEmpty()) {
            String existingField = extractExistingFieldPath(existingFields);
            if (existingField != null && !existingField.startsWith("marc") && !looksLikeMarcField(existingField)) {
              // This is a non-MARC match (e.g., instance.hrid, instance.id)
              // Extract the target MARC field from incoming expression for enrichment
              JsonNode incomingFields = incomingExpr.path("fields");
              if (incomingFields.isArray() && !incomingFields.isEmpty()) {
                MatchCriteria.MatchFieldSpec incomingSpec = parseFieldsToMatchSpec(incomingFields, null);
                if (incomingSpec != null) {
                  nonMarcMatches.add(new MatchCriteria.NonMarcMatchSpec(
                    existingField,
                    incomingSpec.fieldTag(),
                    incomingSpec.subfieldCode(),
                    incomingSpec.indicator1(),
                    incomingSpec.indicator2()
                  ));
                  if (verbose) {
                    LOGGER.info("  Extracted non-MARC match: {} -> MARC {} subfield {}",
                      existingField, incomingSpec.fieldTag(), incomingSpec.subfieldCode());
                  }
                }
              }
            }
          }
        }
      }

      return new MatchCriteria(matchProfileId, matchFields, nonMarcMatches);
    }

    private boolean looksLikeMarcField(String field) {
      return field != null && field.matches("\\d{3}([$.].*)?");
    }

    /**
     * Parses the fields array from a match expression to create a MatchFieldSpec.
     *
     * @param fields the fields array from the match expression
     * @param staticValue optional static value for STATIC_VALUE type matches
     * @return MatchFieldSpec or null if parsing fails
     */
    private MatchCriteria.MatchFieldSpec parseFieldsToMatchSpec(JsonNode fields, String staticValue) {
      String fieldTag = null;
      String indicator1 = null;
      String indicator2 = null;
      String subfieldCode = null;

      for (JsonNode field : fields) {
        String label = field.path("label").asText("");
        String value = field.path("value").asText("");

        switch (label) {
          case "field" -> fieldTag = value;
          case "indicator1" -> indicator1 = value;
          case "indicator2" -> indicator2 = value;
          case "recordSubfield" -> subfieldCode = value;
        }
      }

      if (fieldTag == null || fieldTag.isBlank()) {
        return null;
      }

      return new MatchCriteria.MatchFieldSpec(fieldTag, indicator1, indicator2, subfieldCode, staticValue);
    }

    /**
     * Extracts the field path from an existing match expression's fields array.
     * Concatenates label values to form paths like "instance.hrid" or "instance.id".
     *
     * @param fields the fields array from the existing match expression
     * @return the field path or null if not determinable
     */
    private String extractExistingFieldPath(JsonNode fields) {
      StringBuilder path = new StringBuilder();
      for (JsonNode field : fields) {
        String value = field.path("value").asText("");
        if (!value.isEmpty()) {
          if (path.length() > 0) {
            path.append(".");
          }
          path.append(value);
        }
      }
      return path.length() > 0 ? path.toString() : null;
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
     * @return a ReferenceDataContext populated with available UUIDs; fields may be null when unavailable
     */
    private MinimalMarcRecordBuilder.ReferenceDataContext fetchReferenceDataContext(FolioClient client) {
      try {
        ReferenceDataManager refDataManager = new ReferenceDataManager(
          () -> okhttp3.HttpUrl.parse(folioOptions.baseUrl).newBuilder(),
          folioOptions.getToken(),
          folioOptions.tenant,
          folioOptions.okapiUrl
        );

        Optional<String> locationId = refDataManager.getRandomValidId("locations");
        Optional<String> materialTypeId = refDataManager.getRandomValidId("material-types");
        Optional<String> loanTypeId = refDataManager.getRandomValidId("loan-types");

        if (locationId.isEmpty()) {
          LOGGER.warn("No locations found in tenant. Holdings/Item paths will be classified as generator gaps.");
        }
        if (materialTypeId.isEmpty()) {
          LOGGER.warn("No material types found in tenant. Item paths will be classified as generator gaps.");
        }
        if (loanTypeId.isEmpty()) {
          LOGGER.warn("No loan types found in tenant. Item paths will be classified as generator gaps.");
        }

        return new MinimalMarcRecordBuilder.ReferenceDataContext(
          locationId.orElse(null),
          materialTypeId.orElse(null),
          loanTypeId.orElse(null)
        );
      } catch (Exception e) {
        LOGGER.warn("Failed to fetch reference data: {}. Holdings/Item paths will be classified as generator gaps.",
          e.getMessage());
        return new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null);
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

  @Command(name = "enrich", description = "Enrich update MARC file with instance identifiers from FOLIO", mixinStandardHelpOptions = true)
  static class EnrichCommand implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(index = "0", description = "Path to the -import.mrc file to enrich")
    String importFilePath;

    @Option(names = {"-o", "--output"}, description = "Output file path (default: input file with -ready suffix)")
    String outputPath;

    @Option(names = {"--match-field"}, description = "MARC field for looking up instances (default: 001 for HRID lookup)")
    String matchField = "001";

    @Option(names = {"--enrich-field"}, description = "MARC field to add with instance identifier (e.g., 999ff$i)", required = true)
    String enrichField;

    @Option(names = {"--enrich-type"}, description = "Type of value to enrich with: INSTANCE_ID, INSTANCE_HRID, or SOURCE_RECORD_ID")
    EnrichType enrichType = EnrichType.INSTANCE_ID;

    @Option(names = {"--record-type"}, description = "Source record type for SOURCE_RECORD_ID enrichment")
    String recordType = "MARC_BIBLIOGRAPHIC";

    @Option(names = {"--skip-missing"}, description = "Skip records where instance is not found (default: fail)")
    boolean skipMissing = false;

    enum EnrichType { INSTANCE_ID, INSTANCE_HRID, SOURCE_RECORD_ID }

    private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

    @Override
    public Integer call() {
      try {
        // Ensure password is available if needed
        folioOptions.ensurePassword();

        // Validate input file exists
        Path inputPath = Paths.get(importFilePath);
        if (!Files.exists(inputPath)) {
          LOGGER.error("Input file does not exist: {}", importFilePath);
          return 1;
        }

        // Determine output path
        if (outputPath == null) {
          String inputName = inputPath.getFileName().toString();
          String baseName = inputName.replaceFirst("\\.mrc$", "");
          outputPath = inputPath.getParent() != null
            ? inputPath.getParent().resolve(baseName + "-ready.mrc").toString()
            : baseName + "-ready.mrc";
        }

        // Parse the enrich field specification
        EnrichFieldSpec enrichSpec = parseEnrichField(enrichField);
        if (enrichSpec == null) {
          LOGGER.error("Invalid enrich field format: {}. Expected format like '999ff$i' or '035$a'", enrichField);
          return 1;
        }

        // Connect to FOLIO
        FolioClient client = folioOptions.createFolioClient();

        // Process MARC records
        List<Record> enrichedRecords = new ArrayList<>();
        int totalRecords = 0;
        int enrichedCount = 0;
        int skippedCount = 0;

        try (FileInputStream fis = new FileInputStream(importFilePath)) {
          MarcReader reader = new MarcStreamReader(fis);

          while (reader.hasNext()) {
            Record record = reader.next();
            totalRecords++;

            // Extract the lookup value from the record
            String lookupValue = extractLookupValue(record, matchField);
            if (lookupValue == null || lookupValue.isBlank()) {
              LOGGER.warn("Record {} has no {} field, skipping", totalRecords, matchField);
              if (skipMissing) {
                enrichedRecords.add(record);
                skippedCount++;
                continue;
              } else {
                LOGGER.error("Cannot enrich record without lookup field. Use --skip-missing to continue.");
                return 1;
              }
            }

            Optional<JsonNode> recordOpt = lookupRecord(client, lookupValue, matchField, enrichType);

            if (recordOpt.isEmpty()) {
              LOGGER.warn("Lookup target not found for {} = '{}'", matchField, lookupValue);
              if (skipMissing) {
                enrichedRecords.add(record);
                skippedCount++;
                continue;
              } else {
                LOGGER.error("Lookup target not found. Use --skip-missing to continue without this record.");
                return 1;
              }
            }

            JsonNode lookupRecord = recordOpt.get();

            // Extract the enrichment value
            String enrichValue = extractEnrichValue(lookupRecord, enrichType);
            if (enrichValue == null) {
              LOGGER.warn("Could not extract {} from lookup target", enrichType);
              if (skipMissing) {
                enrichedRecords.add(record);
                skippedCount++;
                continue;
              } else {
                return 1;
              }
            }

            // Enrich the record
            Record enrichedRecord = enrichRecord(record, enrichValue, enrichSpec);
            enrichedRecords.add(enrichedRecord);
            enrichedCount++;

            LOGGER.info("Enriched record {} ({} = '{}'): added {}${} = '{}'",
              totalRecords, matchField, lookupValue, enrichSpec.fieldTag, enrichSpec.subfieldCode, enrichValue);
          }
        }

        // Write enriched records to output file
        try (FileOutputStream fos = new FileOutputStream(outputPath)) {
          MarcStreamWriter writer = new MarcStreamWriter(fos, "UTF-8");
          try {
            for (Record record : enrichedRecords) {
              writer.write(record);
            }
          } finally {
            writer.close();
          }
        }

        LOGGER.info("Enrichment complete:");
        LOGGER.info("  Total records: {}", totalRecords);
        LOGGER.info("  Enriched: {}", enrichedCount);
        LOGGER.info("  Skipped: {}", skippedCount);
        LOGGER.info("  Output written to: {}", outputPath);

        return 0;

      } catch (Exception e) {
        LOGGER.error("Enrichment failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    /**
     * Parses an enrich field specification like "999ff$i" or "035$a".
     * Format: FIELD[IND1][IND2]$SUBFIELD
     */
    private EnrichFieldSpec parseEnrichField(String fieldSpec) {
      if (fieldSpec == null || fieldSpec.isBlank()) {
        return null;
      }

      // Split on $ to separate field/indicators from subfield
      int dollarPos = fieldSpec.indexOf('$');
      if (dollarPos == -1 || dollarPos == fieldSpec.length() - 1) {
        return null;
      }

      String fieldPart = fieldSpec.substring(0, dollarPos);
      char subfieldCode = fieldSpec.charAt(dollarPos + 1);

      // Parse field tag and indicators
      if (fieldPart.length() < 3) {
        return null;
      }

      String fieldTag = fieldPart.substring(0, 3);
      char indicator1 = ' ';
      char indicator2 = ' ';

      if (fieldPart.length() >= 4) {
        indicator1 = fieldPart.charAt(3);
      }
      if (fieldPart.length() >= 5) {
        indicator2 = fieldPart.charAt(4);
      }

      return new EnrichFieldSpec(fieldTag, indicator1, indicator2, subfieldCode);
    }

    /**
     * Extracts a lookup value from a MARC record.
     */
    private String extractLookupValue(Record record, String fieldTag) {
      if ("001".equals(fieldTag)) {
        return record.getControlNumber();
      }

      // Get the field and check its type to avoid ClassCastException
      var field = record.getVariableField(fieldTag);
      if (field == null) {
        return null;
      }

      // For control fields (001-009)
      if (field instanceof ControlField cf) {
        return cf.getData();
      }

      // For data fields, extract first subfield 'a' by default
      if (field instanceof DataField df && df.getSubfield('a') != null) {
        return df.getSubfield('a').getData();
      }

      return null;
    }

    /**
     * Looks up an instance in FOLIO based on the match field.
     */
    private Optional<JsonNode> lookupRecord(FolioClient client, String value, String matchField, EnrichType enrichType) {
      if (enrichType == EnrichType.SOURCE_RECORD_ID) {
        if (!"001".equals(matchField)) {
          LOGGER.warn("SOURCE_RECORD_ID enrichment currently looks up source records by 001; requested {}", matchField);
        }
        return client.findSourceRecordByMarcControlNumber(recordType, value);
      }
      if ("001".equals(matchField)) {
        // 001 typically maps to HRID
        return client.findInstanceByHrid(value);
      } else {
        // Other fields use identifier lookup
        return client.findInstanceByIdentifier(value, null);
      }
    }

    /**
     * Extracts the enrichment value from an instance.
     */
    private String extractEnrichValue(JsonNode instance, EnrichType type) {
      return switch (type) {
        case INSTANCE_ID -> instance.path("id").asText(null);
        case INSTANCE_HRID -> instance.path("hrid").asText(null);
        case SOURCE_RECORD_ID -> instance.path("recordId").asText(instance.path("id").asText(null));
      };
    }

    /**
     * Enriches a MARC record by adding a field with the specified value.
     */
    private Record enrichRecord(Record original, String value, EnrichFieldSpec spec) {
      // Create a new data field with the enrichment value
      DataField dataField = MARC_FACTORY.newDataField(spec.fieldTag, spec.indicator1, spec.indicator2);
      dataField.addSubfield(MARC_FACTORY.newSubfield(spec.subfieldCode, value));

      // Add to the record (creates a copy if record is immutable)
      original.addVariableField(dataField);

      return original;
    }

    /**
     * Specification for an enrichment MARC field.
     */
    private record EnrichFieldSpec(
      String fieldTag,
      char indicator1,
      char indicator2,
      char subfieldCode
    ) {}
  }
}
