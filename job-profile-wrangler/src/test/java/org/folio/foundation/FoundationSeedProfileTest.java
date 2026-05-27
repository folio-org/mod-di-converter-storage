package org.folio.foundation;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class FoundationSeedProfileTest {
  @Test
  public void builtInSeedProfileGraphsCanBeLoaded() throws Exception {
    for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
      Graph<Profile, RegularEdge> graph = seedProfile.graph();

      assertFalse(graph.vertexSet().isEmpty());
      assertFalse(graph.edgeSet().isEmpty());
    }
  }
}
