package org.folio.graph;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class GraphReaderTest {

  public static final String REPO_PATH = "/Users/okolawole/git/folio/mod-di-converter-storage/" +
    "job-profile-wrangler/src/main/resources/repository";

  @Test
  public void readGraph() {
    Graph<Profile, RegularEdge> g1 = GraphReader.read(REPO_PATH, 7);
    Graph<Profile, RegularEdge> g2 = GraphReader.read(REPO_PATH, 7);
    assertEquals(g1, g2);
    assertNotNull(g1);
    GraphWriter.renderGraph("output", g1);
  }

  @Test
  public void readAllGraph() {
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(REPO_PATH);
    assertNotNull(graphs);
    GraphWriter.renderGraph("output", graphs.get(0));
  }

  @Test
  public void isGraphPresent() {
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(REPO_PATH);
    assertNotNull(graphs);
    assertTrue(GraphReader.isGraphPresent(REPO_PATH, graphs.get(0)));
  }

}
