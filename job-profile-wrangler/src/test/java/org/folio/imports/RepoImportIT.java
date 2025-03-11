package org.folio.imports;

import com.google.common.io.Resources;
import okhttp3.HttpUrl;
import org.folio.graph.GraphReader;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.jgrapht.Graph;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.assertFalse;

public class RepoImportIT {

  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Test
  public void run() {
    String repoPath = tempDir.getRoot().toString();
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");

    FolioClient client = new FolioClient(urlTemplate, TENANT_ID, USERNAME, PASSWORD);
    RepoImport repoImport = new RepoImport(client, repoPath);
    repoImport.run();

    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(repoPath);
    assertFalse(graphs.isEmpty());
  }

  @Test
  public void fromString() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    RepoImport.fromString(repoPath, content);
  }
}
