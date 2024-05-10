package org.folio.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.io.Resources;
import org.folio.RepoObject;
import org.folio.graph.GraphReader;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.jgrapht.Graph;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RepoImportTest {
  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  @Mock
  private FolioClient folioClient;

  @Test
  public void run() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    String jobProfilesContent = Resources.toString(Resources.getResource("job_profiles_response.json"), StandardCharsets.UTF_8);
    JsonNode jsonNode = OBJECT_MAPPER.readTree(jobProfilesContent);
    ArrayNode arrayNode = (ArrayNode) jsonNode.path("jobProfiles");
    when(folioClient.getJobProfiles()).thenReturn(StreamSupport.stream(arrayNode.spliterator(), false));
    String snapshot = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    JsonNode snapshotJson = OBJECT_MAPPER.readTree(snapshot);
    when(folioClient.getJobProfileSnapshot(any())).thenReturn(Optional.of(snapshotJson));

    RepoImport repoImport = new RepoImport(folioClient, repoPath);
    repoImport.run();

    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(repoPath);
    assertFalse(graphs.isEmpty());
  }

  @Test
  public void fromString() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    Optional<RepoObject> repoObject = RepoImport.fromString(repoPath, content);
    assertTrue(repoObject.isPresent());
  }
}
