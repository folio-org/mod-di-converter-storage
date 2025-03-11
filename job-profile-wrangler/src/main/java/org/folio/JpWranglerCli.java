package org.folio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Callable;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.exports.MarcTestDataGenerator;
import org.folio.graph.GraphReader;
import org.folio.graph.GraphWriter;
import org.folio.graph.GraphWriterEnhanced;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;

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
    JpWranglerCli.GenerateCommand.class
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

    @Option(names = {"--password"}, description = "FOLIO password", interactive = true, showDefaultValue = Visibility.NEVER)
    String password;

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
          hydration.hydrate(repoId, graph);
          LOGGER.info("Exported job profile {}", repoId);
        }

        return 0;
      } catch (Exception e) {
        LOGGER.error("Export failed: {}", e.getMessage(), e);
        return 1;
      }
    }

    private int getRepoIdFromGraph(Graph<Profile, RegularEdge> graph) {
      // TODO: Extract repo ID from graph if needed
      // This is a simplified approach
      return 1;
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
  static class GenerateCommand implements Callable<Integer> {
    @CommandLine.Mixin
    private FolioConnectionOptions folioOptions = new FolioConnectionOptions();

    @Parameters(index = "0", description = "FOLIO job profile UUID to use for test record generation")
    String jobProfileId;

    @Option(names = {"-o", "--output"}, description = "Output MARC file path", required = true)
    String outputPath;

    @Override
    public Integer call() {
      try {
        // Get token either from options or by authenticating
        String token = folioOptions.getToken();
        if (token == null) {
          LOGGER.error("Authentication failed: could not obtain token");
          return 1;
        }

        // Connect to FOLIO
        FolioClient client = new FolioClient(
          () -> HttpUrl.parse(folioOptions.baseUrl).newBuilder(),
          token
        );

        // Get the job profile snapshot directly using the provided UUID
        Optional<JsonNode> snapshot = client.getJobProfileSnapshot(jobProfileId);

        if (snapshot.isEmpty()) {
          LOGGER.error("Failed to retrieve job profile snapshot for UUID: {}", jobProfileId);
          return 1;
        }

        LOGGER.info("Successfully retrieved job profile snapshot for UUID: {}", jobProfileId);

        // Generate test MARC records using FOLIO as the source
        MarcTestDataGenerator generator = new MarcTestDataGenerator(folioOptions.baseUrl, token);
        try {
          generator.generateTestData(snapshot.get(), outputPath);
          LOGGER.info("Generated test MARC records to {}", outputPath);
        } finally {
          try {
            generator.close();
          } catch (IOException e) {
            LOGGER.warn("Error closing generator: {}", e.getMessage());
          }
        }

        return 0;
      } catch (Exception e) {
        LOGGER.error("Test data generation failed: {}", e.getMessage(), e);
        return 1;
      }
    }
  }
}
