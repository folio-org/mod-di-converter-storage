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
import static org.junit.Assert.assertEquals;
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
    ImportReport report = repoImport.importProfiles();

    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(repoPath);
    assertFalse(graphs.isEmpty());
    assertEquals(1, report.addedCount());
    assertEquals(2, report.duplicateCount());
  }

  @Test
  public void fromString() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    Optional<RepoObject> repoObject = RepoImport.fromString(repoPath, content);
    assertTrue(repoObject.isPresent());
  }

  @Test
  public void importSnapshotReportsDuplicate() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    JsonNode snapshot = OBJECT_MAPPER.readTree(content);

    ImportReport.Entry first = RepoImport.importSnapshot(repoPath, "profile-1", "Profile 1", snapshot);
    ImportReport.Entry second = RepoImport.importSnapshot(repoPath, "profile-2", "Profile 2", snapshot);

    assertTrue(first.outcome() instanceof ImportOutcome.Added);
    assertTrue(second.outcome() instanceof ImportOutcome.Duplicate);
    assertEquals(
      ((ImportOutcome.Added) first.outcome()).repoId(),
      ((ImportOutcome.Duplicate) second.outcome()).existingRepoId()
    );
  }

  @Test
  public void importSnapshotReportsBlockedUnsupportedShape() throws IOException {
    String repoPath = tempDir.getRoot().toString();
    JsonNode snapshot = OBJECT_MAPPER.readTree(getClass().getClassLoader()
      .getResourceAsStream("profile-shape-validator/match-instance-create-item.json"));

    ImportReport.Entry entry = RepoImport.importSnapshot(repoPath, "profile-1", "Blocked profile", snapshot);

    assertTrue(entry.outcome() instanceof ImportOutcome.BlockedUnsupported);
    assertEquals("match-instance-create-item-without-holdings",
      ((ImportOutcome.BlockedUnsupported) entry.outcome()).rule());
    assertTrue(GraphReader.readAll(repoPath).isEmpty());
  }

  @Test
  public void importReportSerializesOutcomeDiscriminators() throws Exception {
    ImportReport report = new ImportReport("2026-05-19T20:00:00Z", List.of(
      new ImportReport.Entry("profile-1", "Profile 1", new ImportOutcome.Added(1)),
      new ImportReport.Entry("profile-2", "Profile 2", new ImportOutcome.Duplicate(1)),
      new ImportReport.Entry("profile-3", "Profile 3", new ImportOutcome.ImportError("boom"))
    ));

    JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(report));

    assertEquals("added", json.get("profiles").get(0).get("outcome").get("type").asText());
    assertEquals("duplicate", json.get("profiles").get(1).get("outcome").get("type").asText());
    assertEquals("import-error", json.get("profiles").get(2).get("outcome").get("type").asText());
  }
}
