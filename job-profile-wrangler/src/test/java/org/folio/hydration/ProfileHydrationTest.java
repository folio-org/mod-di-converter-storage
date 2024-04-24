package org.folio.hydration;

import com.google.common.io.Resources;
import okhttp3.HttpUrl;
import org.folio.RepoObject;
import org.folio.graph.GraphReader;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.imports.RepoImport;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.jgrapht.Graph;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import static org.folio.Constants.REPO_PATH;
import static org.junit.Assert.assertTrue;

public class ProfileHydrationTest {

  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";

  private static Integer repoId;

  @BeforeClass
  public static void setup() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    Optional<RepoObject> repoObject = RepoImport.fromString(REPO_PATH, content);
    if (repoObject.isEmpty()) throw new RuntimeException("Could not create object in repo");
    repoId = repoObject.get().repoId();
  }


  @Test
  public void hydrate() {
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("http")
      .host("localhost")
      .port(9130);
    FolioClient client = new FolioClient(urlTemplate, TENANT_ID, USERNAME, PASSWORD);
    Graph<Profile, RegularEdge> g1 = GraphReader.read(REPO_PATH, repoId);

    ProfileHydration profileHydration = new ProfileHydration(client);
    var jobProfile = profileHydration.hydrate(repoId, g1);
    assertTrue(jobProfile.isPresent());
    assertTrue(jobProfile.get() instanceof JobProfileUpdateDto);
  }
}
