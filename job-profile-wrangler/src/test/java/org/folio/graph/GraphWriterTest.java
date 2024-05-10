package org.folio.graph;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GraphWriterTest {

  @Rule
  public TemporaryFolder tempDir = new TemporaryFolder();

  private Graph<Profile, RegularEdge> graph;

  @Before
  public void setUp() throws IOException {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile profile1 = new JobProfileNode("1", "MARC", 0);
    Profile profile2 = new ActionProfileNode("2", "CREATE", "BIB", 0);

    graph.addVertex(profile1);
    graph.addVertex(profile2);
    graph.addEdge(profile1, profile2, new RegularEdge());

    // Create an invalid file in the temporary directory
    tempDir.newFile("invalid.txt");
  }

  @Test
  public void testWriteGraph() {
    String repoPath = tempDir.getRoot().toString();
    Optional<Integer> fileId = GraphWriter.writeGraph(repoPath, graph);

    assertTrue(fileId.isPresent());

    File dotFile = new File(repoPath, GraphWriter.genGraphFileName(fileId.get()));
    assertTrue(dotFile.exists());
  }

  @Test
  public void testRenderGraph() {
    String fileName = tempDir.getRoot().toString() + "/graph";
    Optional<File> svgFile = GraphWriter.renderGraph(fileName, graph);

    assertTrue(svgFile.isPresent());
    assertTrue(svgFile.get().exists());
    assertEquals("graph.svg", svgFile.get().getName());
  }

  @Test
  public void testWriteGraphMultipleTimes() {
    String repoPath = tempDir.getRoot().toString();

    Optional<Integer> fileId1 = GraphWriter.writeGraph(repoPath, graph);
    Optional<Integer> fileId2 = GraphWriter.writeGraph(repoPath, graph);
    Optional<Integer> fileId3 = GraphWriter.writeGraph(repoPath, graph);

    assertTrue(fileId1.isPresent());
    assertTrue(fileId2.isPresent());
    assertTrue(fileId3.isPresent());

    File dotFile1 = new File(repoPath, GraphWriter.genGraphFileName(fileId1.get()));
    File dotFile2 = new File(repoPath, GraphWriter.genGraphFileName(fileId2.get()));
    File dotFile3 = new File(repoPath, GraphWriter.genGraphFileName(fileId3.get()));

    assertTrue(dotFile1.exists());
    assertTrue(dotFile2.exists());
    assertTrue(dotFile3.exists());
  }
}
