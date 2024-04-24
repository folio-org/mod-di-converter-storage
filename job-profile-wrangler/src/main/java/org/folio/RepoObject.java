package org.folio;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;

public record RepoObject(int repoId, Graph<Profile, RegularEdge> graph) {
}
