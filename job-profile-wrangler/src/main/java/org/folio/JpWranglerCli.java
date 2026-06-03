package org.folio;

import java.io.Console;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exports.GeneratePipeline;
import org.folio.exports.JobProfileAnalyzer;
import org.folio.exports.MinimalMarcRecordBuilder;
import org.folio.foundation.FoundationSeedProfile;
import org.folio.graph.GraphReader;
import org.folio.graph.GraphWriter;
import org.folio.graph.GraphWriterEnhanced;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.http.ReferenceDataManager;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.ImportReport;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;
import org.marc4j.MarcReader;
import org.marc4j.MarcStreamReader;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;

import com.fasterxml.jackson.databind.JsonNode;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.folio.profile.ProfileTree.children;

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
    JpWranglerCli.ImportMarcCommand.class,
    JpWranglerCli.SeedFoundationCommand.class,
    JpWranglerCli.DeleteCommand.class,
    JpWranglerCli.EnrichCommand.class
  },
  footer = "Note: The 'visualize' command requires GraphViz to be installed (https://graphviz.org/).")
public class JpWranglerCli implements Callable<Integer> {
  private static final Logger LOGGER = LogManager.getLogger(JpWranglerCli.class);
  private static final Pattern GENERATED_SEED_JOB_NAME = Pattern.compile("jp-(\\d{3}) \\d{12}-[A-Z]{5}");
  private static final Pattern GENERATED_SEED_SUB_PROFILE_NAME =
    Pattern.compile("jp-(\\d{3}) \\d{12}-[A-Z]{5} .+");

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
  static class FolioConnectionOptions {
    private static final String DEFAULT_ENV_FILE = ".env";

    private boolean environmentDefaultsApplied;
    private OkHttpClient httpClient;

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

    @Option(names = {"--env-file"}, description = "Path to dotenv file for FOLIO connection options")
    String envFile;

    @Option(names = {"--http-timeout-seconds"}, description = "HTTP read/write timeout for slower local FOLIO stacks")
    int httpTimeoutSeconds = 120;

    void ensurePassword() {
      applyEnvironmentDefaults();
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
      applyEnvironmentDefaults();
      if (token != null) {
        return token;
      } else if (tenant != null && username != null && password != null && baseUrl != null) {
        Optional<String> okapiToken = FolioClient.getOkapiToken(
          httpClient(),
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
      applyEnvironmentDefaults();
      if (baseUrl == null) {
        throw new IllegalArgumentException("FOLIO base URL is required");
      }

      OkHttpClient httpClient = httpClient();
      if (token != null) {
        return new FolioClient(httpClient, () -> HttpUrl.parse(baseUrl).newBuilder(), token, tenant, okapiUrl);
      } else if (tenant != null && username != null && password != null) {
        return new FolioClient(httpClient, () -> HttpUrl.parse(baseUrl).newBuilder(), getToken(), tenant, okapiUrl);
      } else {
        throw new IllegalArgumentException("Either token or tenant, username, and password must be provided");
      }
    }

    OkHttpClient httpClient() {
      if (httpClient != null) {
        return httpClient;
      }
      long timeoutSeconds = Math.max(1L, (long) httpTimeoutSeconds);
      // OkHttp clients own thread and connection pools, so token and API calls share one instance.
      httpClient = new OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(timeoutSeconds))
        .readTimeout(Duration.ofSeconds(timeoutSeconds))
        .writeTimeout(Duration.ofSeconds(timeoutSeconds))
        .build();
      return httpClient;
    }

    void applyEnvironmentDefaults() {
      if (environmentDefaultsApplied) {
        return;
      }
      environmentDefaultsApplied = true;

      boolean explicitEnvFile = envFile != null && !envFile.isBlank();
      Map<String, String> dotenv = loadDotenv(explicitEnvFile ? envFile : DEFAULT_ENV_FILE, explicitEnvFile);
      Map<String, String> environment = System.getenv();

      baseUrl = firstPresent(baseUrl, environment, dotenv,
        List.of("JP_WRANGLER_URL", "FOLIO_URL"),
        List.of("OKAPI", "OKAPI_URL", "URL"));
      token = firstPresent(token, environment, dotenv,
        List.of("JP_WRANGLER_TOKEN", "FOLIO_TOKEN", "OKAPI_TOKEN"),
        List.of("TOKEN"));
      tenant = firstPresent(tenant, environment, dotenv,
        List.of("JP_WRANGLER_TENANT", "FOLIO_TENANT"),
        List.of("TENANT"));
      okapiUrl = firstPresent(okapiUrl, environment, dotenv,
        List.of("JP_WRANGLER_OKAPI_URL", "JP_WRANGLER_X_OKAPI_URL", "FOLIO_OKAPI_URL"),
        List.of("X_OKAPI_URL"));
      username = firstPresent(username, environment, dotenv,
        List.of("JP_WRANGLER_USERNAME", "FOLIO_USERNAME", "FOLIO_USER"),
        List.of("USERNAME", "USER"));
      password = firstPresent(password, environment, dotenv,
        List.of("JP_WRANGLER_PASSWORD", "FOLIO_PASSWORD", "FOLIO_PASS"),
        List.of("PASSWORD", "PASS"));
    }

    private static String firstPresent(String explicitValue, Map<String, String> environment, Map<String, String> dotenv,
                                       List<String> scopedNames, List<String> genericNames) {
      if (explicitValue != null && !explicitValue.isBlank()) {
        return explicitValue;
      }
      return firstPresent(environment, scopedNames)
        .or(() -> firstPresent(dotenv, concat(scopedNames, genericNames)))
        .or(() -> firstPresent(environment, genericNames))
        .orElse(explicitValue);
    }

    private static Optional<String> firstPresent(Map<String, String> values, List<String> names) {
      return names.stream()
        .map(values::get)
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
    }

    private static List<String> concat(List<String> first, List<String> second) {
      List<String> combined = new ArrayList<>(first);
      combined.addAll(second);
      return combined;
    }

    static Map<String, String> loadDotenv(String dotenvPath) {
      return loadDotenv(dotenvPath, false);
    }

    static Map<String, String> loadDotenv(String dotenvPath, boolean required) {
      Path path = Paths.get(dotenvPath);
      if (!Files.exists(path)) {
        if (required) {
          throw new IllegalArgumentException("Dotenv file does not exist: " + dotenvPath);
        }
        return Map.of();
      }

      Map<String, String> values = new HashMap<>();
      try (BufferedReader reader = Files.newBufferedReader(path)) {
        String line;
        while ((line = reader.readLine()) != null) {
          parseDotenvLine(line).ifPresent(entry -> values.put(entry.name(), entry.value()));
        }
      } catch (IOException e) {
        if (required) {
          throw new IllegalArgumentException("Could not read dotenv file " + dotenvPath + ": " + e.getMessage(), e);
        }
        LOGGER.warn("Could not read dotenv file {}: {}", dotenvPath, e.getMessage());
      }
      return values;
    }

    static Optional<DotenvEntry> parseDotenvLine(String line) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return Optional.empty();
      }
      if (trimmed.startsWith("export ")) {
        trimmed = trimmed.substring("export ".length()).trim();
      }

      int separator = trimmed.indexOf('=');
      if (separator <= 0) {
        return Optional.empty();
      }

      String name = trimmed.substring(0, separator).trim();
      String value = trimmed.substring(separator + 1).trim();
      if (name.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(new DotenvEntry(name, stripDotenvQuotes(value)));
    }

    private static String stripDotenvQuotes(String value) {
      if (value.length() < 2) {
        return value;
      }
      char first = value.charAt(0);
      char last = value.charAt(value.length() - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        return value.substring(1, value.length() - 1);
      }
      return value;
    }

    record DotenvEntry(String name, String value) { }
  }

  // Helper class for repository operations
  static class RepositoryOptions {
    private static final String DEFAULT_WRITABLE_REPOSITORY = "./repository";
    private static final int MAX_EMBEDDED_REPOSITORY_ID = 999;
    private static Path embeddedRepositoryPath;

    @Option(names = {"-r", "--repository"},
      description = "Path to job profile repository. If omitted, read commands use ./repository when populated, otherwise the embedded repository.")
    String repoPath;

    void ensureRepositoryExists() {
      Path path = Paths.get(writableRepoPath());
      if (!Files.exists(path)) {
        try {
          Files.createDirectories(path);
          LOGGER.info("Created repository directory: {}", path);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to create repository directory: " + e.getMessage(), e);
        }
      }
    }

    String writableRepoPath() {
      return hasExplicitRepository() ? explicitRepoPath() : DEFAULT_WRITABLE_REPOSITORY;
    }

    String readableRepoPath() throws IOException {
      return readableRepoPath(null);
    }

    String readableRepoPath(Integer requiredRepoId) throws IOException {
      if (hasExplicitRepository()) {
        return explicitRepoPath();
      }
      Path localRepository = Paths.get(DEFAULT_WRITABLE_REPOSITORY);
      if (containsDotProfiles(localRepository)) {
        return localRepository.toString();
      }
      if (Files.isDirectory(localRepository)) {
        LOGGER.warn("Local repository '{}' has no DOT profiles; using embedded bundled repository",
          localRepository);
      }
      return embeddedRepositoryPath(requiredRepoId).toString();
    }

    boolean hasExplicitRepository() {
      return repoPath != null;
    }

    private String explicitRepoPath() {
      if (repoPath.isBlank()) {
        throw new IllegalArgumentException("--repository must not be blank");
      }
      return repoPath;
    }

    private static boolean containsDotProfiles(Path path) throws IOException {
      if (!Files.isDirectory(path)) {
        return false;
      }
      try (java.util.stream.Stream<Path> stream = Files.list(path)) {
        return stream
          .filter(Files::isRegularFile)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> GraphWriter.DOT_FILE_PATTERN.matcher(name).matches());
      }
    }

    private static synchronized Path embeddedRepositoryPath(Integer requiredRepoId) throws IOException {
      if (embeddedRepositoryPath != null && embeddedRepositoryHasRequiredProfiles(embeddedRepositoryPath, requiredRepoId)) {
        return embeddedRepositoryPath;
      }

      Path previousRepo = embeddedRepositoryPath;
      Path tempRepo = Files.createTempDirectory("jp-wrangler-embedded-repository-");
      tempRepo.toFile().deleteOnExit();
      int copied = 0;
      ClassLoader classLoader = JpWranglerCli.class.getClassLoader();
      for (int id = 1; id <= MAX_EMBEDDED_REPOSITORY_ID; id++) {
        String fileName = GraphWriter.genGraphFileName(id);
        String resourcePath = "repository/" + fileName;
        try (InputStream input = classLoader.getResourceAsStream(resourcePath)) {
          if (input == null) {
            continue;
          }
          Path target = tempRepo.resolve(fileName);
          Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
          target.toFile().deleteOnExit();
          copied++;
        }
      }
      if (copied == 0) {
        Files.deleteIfExists(tempRepo);
        throw new IOException("Embedded repository is not available in this jar");
      }
      embeddedRepositoryPath = tempRepo;
      // Tests can re-extract for a required id; remove the old temp tree instead of orphaning it.
      if (previousRepo != null && !previousRepo.equals(tempRepo)) {
        deleteDirectoryQuietly(previousRepo);
      }
      return embeddedRepositoryPath;
    }

    private static void deleteDirectoryQuietly(Path directory) {
      if (directory == null || !Files.isDirectory(directory)) {
        return;
      }
      try (java.util.stream.Stream<Path> stream = Files.walk(directory)) {
        stream
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(path -> {
            try {
              Files.deleteIfExists(path);
            } catch (IOException ignored) {
              path.toFile().deleteOnExit();
            }
          });
      } catch (IOException ignored) {
        directory.toFile().deleteOnExit();
      }
    }

    private static boolean embeddedRepositoryHasRequiredProfiles(Path path, Integer requiredRepoId) throws IOException {
      if (requiredRepoId == null) {
        return containsDotProfiles(path);
      }
      return Files.isRegularFile(path.resolve(GraphWriter.genGraphFileName(requiredRepoId)));
    }

    /**
     * Lists all available job profile IDs in the repository.
     *
     * @return List of available job profile IDs
     * @throws IOException If an error occurs reading the repository
     */
    java.util.List<Integer> listAvailableProfileIds() throws IOException {
      Path path = Paths.get(readableRepoPath());
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
      try {
        repoPath = writableRepoPath();
        ensureRepositoryExists();
        FolioClient client = folioOptions.createFolioClient();
        RepoImport importer = new RepoImport(client, repoPath);
        ImportReport report = importer.importProfiles();
        Path reportPath = writeImportReport(report);
        System.out.printf("Added: %d, Duplicate: %d, Blocked: %d, Errors: %d%n",
          report.addedCount(), report.duplicateCount(), report.blockedCount(), report.errorCount());
        System.out.println("Report: " + reportPath);
        return exitCodeForReport(report);
      } catch (IllegalArgumentException e) {
        LOGGER.error("Import failed: {}", e.getMessage());
        return 1;
      } catch (Exception e) {
        LOGGER.error("Import failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    static int exitCodeForReport(ImportReport report) {
      return report.blockedCount() > 0 || report.errorCount() > 0 ? 1 : 0;
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

    @Option(names = {"--foundation-seed-profiles", "--foundation-profiles"},
      description = "Export built-in foundation seed profiles jp-900, jp-901, and jp-902")
    boolean exportFoundationSeedProfiles;

    @Override
    public Integer call() {
      if (selectedExportModeCount(repoId, exportAll, exportFoundationSeedProfiles) != 1) {
        LOGGER.error("Specify --id, --all, or --foundation-seed-profiles");
        return 1;
      }

      try {
        FolioClient client = folioOptions.createFolioClient();
        ProfileHydration hydration = new ProfileHydration(client);

        if (exportFoundationSeedProfiles) {
          List<DeleteCommand.ProfileDeletionData> seedProfilesToReplace = foundationSeedProfilesToReplace(client);
          DeleteCommand.ProfileReferences orphanSeedProfilesToReplace =
            foundationSeedOrphanProfilesToReplace(client, seedProfilesToReplace);
          Map<FoundationSeedProfile, Graph<Profile, RegularEdge>> seedGraphs = new LinkedHashMap<>();
          for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
            seedGraphs.put(seedProfile, seedProfile.graph());
          }

          List<Integer> failedProfileIds = new ArrayList<>();
          List<ProfileHydration.HydrationResult> createdSeedProfiles = new ArrayList<>();
          for (Map.Entry<FoundationSeedProfile, Graph<Profile, RegularEdge>> seedEntry : seedGraphs.entrySet()) {
            FoundationSeedProfile seedProfile = seedEntry.getKey();
            try {
              var result = hydration.hydrateFoundationSeedProfile(seedProfile, seedEntry.getValue());
              if (result.isPresent()) {
                createdSeedProfiles.add(result.get());
                LOGGER.info("Exported foundation seed profile jp-{}", seedProfile.repoId());
              } else {
                LOGGER.error("Failed to export foundation seed profile jp-{}", seedProfile.repoId());
                failedProfileIds.add(seedProfile.repoId());
              }
            } catch (Exception e) {
              LOGGER.error("Error exporting foundation seed profile jp-{}: {}",
                seedProfile.repoId(), e.getMessage());
              failedProfileIds.add(seedProfile.repoId());
            }
          }
          if (!failedProfileIds.isEmpty()) {
            rollbackSeedProfiles(hydration, createdSeedProfiles);
            LOGGER.error("Foundation seed profile export failed for repository IDs: {}", failedProfileIds);
            return 1;
          }
          DeleteCommand.DeletionResult deletionResult = deleteSeedProfiles(client, seedProfilesToReplace,
            orphanSeedProfilesToReplace);
          if (deletionResult.totalFailed() > 0) {
            LOGGER.error("Foundation seed profile replacement left {} old profile deletion(s) failed",
              deletionResult.totalFailed());
            return 1;
          }
        } else if (exportAll) {
          // Export all profiles
          String sourceRepoPath = readableRepoPath();
          java.util.List<Integer> profileIds = listAvailableProfileIds();
          if (profileIds.isEmpty()) {
            System.err.println("No job profiles found in repository: " + sourceRepoPath);
            return 1;
          }

          List<Integer> failedProfileIds = new ArrayList<>();
          for (Integer id : profileIds) {
            try {
              Graph<Profile, RegularEdge> graph = GraphReader.read(sourceRepoPath, id);
              var result = hydration.hydrate(id, graph);
              if (result.isPresent()) {
                LOGGER.info("Exported job profile {}", id);
              } else {
                LOGGER.error("Failed to export job profile {}", id);
                failedProfileIds.add(id);
              }
            } catch (Exception e) {
              LOGGER.error("Error exporting job profile {}: {}", id, e.getMessage());
              failedProfileIds.add(id);
            }
          }
          if (!failedProfileIds.isEmpty()) {
            LOGGER.error("Export failed for job profile repository IDs: {}", failedProfileIds);
            return 1;
          }
        } else {
          // Export specific profile
          String sourceRepoPath = readableRepoPath(repoId);
          String filename = GraphWriter.genGraphFileName(repoId);
          Path profilePath = Paths.get(sourceRepoPath, filename);

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

          Graph<Profile, RegularEdge> graph = GraphReader.read(sourceRepoPath, repoId);
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

    static int selectedExportModeCount(Integer repoId, boolean exportAll, boolean exportFoundationSeedProfiles) {
      int count = 0;
      if (repoId != null) {
        count++;
      }
      if (exportAll) {
        count++;
      }
      if (exportFoundationSeedProfiles) {
        count++;
      }
      return count;
    }

    static void rollbackSeedProfiles(
        ProfileHydration hydration,
        List<ProfileHydration.HydrationResult> createdSeedProfiles) {
      for (int i = createdSeedProfiles.size() - 1; i >= 0; i--) {
        hydration.rollback(createdSeedProfiles.get(i));
      }
    }

    static List<DeleteCommand.ProfileDeletionData> foundationSeedProfilesToReplace(FolioClient client) {
      List<DeleteCommand.ProfileDeletionData> profilesToReplace = new ArrayList<>();
      for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
        client.getJobProfiles(Map.of("query", seedProfileNameQuery(seedProfile.repoId())))
          // Name prefix is only the search window; the generated suffix proves wrangler ownership.
          .filter(profile -> isGeneratedFoundationSeedName(profile, seedProfile.repoId(), false))
          .forEach(profile -> profilesToReplace.add(seedProfileDeletionData(client, profile)));
      }
      return profilesToReplace;
    }

    static DeleteCommand.ProfileReferences foundationSeedOrphanProfilesToReplace(
        FolioClient client,
        List<DeleteCommand.ProfileDeletionData> seedProfilesToReplace) {
      DeleteCommand.ProfileReferences namedSeedChildren = DeleteCommand.ProfileReferences.empty();
      for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
        Map<String, String> query = Map.of("query", seedProfileNameQuery(seedProfile.repoId()));
        collectGeneratedSeedProfileIds(client.getActionProfiles(query), namedSeedChildren.actionIds(),
          seedProfile.repoId());
        collectGeneratedSeedProfileIds(client.getMappingProfiles(query), namedSeedChildren.mappingIds(),
          seedProfile.repoId());
        collectGeneratedSeedProfileIds(client.getMatchProfiles(query), namedSeedChildren.matchIds(),
          seedProfile.repoId());
      }

      DeleteCommand.ProfileReferences referencedBySeedJobs = DeleteCommand.ProfileReferences.empty();
      seedProfilesToReplace.stream()
        .map(DeleteCommand.ProfileDeletionData::references)
        .forEach(referencedBySeedJobs::addAll);
      return namedSeedChildren.without(referencedBySeedJobs);
    }

    private static void collectGeneratedSeedProfileIds(Stream<JsonNode> profiles, Set<String> target, int repoId) {
      profiles
        .filter(profile -> isGeneratedFoundationSeedName(profile, repoId, true))
        .map(profile -> profile.path("id").asText(""))
        .filter(id -> !id.isBlank())
        .forEach(target::add);
    }

    private static boolean isGeneratedFoundationSeedName(JsonNode profile, int repoId, boolean subProfile) {
      String name = profile.path("name").asText("");
      Pattern pattern = subProfile ? GENERATED_SEED_SUB_PROFILE_NAME : GENERATED_SEED_JOB_NAME;
      var matcher = pattern.matcher(name);
      if (!matcher.matches() || Integer.parseInt(matcher.group(1)) != repoId) {
        LOGGER.warn("Skipping profile '{}' because it is not a wrangler-generated foundation seed name", name);
        return false;
      }
      return true;
    }

    private static DeleteCommand.ProfileDeletionData seedProfileDeletionData(FolioClient client, JsonNode profile) {
      String id = profile.path("id").asText();
      String name = profile.path("name").asText();
      Optional<JsonNode> snapshotOpt = client.getJobProfileSnapshot(id);
      if (snapshotOpt.isEmpty()) {
        // A missing snapshot means the cascade scope is unknown; deleting by name would be unsafe.
        throw new IllegalStateException("Could not fetch foundation seed profile snapshot for " + name + " (" + id
          + "); refusing to replace because child profile references are unknown.");
      }
      DeleteCommand.ProfileReferences references = DeleteCommand.collectProfileIds(snapshotOpt.get());
      return new DeleteCommand.ProfileDeletionData(id, name, references.matchIds(), references.actionIds(),
        references.mappingIds());
    }

    static String seedProfileNameQuery(int repoId) {
      return "name==\"jp-" + repoId + " *\"";
    }

    static DeleteCommand.DeletionResult deleteSeedProfiles(
        FolioClient client,
        List<DeleteCommand.ProfileDeletionData> seedProfilesToReplace) {
      return deleteSeedProfiles(client, seedProfilesToReplace, DeleteCommand.ProfileReferences.empty());
    }

    static DeleteCommand.DeletionResult deleteSeedProfiles(
        FolioClient client,
        List<DeleteCommand.ProfileDeletionData> seedProfilesToReplace,
        DeleteCommand.ProfileReferences orphanSeedProfilesToReplace) {
      DeleteCommand.ProfileReferences sharedReferences = sharedSeedReferences(client, seedProfilesToReplace,
        orphanSeedProfilesToReplace);
      if (!sharedReferences.isEmpty()) {
        // Seed replacement must not break unrelated job profiles that reused a seed child.
        LOGGER.warn("Skipping foundation seed sub-profiles still referenced by non-seed job profiles: "
            + "{} match, {} action, {} mapping",
          sharedReferences.matchIds().size(),
          sharedReferences.actionIds().size(),
          sharedReferences.mappingIds().size());
        seedProfilesToReplace = seedProfilesToReplace.stream()
          .map(profile -> profile.without(sharedReferences))
          .toList();
        orphanSeedProfilesToReplace = orphanSeedProfilesToReplace.without(sharedReferences);
      }

      DeleteCommand.DeletionResult totalResult = DeleteCommand.DeletionResult.empty();
      for (DeleteCommand.ProfileDeletionData profile : seedProfilesToReplace) {
        totalResult = DeleteCommand.DeletionResult.combine(totalResult,
          DeleteCommand.deleteProfileCascade(client, profile));
      }
      totalResult = DeleteCommand.DeletionResult.combine(totalResult,
        DeleteCommand.deleteProfileReferences(client, orphanSeedProfilesToReplace, "orphan foundation seed sub-profile"));
      if (!seedProfilesToReplace.isEmpty()) {
        LOGGER.info("Replaced {} existing foundation seed profile(s)", seedProfilesToReplace.size());
      }
      return totalResult;
    }

    private static DeleteCommand.ProfileReferences sharedSeedReferences(
        FolioClient client,
        List<DeleteCommand.ProfileDeletionData> seedProfilesToReplace,
        DeleteCommand.ProfileReferences orphanSeedProfilesToReplace) {
      DeleteCommand.ProfileReferences selectedReferences = DeleteCommand.ProfileReferences.empty();
      seedProfilesToReplace.stream()
        .map(DeleteCommand.ProfileDeletionData::references)
        .forEach(selectedReferences::addAll);
      selectedReferences.addAll(orphanSeedProfilesToReplace);
      if (selectedReferences.isEmpty()) {
        return DeleteCommand.ProfileReferences.empty();
      }

      Set<String> selectedJobProfileIds = seedProfilesToReplace.stream()
        .map(DeleteCommand.ProfileDeletionData::id)
        .collect(java.util.stream.Collectors.toSet());
      return DeleteCommand.sharedReferencesFromNonSelectedProfiles(client, client.getJobProfiles().toList(),
        selectedJobProfileIds, selectedReferences);
    }
  }

  @Command(name = "list", description = "List job profiles in repository", mixinStandardHelpOptions = true)
  static class ListCommand extends RepositoryOptions implements Callable<Integer> {
    @Override
    public Integer call() {
      try {
        java.util.List<Integer> profileIds = listAvailableProfileIds();

        if (profileIds.isEmpty()) {
          System.out.println("No job profiles found in repository: " + readableRepoPath());
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
        String sourceRepoPath = readableRepoPath(repoId);

        // Check if the profile file exists before trying to read it
        String filename = GraphWriter.genGraphFileName(repoId);
        Path profilePath = Paths.get(sourceRepoPath, filename);

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

        Graph<Profile, RegularEdge> graph = GraphReader.read(sourceRepoPath, repoId);

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

        if (!isOutputParentWritable()) {
          return 1;
        }

        // Connect to FOLIO to get the job profile snapshot
        FolioClient client = folioOptions.createFolioClient();

        // Get the job profile snapshot directly using the provided UUID
        Optional<JsonNode> snapshot = client.getJobProfileSnapshot(jobProfileId);

        if (snapshot.isEmpty()) {
          LOGGER.error("Failed to retrieve job profile snapshot for UUID: {}", jobProfileId);
          return 1;
        }

        LOGGER.info("Successfully retrieved job profile snapshot for UUID: {}", jobProfileId);

        GeneratePipeline pipeline = new GeneratePipeline(
          client,
          () -> fetchReferenceDataContext(client),
          Paths.get(outputPath),
          verbose);
        return pipeline.generate(snapshot.get(), jobProfileId);

      } catch (Exception e) {
        LOGGER.error("Test data generation failed: {}", e.getMessage(), e);
        return 1;
      }
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

        Set<String> selectedJobProfileIds = matchingProfiles.stream()
          .map(profile -> profile.path("id").asText())
          .collect(java.util.stream.Collectors.toSet());
        List<ProfileDeletionData> deletionDataList = new ArrayList<>();
        ProfileReferences selectedReferences = ProfileReferences.empty();

        for (JsonNode profile : matchingProfiles) {
          String id = profile.path("id").asText();
          String name = profile.path("name").asText();

          Optional<JsonNode> snapshotOpt = client.getJobProfileSnapshot(id);
          if (snapshotOpt.isEmpty()) {
            LOGGER.error("Could not fetch job profile snapshot for {} ({}); refusing to delete because child "
              + "profile references are unknown.", name, id);
            return 1;
          }
          ProfileReferences references = collectProfileIds(snapshotOpt.get());
          selectedReferences.addAll(references);

          System.out.println("  - " + name + " (ID: " + id + ")");
          System.out.println("    - " + references.matchIds().size() + " match profile(s)");
          System.out.println("    - " + references.actionIds().size() + " action profile(s)");
          System.out.println("    - " + references.mappingIds().size() + " mapping profile(s)");

          totalMatchProfiles += references.matchIds().size();
          totalActionProfiles += references.actionIds().size();
          totalMappingProfiles += references.mappingIds().size();

          deletionDataList.add(new ProfileDeletionData(id, name, references.matchIds(), references.actionIds(),
            references.mappingIds()));
        }

        ProfileReferences sharedReferences = sharedReferencesFromNonSelectedProfiles(client, allProfiles,
          selectedJobProfileIds, selectedReferences);
        if (!sharedReferences.isEmpty()) {
          System.out.println("\nSkipping shared sub-profiles that are still referenced by non-selected job profiles:");
          System.out.println("  Match profiles:   " + sharedReferences.matchIds().size());
          System.out.println("  Action profiles:  " + sharedReferences.actionIds().size());
          System.out.println("  Mapping profiles: " + sharedReferences.mappingIds().size());
          deletionDataList = deletionDataList.stream()
            .map(data -> data.without(sharedReferences))
            .toList();
          totalMatchProfiles -= sharedReferences.matchIds().size();
          totalActionProfiles -= sharedReferences.actionIds().size();
          totalMappingProfiles -= sharedReferences.mappingIds().size();
        }

        int totalSubProfiles = totalMatchProfiles + totalActionProfiles + totalMappingProfiles;

        if (!confirm) {
          System.out.println("\nDry-run mode: No profiles were deleted.");
          System.out.println("Run with --confirm to delete " + matchingProfiles.size() +
            " job profile(s) and " + totalSubProfiles + " sub-profile(s).");
          return 0;
        }

        // Delete parent links before child profiles: Job → Action → Mapping → Match.
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
    static ProfileReferences collectProfileIds(JsonNode node) {
      ProfileReferences references = ProfileReferences.empty();
      collectProfileIds(node, references);
      return references;
    }

    private static void collectProfileIds(JsonNode node, ProfileReferences references) {
      String contentType = node.path("contentType").asText();
      String id = node.path("content").path("id").asText();

      if (!id.isEmpty()) {
        switch (contentType) {
          case "MAPPING_PROFILE" -> references.mappingIds().add(id);
          case "ACTION_PROFILE" -> references.actionIds().add(id);
          case "MATCH_PROFILE" -> references.matchIds().add(id);
          // JOB_PROFILE is handled separately
        }
      }

      JsonNode childNodes = children(node);
      if (childNodes.isArray()) {
        for (JsonNode child : childNodes) {
          collectProfileIds(child, references);
        }
      }
    }

    static ProfileReferences sharedReferencesFromNonSelectedProfiles(FolioClient client,
                                                                     List<JsonNode> allProfiles,
                                                                     Set<String> selectedJobProfileIds,
                                                                     ProfileReferences selectedReferences) {
      ProfileReferences sharedReferences = ProfileReferences.empty();
      if (selectedReferences.isEmpty()) {
        return sharedReferences;
      }

      for (JsonNode profile : allProfiles) {
        String id = profile.path("id").asText();
        if (selectedJobProfileIds.contains(id)) {
          continue;
        }
        Optional<JsonNode> snapshotOpt = client.getJobProfileSnapshot(id);
        if (snapshotOpt.isEmpty()) {
          throw new IllegalStateException("Could not fetch job profile snapshot for non-selected profile " + id
            + "; refusing to delete because shared profile references cannot be verified.");
        }
        sharedReferences.addAll(collectProfileIds(snapshotOpt.get()).intersection(selectedReferences));
      }
      return sharedReferences;
    }

    record ProfileReferences(
      Set<String> matchIds,
      Set<String> actionIds,
      Set<String> mappingIds
    ) {
      static ProfileReferences empty() {
        return new ProfileReferences(new HashSet<>(), new HashSet<>(), new HashSet<>());
      }

      boolean isEmpty() {
        return matchIds.isEmpty() && actionIds.isEmpty() && mappingIds.isEmpty();
      }

      void addAll(ProfileReferences other) {
        matchIds.addAll(other.matchIds);
        actionIds.addAll(other.actionIds);
        mappingIds.addAll(other.mappingIds);
      }

      ProfileReferences intersection(ProfileReferences other) {
        return new ProfileReferences(intersection(matchIds, other.matchIds),
          intersection(actionIds, other.actionIds),
          intersection(mappingIds, other.mappingIds));
      }

      ProfileReferences without(ProfileReferences other) {
        return new ProfileReferences(without(matchIds, other.matchIds),
          without(actionIds, other.actionIds),
          without(mappingIds, other.mappingIds));
      }

      private static Set<String> intersection(Set<String> first, Set<String> second) {
        Set<String> result = new HashSet<>(first);
        result.retainAll(second);
        return result;
      }

      private static Set<String> without(Set<String> ids, Set<String> exclusions) {
        Set<String> result = new HashSet<>(ids);
        result.removeAll(exclusions);
        return result;
      }
    }

    /**
     * Holds data needed for cascade deletion of a job profile and its sub-profiles.
     */
    record ProfileDeletionData(
      String id,
      String name,
      Set<String> matchIds,
      Set<String> actionIds,
      Set<String> mappingIds
    ) {
      ProfileReferences references() {
        return new ProfileReferences(new HashSet<>(matchIds), new HashSet<>(actionIds), new HashSet<>(mappingIds));
      }

      ProfileDeletionData without(ProfileReferences references) {
        return new ProfileDeletionData(id, name, without(matchIds, references.matchIds()),
          without(actionIds, references.actionIds()),
          without(mappingIds, references.mappingIds()));
      }

      private static Set<String> without(Set<String> ids, Set<String> exclusions) {
        Set<String> result = new HashSet<>(ids);
        result.removeAll(exclusions);
        return result;
      }
    }

    /**
     * Holds the result of deletion operations with success/fail counts for each profile type.
     */
    record DeletionResult(
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
     * Delete the job first to remove the root association, then actions before mappings because actions retain the
     * action-to-mapping association in FOLIO until they are removed.
     */
    static DeletionResult deleteProfileCascade(FolioClient client, ProfileDeletionData data) {
      System.out.println("\nDeleting job profile: " + data.name());

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
          jobSuccess, jobFail, 0, 0,
          0, 0, 0, 0
        );
      }

      DeletionResult subProfileResult = deleteProfileReferences(client, data.references(), "sub-profile");
      return new DeletionResult(
        jobSuccess, jobFail,
        subProfileResult.matchSuccess(), subProfileResult.matchFail(),
        subProfileResult.actionSuccess(), subProfileResult.actionFail(),
        subProfileResult.mappingSuccess(), subProfileResult.mappingFail()
      );
    }

    static DeletionResult deleteProfileReferences(
        FolioClient client,
        ProfileReferences references,
        String label) {
      int mappingSuccess = 0, mappingFail = 0;
      int actionSuccess = 0, actionFail = 0;
      int matchSuccess = 0, matchFail = 0;

      // Actions retain their action-to-mapping association in FOLIO, so mappings must be removed after actions.
      for (String actionId : references.actionIds()) {
        if (client.deleteActionProfile(actionId)) {
          actionSuccess++;
          LOGGER.info("  Deleted {} action profile: {}", label, actionId);
        } else {
          actionFail++;
          LOGGER.warn("  Failed to delete {} action profile: {}", label, actionId);
        }
      }

      // Delete mapping profiles after action profiles have released their associations.
      for (String mappingId : references.mappingIds()) {
        if (client.deleteMappingProfile(mappingId)) {
          mappingSuccess++;
          LOGGER.info("  Deleted {} mapping profile: {}", label, mappingId);
        } else {
          mappingFail++;
          LOGGER.warn("  Failed to delete {} mapping profile: {}", label, mappingId);
        }
      }

      // Delete match profiles (may fail if shared with other job profiles)
      for (String matchId : references.matchIds()) {
        if (client.deleteMatchProfile(matchId)) {
          matchSuccess++;
          LOGGER.info("  Deleted {} match profile: {}", label, matchId);
        } else {
          matchFail++;
          LOGGER.warn("  Failed to delete {} match profile: {}", label, matchId);
        }
      }

      return new DeletionResult(
        0, 0, matchSuccess, matchFail,
        actionSuccess, actionFail, mappingSuccess, mappingFail
      );
    }
  }

  @Command(name = "seed-foundation",
    description = "Upload foundation MARC files with the built-in seed profiles and wait for completion",
    mixinStandardHelpOptions = true)
  static class SeedFoundationCommand implements Callable<Integer> {
    private static final Set<String> SUCCESS_STATUSES = Set.of(
      "COMPLETED", "COMMITTED", "SUCCESS", "RUNNING_COMPLETE"
    );
    private static final Set<String> FAILURE_STATUSES = Set.of(
      "ERROR", "FAILED", "FAIL", "CANCELLED", "CANCELED", "DISCARDED"
    );

    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(arity = "1..*", description = "Foundation MARC files to upload")
    List<Path> files;

    @Option(names = {"--poll-interval-seconds"}, description = "Seconds between job status checks")
    int pollIntervalSeconds = 5;

    @Option(names = {"--timeout-seconds"}, description = "Maximum seconds to wait for each foundation import")
    int timeoutSeconds = 300;

    @Override
    public Integer call() {
      try {
        folioOptions.ensurePassword();
        FolioClient client = folioOptions.createFolioClient();

        int failures = 0;
        for (Path file : files) {
          if (!Files.isRegularFile(file)) {
            LOGGER.error("Foundation file does not exist: {}", file);
            failures++;
            continue;
          }
          FoundationBucket bucket = detectFoundationBucket(file);
          LOGGER.info("Detected {} foundation shape for {}", bucket.label(), file);
          Optional<JsonNode> profile = latestProfile(client, bucket.profilePrefix());
          if (profile.isEmpty()) {
            LOGGER.error("No foundation seed job profile found for prefix {}", bucket.profilePrefix());
            failures++;
            continue;
          }
          if (!submitMarcFile(client, file, profile.get(), pollIntervalSeconds, timeoutSeconds)) {
            failures++;
          }
        }
        return failures == 0 ? 0 : 1;
      } catch (Exception e) {
        LOGGER.error("Foundation seeding failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    static boolean submitMarcFile(
        FolioClient client,
        Path file,
        JsonNode profile,
        int pollIntervalSeconds,
        int timeoutSeconds) throws InterruptedException {
      String profileId = profile.path("id").asText();
      String profileName = profile.path("name").asText();
      String fileName = file.getFileName().toString();
      LOGGER.info("Uploading {} with job profile {} ({})", fileName, profileName, profileId);

      Optional<JsonNode> uploadDefinitionOpt = client.createUploadDefinition(fileName);
      if (uploadDefinitionOpt.isEmpty()) {
        return false;
      }
      JsonNode uploadDefinition = uploadDefinitionOpt.get();
      String uploadDefinitionId = uploadDefinition.path("id").asText();
      String fileDefinitionId = uploadDefinition.path("fileDefinitions").path(0).path("id").asText();
      if (uploadDefinitionId.isBlank() || fileDefinitionId.isBlank()) {
        LOGGER.error("Upload definition response did not include required ids for {}", fileName);
        return false;
      }

      Optional<JsonNode> uploadUrlOpt = client.getUploadUrl(fileName);
      if (uploadUrlOpt.isEmpty()) {
        return false;
      }
      JsonNode uploadUrl = uploadUrlOpt.get();
      String url = uploadUrl.path("url").asText();
      String key = uploadUrl.path("key").asText();
      String uploadId = uploadUrl.path("uploadId").asText();
      if (url.isBlank() || key.isBlank() || uploadId.isBlank()) {
        LOGGER.error("Upload URL response did not include url, key, and uploadId for {}", fileName);
        return false;
      }

      Optional<String> etag = client.uploadFileToStorage(url, file);
      if (etag.isEmpty()) {
        return false;
      }
      if (!client.assembleStorageFile(uploadDefinitionId, fileDefinitionId, key, etag.get(), uploadId)) {
        return false;
      }

      Optional<JsonNode> assembledDefinition = client.getUploadDefinition(uploadDefinitionId);
      if (assembledDefinition.isEmpty()) {
        return false;
      }
      Instant submittedAfter = Instant.now();
      Optional<JsonNode> processResponse =
        client.processUploadedFiles(uploadDefinitionId, assembledDefinition.get(), profileId, profileName);
      if (processResponse.isEmpty()) {
        return false;
      }

      Optional<String> jobExecutionId = findJobExecutionId(processResponse.get());
      if (jobExecutionId.isEmpty()) {
        jobExecutionId = awaitLatestJobExecutionId(client, profileId, fileName, submittedAfter,
          pollIntervalSeconds, timeoutSeconds);
      }
      if (jobExecutionId.isEmpty()) {
        LOGGER.error("Could not locate job execution for {}", fileName);
        return false;
      }
      return waitForCompletion(client, jobExecutionId.get(), fileName, pollIntervalSeconds, timeoutSeconds);
    }

    static boolean waitForCompletion(
        FolioClient client,
        String jobExecutionId,
        String fileName,
        int pollIntervalSeconds,
        int timeoutSeconds)
        throws InterruptedException {
      long timeoutMillis = Math.max(1L, (long) timeoutSeconds) * 1000L;
      long sleepMillis = Math.max(1L, (long) pollIntervalSeconds) * 1000L;
      long deadline = System.currentTimeMillis() + timeoutMillis;
      JsonNode lastExecution = null;
      while (System.currentTimeMillis() <= deadline) {
        Optional<JsonNode> execution = client.getJobExecution(jobExecutionId);
        if (execution.isPresent()) {
          lastExecution = execution.get();
          String status = statusText(lastExecution);
          LOGGER.info("Foundation import {} job {} status: {}", fileName, jobExecutionId, status);
          if (isSuccess(lastExecution)) {
            LOGGER.info("Foundation import completed for {} ({})", fileName, jobExecutionId);
            return true;
          }
          if (isFailure(lastExecution)) {
            LOGGER.error("Foundation import failed for {} ({}): {}", fileName, jobExecutionId, status);
            return false;
          }
        }
        Thread.sleep(sleepMillis);
      }
      LOGGER.error("Timed out waiting for foundation import {} ({}) to complete. Last status: {}",
        fileName, jobExecutionId, lastExecution == null ? "unknown" : statusText(lastExecution));
      return false;
    }

    static Optional<JsonNode> latestProfile(FolioClient client, String prefix) {
      return client.getJobProfiles(Map.of("query", "name==\"" + prefix + "*\""))
        .max((first, second) -> profileCreatedDate(first).compareTo(profileCreatedDate(second)));
    }

    private static Optional<String> awaitLatestJobExecutionId(
        FolioClient client,
        String profileId,
        String fileName,
        Instant submittedAfter,
        int pollIntervalSeconds,
        int timeoutSeconds) throws InterruptedException {
      long sleepMillis = Math.max(1L, (long) pollIntervalSeconds) * 1000L;
      long timeoutMillis = Math.max(1L, (long) timeoutSeconds) * 1000L;
      long discoveryMillis = Math.min(timeoutMillis, Math.max(60_000L, sleepMillis * 3));
      long deadline = System.currentTimeMillis() + discoveryMillis;
      while (System.currentTimeMillis() <= deadline) {
        Optional<String> jobExecutionId = latestJobExecutionId(client, profileId, fileName, submittedAfter);
        if (jobExecutionId.isPresent()) {
          return jobExecutionId;
        }
        Thread.sleep(sleepMillis);
      }
      return Optional.empty();
    }

    static Optional<String> latestJobExecutionId(FolioClient client, String profileId, String fileName) {
      return latestJobExecutionId(client, profileId, fileName, Instant.EPOCH);
    }

    static Optional<String> latestJobExecutionId(
        FolioClient client,
        String profileId,
        String fileName,
        Instant submittedAfter) {
      return client.getJobExecutions(25)
        .filter(execution -> profileId.equals(execution.path("jobProfileInfo").path("id").asText()))
        .filter(execution -> matchesExecutionFile(execution, fileName))
        .filter(execution -> startedAtOrEpoch(execution).compareTo(submittedAfter.minusSeconds(1)) >= 0)
        .map(execution -> execution.path("id").asText(""))
        .filter(id -> !id.isBlank())
        .findFirst();
    }

    static boolean matchesExecutionFile(JsonNode execution, String fileName) {
      String executionFileName = execution.path("fileName").asText("");
      String sourcePath = execution.path("sourcePath").asText("");
      return matchesDataImportFileName(executionFileName, fileName)
        || matchesDataImportFileName(sourcePath, fileName)
        || matchesDataImportFileName(lastPathSegment(sourcePath), fileName);
    }

    private static String lastPathSegment(String path) {
      int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
      return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private static boolean matchesDataImportFileName(String executionValue, String originalFileName) {
      if (originalFileName.equals(executionValue)) {
        return true;
      }
      String executionFileName = lastPathSegment(executionValue);
      if (originalFileName.equals(executionFileName)) {
        return true;
      }
      if (executionFileName.matches("\\d+-" + java.util.regex.Pattern.quote(originalFileName))) {
        return true;
      }

      int dot = originalFileName.lastIndexOf('.');
      if (dot <= 0) {
        return false;
      }
      String base = java.util.regex.Pattern.quote(originalFileName.substring(0, dot));
      String extension = java.util.regex.Pattern.quote(originalFileName.substring(dot));
      return executionFileName.matches("\\d+-" + base + "_\\d+" + extension);
    }

    private static Instant profileCreatedDate(JsonNode profile) {
      String createdDate = profile.path("metadata").path("createdDate").asText("");
      return parseFolioInstantOrEpoch(createdDate, profile.path("id").asText(""));
    }

    private static Instant startedAtOrEpoch(JsonNode execution) {
      String startedDate = execution.path("startedDate").asText("");
      if (startedDate.isBlank()) {
        startedDate = execution.path("started_date").asText("");
      }
      return parseFolioInstantOrEpoch(startedDate, execution.path("id").asText(""));
    }

    private static Instant parseFolioInstantOrEpoch(String createdDate, String idForLogging) {
      if (createdDate.isBlank()) {
        return Instant.EPOCH;
      }
      try {
        return parseFolioInstant(createdDate);
      } catch (DateTimeParseException e) {
        LOGGER.warn("Ignoring unparsable FOLIO timestamp '{}' for {}", createdDate, idForLogging);
        return Instant.EPOCH;
      }
    }

    private static Instant parseFolioInstant(String value) {
      try {
        return Instant.parse(value);
      } catch (DateTimeParseException ignored) {
        // FOLIO responses commonly use +0000 offsets; normalize them to ISO's +00:00 form.
      }
      String normalized = value.replaceFirst("([+-]\\d{2})(\\d{2})$", "$1:$2");
      try {
        return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_DATE_TIME).toInstant();
      } catch (DateTimeParseException ignored) {
        // Some older metadata has no offset; treat it as UTC because FOLIO createdDate is UTC metadata.
      }
      return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME).toInstant(ZoneOffset.UTC);
    }

    static FoundationBucket detectFoundationBucket(Path file) throws IOException {
      boolean sawRecord = false;
      boolean sawHoldings = false;
      try (InputStream input = Files.newInputStream(file)) {
        MarcReader reader = new MarcStreamReader(input);
        while (reader.hasNext()) {
          sawRecord = true;
          Record record = reader.next();
          FoundationBucket recordBucket = bucketForRecord(record);
          if (recordBucket == FoundationBucket.ITEM) {
            return FoundationBucket.ITEM;
          }
          if (recordBucket == FoundationBucket.HOLDINGS) {
            sawHoldings = true;
          }
        }
      }
      if (!sawRecord) {
        throw new IOException("Foundation MARC file contains no records: " + file);
      }
      return sawHoldings ? FoundationBucket.HOLDINGS : FoundationBucket.INSTANCE;
    }

    static FoundationBucket bucketForRecord(Record record) {
      if (!record.getVariableFields("945").isEmpty()) {
        return FoundationBucket.ITEM;
      }
      if (!record.getVariableFields("852").isEmpty()) {
        return FoundationBucket.HOLDINGS;
      }
      return FoundationBucket.INSTANCE;
    }

    static Optional<String> findJobExecutionId(JsonNode node) {
      if (node == null || node.isMissingNode() || node.isNull()) {
        return Optional.empty();
      }
      if (node.isObject()) {
        for (String fieldName : List.of("jobExecutionId", "jobExecutionID", "id")) {
          JsonNode id = node.get(fieldName);
          if (id != null && id.isTextual() && !id.asText().isBlank()
            && (fieldName.startsWith("jobExecution") || hasJobExecutionShape(node))) {
            return Optional.of(id.asText());
          }
        }
        JsonNode executions = node.get("jobExecutions");
        if (executions != null && executions.isArray() && !executions.isEmpty()) {
          return findJobExecutionId(executions.get(0));
        }
        java.util.Iterator<JsonNode> values = node.elements();
        while (values.hasNext()) {
          Optional<String> nested = findJobExecutionId(values.next());
          if (nested.isPresent()) {
            return nested;
          }
        }
      } else if (node.isArray()) {
        for (JsonNode element : node) {
          Optional<String> nested = findJobExecutionId(element);
          if (nested.isPresent()) {
            return nested;
          }
        }
      }
      return Optional.empty();
    }

    private static boolean hasJobExecutionShape(JsonNode node) {
      return node.has("jobProfileInfo") || node.has("status") || node.has("uiStatus");
    }

    private static boolean isSuccess(JsonNode execution) {
      return SUCCESS_STATUSES.contains(execution.path("status").asText("").toUpperCase())
        || SUCCESS_STATUSES.contains(execution.path("uiStatus").asText("").toUpperCase());
    }

    private static boolean isFailure(JsonNode execution) {
      return FAILURE_STATUSES.contains(execution.path("status").asText("").toUpperCase())
        || FAILURE_STATUSES.contains(execution.path("uiStatus").asText("").toUpperCase())
        || FAILURE_STATUSES.contains(execution.path("errorStatus").asText("").toUpperCase());
    }

    private static String statusText(JsonNode execution) {
      return "status=" + execution.path("status").asText("")
        + ", uiStatus=" + execution.path("uiStatus").asText("")
        + ", errorStatus=" + execution.path("errorStatus").asText("");
    }

    enum FoundationBucket {
      INSTANCE("instance", "jp-900 "),
      HOLDINGS("holdings", "jp-901 "),
      ITEM("item", "jp-902 ");

      private final String label;
      private final String profilePrefix;

      FoundationBucket(String label, String profilePrefix) {
        this.label = label;
        this.profilePrefix = profilePrefix;
      }

      String label() {
        return label;
      }

      String profilePrefix() {
        return profilePrefix;
      }
    }
  }

  @Command(name = "import-marc",
    description = "Upload a MARC file with a selected job profile and wait for completion",
    mixinStandardHelpOptions = true)
  static class ImportMarcCommand implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(index = "0", description = "MARC file to upload")
    Path file;

    @Option(names = {"--profile-id"}, description = "Exact FOLIO job profile UUID")
    String profileId;

    @Option(names = {"--profile-prefix"},
      description = "Find the newest job profile whose name starts with this prefix, e.g. jp-063")
    String profilePrefix;

    @Option(names = {"--poll-interval-seconds"}, description = "Seconds between job status checks")
    int pollIntervalSeconds = 5;

    @Option(names = {"--timeout-seconds"}, description = "Maximum seconds to wait for the import")
    int timeoutSeconds = 300;

    @Override
    public Integer call() {
      if ((profileId == null || profileId.isBlank()) == (profilePrefix == null || profilePrefix.isBlank())) {
        LOGGER.error("Specify exactly one of --profile-id or --profile-prefix");
        return 1;
      }
      if (!Files.isRegularFile(file)) {
        LOGGER.error("MARC file does not exist: {}", file);
        return 1;
      }

      try {
        folioOptions.ensurePassword();
        FolioClient client = folioOptions.createFolioClient();

        Optional<JsonNode> profile = selectedProfile(client);
        if (profile.isEmpty()) {
          LOGGER.error("Could not find job profile for import");
          return 1;
        }
        return SeedFoundationCommand.submitMarcFile(client, file, profile.get(), pollIntervalSeconds, timeoutSeconds)
          ? 0 : 1;
      } catch (Exception e) {
        LOGGER.error("MARC import failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    private Optional<JsonNode> selectedProfile(FolioClient client) {
      if (profileId != null && !profileId.isBlank()) {
        return client.getJobProfiles(Map.of("query", "id==\"" + profileId + "\"")).findFirst();
      }
      return SeedFoundationCommand.latestProfile(client, profilePrefix);
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
    String recordType = "MARC_BIB";

    @Option(names = {"--skip-missing"}, description = "Skip records where instance is not found (default: fail)")
    boolean skipMissing = false;

    @Option(names = {"--record-number"}, description = "Only enrich this 1-based MARC record number; repeat for multiple records")
    List<Integer> recordNumbers = new ArrayList<>();

    enum EnrichType { INSTANCE_ID, INSTANCE_HRID, SOURCE_RECORD_ID }

    private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();
    private static final Set<String> SOURCE_RECORD_TYPES =
      Set.of("MARC_BIB", "MARC_AUTHORITY", "MARC_HOLDING", "EDIFACT");

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
        if (enrichType == EnrichType.SOURCE_RECORD_ID && !isValidSourceRecordType(recordType)) {
          LOGGER.error("Invalid source record type for SOURCE_RECORD_ID enrichment: {}. Expected one of {}",
            recordType, SOURCE_RECORD_TYPES);
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

            if (!shouldEnrichRecord(totalRecords)) {
              enrichedRecords.add(record);
              skippedCount++;
              continue;
            }

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
            addAuthorityIdForAuthoritySourceRecord(enrichedRecord, lookupRecord, enrichSpec);
            enrichedRecords.add(enrichedRecord);
            enrichedCount++;

            LOGGER.info("Enriched record {} ({} = '{}'): added {}${} = '{}'",
              totalRecords, matchField, lookupValue, enrichSpec.fieldTag, enrichSpec.subfieldCode, enrichValue);
          }
        }

        writeEnrichedRecordsAtomically(enrichedRecords, Paths.get(outputPath));

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

    private boolean shouldEnrichRecord(int recordNumber) {
      return recordNumbers == null || recordNumbers.isEmpty() || recordNumbers.contains(recordNumber);
    }

    private void writeEnrichedRecordsAtomically(List<Record> records, Path output) throws IOException {
      output = output.toAbsolutePath();
      Path parent = output.getParent();
      if (parent == null) {
        parent = Paths.get(".").toAbsolutePath();
      }
      Files.createDirectories(parent);
      Path temp = Files.createTempFile(parent, output.getFileName().toString(), ".tmp");
      try {
        try (OutputStream outputStream = Files.newOutputStream(temp)) {
          MarcStreamWriter writer = new MarcStreamWriter(outputStream, "UTF-8");
          try {
            for (Record record : records) {
              writer.write(record);
            }
          } finally {
            writer.close();
          }
        }
        try {
          Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(temp, output, StandardCopyOption.REPLACE_EXISTING);
        }
      } finally {
        Files.deleteIfExists(temp);
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
        case SOURCE_RECORD_ID -> instance.path("recordId").asText(null);
      };
    }

    /**
     * Enriches a MARC record by adding a field with the specified value.
     */
    private Record enrichRecord(Record original, String value, EnrichFieldSpec spec) {
      removeExistingEnrichmentField(original, spec);

      // Create a new data field with the enrichment value
      DataField dataField = MARC_FACTORY.newDataField(spec.fieldTag, spec.indicator1, spec.indicator2);
      dataField.addSubfield(MARC_FACTORY.newSubfield(spec.subfieldCode, value));

      // Add to the record (creates a copy if record is immutable)
      original.addVariableField(dataField);

      return original;
    }

    private void addAuthorityIdForAuthoritySourceRecord(Record record, JsonNode sourceRecord, EnrichFieldSpec spec) {
      if (enrichType != EnrichType.SOURCE_RECORD_ID
        || !"MARC_AUTHORITY".equals(recordType)
        || !"999".equals(spec.fieldTag)
        || spec.indicator1 != 'f'
        || spec.indicator2 != 'f'
        || spec.subfieldCode != 's') {
        return;
      }

      String authorityId = sourceRecord.path("externalIdsHolder").path("authorityId").asText(null);
      if (authorityId == null || authorityId.isBlank()) {
        return;
      }

      VariableField field = record.getVariableFields("999").stream()
        .filter(DataField.class::isInstance)
        .map(DataField.class::cast)
        .filter(dataField -> dataField.getIndicator1() == 'f' && dataField.getIndicator2() == 'f')
        .findFirst()
        .orElse(null);
      if (field instanceof DataField dataField && dataField.getSubfield('i') == null) {
        dataField.addSubfield(MARC_FACTORY.newSubfield('i', authorityId));
      }
    }

    private boolean isValidSourceRecordType(String candidate) {
      return SOURCE_RECORD_TYPES.contains(candidate);
    }

    private void removeExistingEnrichmentField(Record record, EnrichFieldSpec spec) {
      List<VariableField> existingFields = new ArrayList<>(record.getVariableFields(spec.fieldTag));
      for (VariableField field : existingFields) {
        if (field instanceof DataField dataField
          && dataField.getIndicator1() == spec.indicator1
          && dataField.getIndicator2() == spec.indicator2
          && dataField.getSubfield(spec.subfieldCode) != null) {
          record.removeVariableField(dataField);
        }
      }
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
