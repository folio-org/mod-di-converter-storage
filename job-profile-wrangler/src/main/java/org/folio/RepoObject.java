package org.folio;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;

/**
 * Holder class for objects in the repository to link a repository identifier to a graph
 */
public record RepoObject(int repoId, Graph<Profile, RegularEdge> graph) {
}
