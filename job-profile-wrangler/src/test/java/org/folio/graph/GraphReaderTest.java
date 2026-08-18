package org.folio.graph;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.folio.RepoObject;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphReaderTest {

  @TempDir
  static Path tempDir;

  private static Integer repoId;

  @BeforeAll
  static void setup() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    Optional<RepoObject> repoObject = RepoImport.fromString(tempDir.toString(), content);
    if (repoObject.isEmpty()) {
      throw new RuntimeException("Could not create object in repo");
    }
    repoId = repoObject.get().repoId();
  }

  @DisplayName("should return equal graphs when reading the same id twice")
  @Test
  void shouldReturnEqualGraphs_whenReadingSameIdTwice() {
    // arrange / act
    Graph<Profile, RegularEdge> g1 = GraphReader.read(tempDir.toString(), repoId);
    Graph<Profile, RegularEdge> g2 = GraphReader.read(tempDir.toString(), repoId);

    // assert
    assertThat(g1).isNotNull().isEqualTo(g2);
  }

  @DisplayName("should return non-empty list when reading all graphs")
  @Test
  void shouldReturnNonEmptyList_whenReadingAllGraphs() {
    // act
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(tempDir.toString());

    // assert
    assertThat(graphs).isNotNull().isNotEmpty();
  }

  @DisplayName("should find graph when searching for an existing graph")
  @Test
  void shouldFindGraph_whenSearchingForExistingGraph() {
    // arrange
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(tempDir.toString());
    assertThat(graphs).isNotEmpty();

    // act / assert
    assertThat(GraphReader.search(tempDir.toString(), graphs.getFirst())).isPresent();
  }
}
