package org.folio;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.Profile;
import org.folio.imports.GraphReader;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class Main {
  private final static Logger LOGGER = LogManager.getLogger();
  final static OkHttpClient client = new OkHttpClient();

  final static String TENANT_ID = "diku";
  final static String USERNAME = "diku_admin";
  final static String PASSWORD = "admin";

  public static void main(String[] args) {
    ExecutorService executor = Executors.newFixedThreadPool(5);
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");

    String repoPath = "/Users/okolawole/git/folio/mod-di-converter-storage/" +
      "job-profile-wrangler/src/main/resources/repository";

    Optional<String> token = Utilities.getOkapiToken(client,  urlTemplate, TENANT_ID, USERNAME, PASSWORD);
    token.ifPresent(tokenValue -> {
      RepoImport repoImport = new RepoImport(client, urlTemplate, tokenValue, executor, repoPath);
      try {
//        repoImport.run();

        Graph<Profile, DefaultEdge> read = GraphReader.read(repoPath, 4);
        LOGGER.info(read);
      } finally {
        executor.shutdownNow();
      }
    });
  }

}
