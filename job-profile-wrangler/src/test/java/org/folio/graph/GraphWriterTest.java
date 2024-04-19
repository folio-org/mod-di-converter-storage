package org.folio.graph;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class GraphWriterTest {

  public static final String REPO_PATH = "/Users/okolawole/git/folio/mod-di-converter-storage/" +
    "job-profile-wrangler/src/main/resources/repository";

  @Test
  public void renderGraph() {
    Graph<Profile, RegularEdge> g1 = GraphReader.read(REPO_PATH, 19);
    assertNotNull(g1);
    GraphWriter.renderGraph("output", g1);
  }
}
