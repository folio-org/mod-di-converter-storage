package org.folio.imports;

import com.google.common.io.Resources;
import okhttp3.HttpUrl;
import org.folio.http.FolioClient;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.folio.Constants.REPO_PATH;

public class RepoImportTest {

  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";

  @Test
  public void run() {
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");

    FolioClient client = new FolioClient(urlTemplate, TENANT_ID, USERNAME, PASSWORD);
    RepoImport repoImport = new RepoImport(client, REPO_PATH);
    repoImport.run();
  }

  @Test
  public void fromString() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    RepoImport.fromString(REPO_PATH, content);
  }
}
