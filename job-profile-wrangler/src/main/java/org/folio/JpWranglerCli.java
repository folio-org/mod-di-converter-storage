package org.folio;

import java.io.Console;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
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

      // Build the graph from snapshot and analyze paths
      // For now, we'll generate one record per CREATE action path
      List<JobProfilePath> createPaths = extractCreateOnlyPaths(snapshot);

      if (createPaths.isEmpty()) {
        LOGGER.warn("No CREATE action paths found in job profile");
        return 1;
      }

      LOGGER.info("Found {} CREATE action paths in job profile", createPaths.size());

      // Generate minimal records using stateless builder
      GenerationReport.Builder reportBuilder = verbose ? GenerationReport.builder() : null;
      List<Record> records = new ArrayList<>();

      int recordNumber = 0;
      for (JobProfilePath path : createPaths) {
        recordNumber++;
        MinimalMarcRecordBuilder.BuildResult result = MinimalMarcRecordBuilder.buildRecordForPath(
          path, recordNumber, reportBuilder);
        records.add(result.record());
      }

      // Write to output file
      String createFilePath = outputPath + "-create.mrc";
      try (FileOutputStream fos = new FileOutputStream(createFilePath)) {
        MarcStreamWriter writer = new MarcStreamWriter(fos, "UTF-8");
        for (Record record : records) {
          writer.write(record);
        }
        writer.close();
      }

      LOGGER.info("Generated {} minimal MARC record(s) written to {}", records.size(), createFilePath);

      // Print verbose report if requested
      if (verbose) {
        GenerationReport report = reportBuilder
          .outputPath(createFilePath)
          .build();
        report.print();
      }

      return 0;
    }

    /**
     * Extracts CREATE-only paths from the job profile snapshot.
     * This simplified implementation creates paths for each CREATE action found.
     */
    private List<JobProfilePath> extractCreateOnlyPaths(JsonNode snapshot) {
      List<JobProfilePath> paths = new ArrayList<>();
      extractPathsRecursive(snapshot, new ArrayList<>(), paths);
      return paths;
    }

    /**
     * Recursively extracts paths from the snapshot, creating a path for each leaf.
     */
    private void extractPathsRecursive(JsonNode node, List<Profile> currentPath, List<JobProfilePath> paths) {
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
      }

      // Check children
      JsonNode children = node.path("childSnapshotWrappers");
      if (children.isArray() && children.size() > 0) {
        if (verbose) {
          LOGGER.info("Found {} children", children.size());
        }
        for (JsonNode child : children) {
          extractPathsRecursive(child, new ArrayList<>(currentPath), paths);
        }
      } else {
        // Leaf node - check if this path contains a CREATE action
        boolean hasCreateAction = currentPath.stream()
          .filter(p -> p instanceof ActionProfileNode)
          .map(p -> (ActionProfileNode) p)
          .anyMatch(ap -> "CREATE".equals(ap.action()));

        if (verbose) {
          LOGGER.info("Leaf node - path size: {}, hasCreateAction: {}", currentPath.size(), hasCreateAction);
          currentPath.stream()
            .filter(p -> p instanceof ActionProfileNode)
            .map(p -> (ActionProfileNode) p)
            .forEach(ap -> LOGGER.info("  ActionProfile action: '{}'", ap.action()));
        }

        if (hasCreateAction && !currentPath.isEmpty()) {
          paths.add(new JobProfilePath(new ArrayList<>(currentPath)));
        }
      }
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
     */
    private DeletionResult deleteProfileCascade(FolioClient client, ProfileDeletionData data) {
      System.out.println("\nDeleting job profile: " + data.name());

      int mappingSuccess = 0, mappingFail = 0;
      int actionSuccess = 0, actionFail = 0;
      int matchSuccess = 0, matchFail = 0;
      int jobSuccess = 0, jobFail = 0;

      // Delete mapping profiles first
      for (String mappingId : data.mappingIds()) {
        if (client.deleteMappingProfile(mappingId)) {
          mappingSuccess++;
          LOGGER.info("  Deleted mapping profile: {}", mappingId);
        } else {
          mappingFail++;
          LOGGER.warn("  Failed to delete mapping profile: {}", mappingId);
        }
      }

      // Delete action profiles
      for (String actionId : data.actionIds()) {
        if (client.deleteActionProfile(actionId)) {
          actionSuccess++;
          LOGGER.info("  Deleted action profile: {}", actionId);
        } else {
          actionFail++;
          LOGGER.warn("  Failed to delete action profile: {}", actionId);
        }
      }

      // Delete match profiles
      for (String matchId : data.matchIds()) {
        if (client.deleteMatchProfile(matchId)) {
          matchSuccess++;
          LOGGER.info("  Deleted match profile: {}", matchId);
        } else {
          matchFail++;
          LOGGER.warn("  Failed to delete match profile: {}", matchId);
        }
      }

      // Delete the job profile
      if (client.deleteJobProfile(data.id())) {
        System.out.println("  Deleted: " + data.name());
        jobSuccess++;
      } else {
        System.out.println("  Failed to delete: " + data.name());
        jobFail++;
      }

      return new DeletionResult(
        jobSuccess, jobFail, matchSuccess, matchFail,
        actionSuccess, actionFail, mappingSuccess, mappingFail
      );
    }
  }
}
