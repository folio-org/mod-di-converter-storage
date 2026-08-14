package org.folio.imports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.Constants.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.folio.RepoObject;
import org.folio.graph.GraphReader;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.jgrapht.Graph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepoImportTest {

  @TempDir
  Path tempDir;

  @Mock
  private FolioClient folioClient;

  @DisplayName("should write graphs to repo when running import with valid job profiles")
  @Test
  void shouldWriteGraphsToRepo_whenRunningImportWithValidJobProfiles() throws IOException {
    // arrange
    String jobProfilesContent =
      Resources.toString(Resources.getResource("job_profiles_response.json"), StandardCharsets.UTF_8);
    JsonNode jsonNode = OBJECT_MAPPER.readTree(jobProfilesContent);
    ArrayNode arrayNode = (ArrayNode) jsonNode.path("jobProfiles");
    when(folioClient.getJobProfiles()).thenReturn(StreamSupport.stream(arrayNode.spliterator(), false));

    String snapshot = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    JsonNode snapshotJson = OBJECT_MAPPER.readTree(snapshot);
    when(folioClient.getJobProfileSnapshot(any())).thenReturn(Optional.of(snapshotJson));

    // act
    new RepoImport(folioClient, tempDir.toString()).run();

    // assert
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(tempDir.toString());
    assertThat(graphs).isNotEmpty();
  }

  @DisplayName("should return repo object when creating from valid snapshot string")
  @Test
  void shouldReturnRepoObject_whenCreatingFromValidSnapshotString() throws IOException {
    // arrange
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);

    // act
    Optional<RepoObject> repoObject = RepoImport.fromString(tempDir.toString(), content);

    // assert
    assertThat(repoObject).isPresent();
  }
}
