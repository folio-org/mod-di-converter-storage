package org.folio.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GraphWriterTest {

  @TempDir
  Path tempDir;

  private Graph<Profile, RegularEdge> graph;

  @BeforeEach
  void setUp() throws IOException {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile profile1 = new JobProfileNode("1", "MARC", 0);
    Profile profile2 = new ActionProfileNode("2", "CREATE", "BIB", 0);

    graph.addVertex(profile1);
    graph.addVertex(profile2);
    graph.addEdge(profile1, profile2, new RegularEdge());

    Files.createFile(tempDir.resolve("invalid.txt"));
  }

  @DisplayName("should write graph file when graph is valid")
  @Test
  void shouldWriteGraphFile_whenGraphIsValid() {
    // act
    Optional<Integer> fileId = GraphWriter.writeGraph(tempDir.toString(), graph);

    // assert
    assertThat(fileId).isPresent();
    assertThat(new File(tempDir.toString(), GraphWriter.genGraphFileName(fileId.get()))).exists();
  }

  @DisplayName("should produce svg file when rendering a graph")
  @Test
  void shouldProduceSvgFile_whenRenderingGraph() {
    // arrange
    String fileName = tempDir + "/graph";

    // act
    Optional<File> svgFile = GraphWriter.renderGraph(fileName, graph);

    // assert
    assertThat(svgFile).isPresent();
    assertThat(svgFile.get()).exists().hasName("graph.svg");
  }

  @DisplayName("should generate distinct file ids when writing the same graph multiple times")
  @Test
  void shouldGenerateDistinctFileIds_whenWritingSameGraphMultipleTimes() {
    // act
    Optional<Integer> fileId1 = GraphWriter.writeGraph(tempDir.toString(), graph);
    Optional<Integer> fileId2 = GraphWriter.writeGraph(tempDir.toString(), graph);
    Optional<Integer> fileId3 = GraphWriter.writeGraph(tempDir.toString(), graph);

    // assert
    assertThat(fileId1).isPresent();
    assertThat(fileId2).isPresent();
    assertThat(fileId3).isPresent();
    assertThat(new File(tempDir.toString(), GraphWriter.genGraphFileName(fileId1.get()))).exists();
    assertThat(new File(tempDir.toString(), GraphWriter.genGraphFileName(fileId2.get()))).exists();
    assertThat(new File(tempDir.toString(), GraphWriter.genGraphFileName(fileId3.get()))).exists();
  }
}
