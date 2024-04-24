package org.folio.graph;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.traverse.DepthFirstIterator;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ProfileDepthFirstIterator extends DepthFirstIterator<Profile, RegularEdge> {

  private static final Comparator<Integer> intComparator = Comparator.comparingInt(Integer::intValue);
  private static final Comparator<RegularEdge> edgeComparator = (edge1, edge2) -> {
    Profile source1 = (Profile) edge1.getSource();
    Profile source2 = (Profile) edge2.getSource();
    // Compare the source vertices of the edges lexicographically
    return intComparator.compare(source1.getOrder(), source2.getOrder());
  };

  public ProfileDepthFirstIterator(Graph<Profile, RegularEdge> g) {
    super(g);
  }

  public ProfileDepthFirstIterator(Graph<Profile, RegularEdge> g, Profile startVertex) {
    super(g, startVertex);
  }

  public ProfileDepthFirstIterator(Graph<Profile, RegularEdge> g, Iterable<Profile> startVertices) {
    super(g, startVertices);
  }

  /**
   * Selects the outgoing edges for the given vertex based on the "order" attribute of the target profile.
   *
   * @param vertex the vertex for which to select outgoing edges
   * @return a sorted set of outgoing edges, ordered by the "order" attribute of the target profiles
   */
  @Override
  protected Set<RegularEdge> selectOutgoingEdges(Profile vertex) {
    Set<RegularEdge> regularEdges = super.selectOutgoingEdges(vertex);
    return regularEdges.stream()
      .sorted(edgeComparator)
      .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
