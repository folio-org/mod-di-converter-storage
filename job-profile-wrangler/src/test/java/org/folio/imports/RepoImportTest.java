package org.folio.imports;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.folio.Utilities;
import org.junit.Test;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class RepoImportTest {

  static final OkHttpClient client = new OkHttpClient();
  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";

  @Test
  public void run() {
    ExecutorService executor = Executors.newFixedThreadPool(5);
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");

    String repoPath = "/Users/okolawole/git/folio/mod-di-converter-storage/" +
      "job-profile-wrangler/src/main/resources/repository";

    Optional<String> token = Utilities.getOkapiToken(client, urlTemplate, TENANT_ID, USERNAME, PASSWORD);
    token.ifPresent(tokenValue -> {
      RepoImport repoImport = new RepoImport(client, urlTemplate, tokenValue, executor, repoPath);
      try {
        repoImport.run();
      } finally {
        executor.shutdownNow();
      }
    });
  }
}
