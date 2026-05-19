package org.folio.graph;

import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GraphReaderEquivalenceTest {
  @Test
  public void graphIsEquivalentToItself() {
    Graph<Profile, RegularEdge> graph = createInstanceGraph("CREATE", "INSTANCE", 0);

    assertTrue(GraphReader.isShapeEquivalent(graph, graph));
  }

  @Test
  public void profileNamesAndIdsDoNotAffectShape() {
    Graph<Profile, RegularEdge> left = createInstanceGraph("CREATE", "INSTANCE", 0);
    Graph<Profile, RegularEdge> right = createInstanceGraph("CREATE", "INSTANCE", 0);

    assertTrue(GraphReader.isShapeEquivalent(left, right));
  }

  @Test
  public void edgeClassDifferenceIsNotEquivalent() {
    Graph<Profile, RegularEdge> match = matchBranchGraph(new MatchRelationshipEdge());
    Graph<Profile, RegularEdge> nonMatch = matchBranchGraph(new NonMatchRelationshipEdge());

    assertFalse(GraphReader.isShapeEquivalent(match, nonMatch));
  }

  @Test
  public void orderDifferenceIsNotEquivalent() {
    Graph<Profile, RegularEdge> left = createInstanceGraph("CREATE", "INSTANCE", 0);
    Graph<Profile, RegularEdge> right = createInstanceGraph("CREATE", "INSTANCE", 1);

    assertFalse(GraphReader.isShapeEquivalent(left, right));
  }

  @Test
  public void incomingRecordTypeDifferenceIsNotEquivalent() {
    Graph<Profile, RegularEdge> bib = matchGraph("MARC_BIBLIOGRAPHIC");
    Graph<Profile, RegularEdge> authority = matchGraph("MARC_AUTHORITY");

    assertFalse(GraphReader.isShapeEquivalent(bib, authority));
  }

  @Test
  public void vertexCountDifferenceIsNotEquivalent() {
    Graph<Profile, RegularEdge> left = createInstanceGraph("CREATE", "INSTANCE", 0);
    Graph<Profile, RegularEdge> right = createInstanceGraph("CREATE", "INSTANCE", 0);
    right.addVertex(new ActionProfileNode("extra", "CREATE", "ITEM", 1));

    assertFalse(GraphReader.isShapeEquivalent(left, right));
  }

  @Test
  public void edgeCountDifferenceIsNotEquivalent() {
    Graph<Profile, RegularEdge> left = createInstanceGraph("CREATE", "INSTANCE", 0);
    Graph<Profile, RegularEdge> right = createInstanceGraph("CREATE", "INSTANCE", 0);
    Profile job = right.vertexSet().stream().filter(JobProfileNode.class::isInstance).findFirst().orElseThrow();
    Profile mapping = right.vertexSet().stream().filter(MappingProfileNode.class::isInstance).findFirst().orElseThrow();
    right.addEdge(job, mapping, new RegularEdge());

    assertFalse(GraphReader.isShapeEquivalent(left, right));
  }

  @Test
  public void repositoryShapesSelfEqualAndPairwiseDistinct() throws Exception {
    Path repo = Path.of("src/main/resources/repository");
    List<Graph<Profile, RegularEdge>> graphs;
    try (var files = Files.list(repo)) {
      graphs = files
        .filter(path -> path.getFileName().toString().endsWith(".dot"))
        .sorted()
        .map(path -> GraphReader.read(repo.toString(), repoId(path)))
        .toList();
    }

    for (int i = 0; i < graphs.size(); i++) {
      assertTrue(GraphReader.isShapeEquivalent(graphs.get(i), graphs.get(i)));
      for (int j = i + 1; j < graphs.size(); j++) {
        assertFalse(GraphReader.isShapeEquivalent(graphs.get(i), graphs.get(j)));
      }
    }
  }

  private Graph<Profile, RegularEdge> createInstanceGraph(String action, String folioRecord, int order) {
    Graph<Profile, RegularEdge> graph = new SimpleDirectedGraph<>(RegularEdge.class);
    JobProfileNode job = new JobProfileNode("job-" + order, "MARC", 0);
    ActionProfileNode actionProfile = new ActionProfileNode("action-" + order, action, folioRecord, order);
    MappingProfileNode mapping = new MappingProfileNode("mapping-" + order, "MARC_BIBLIOGRAPHIC", folioRecord, 0);
    graph.addVertex(job);
    graph.addVertex(actionProfile);
    graph.addVertex(mapping);
    graph.addEdge(job, actionProfile, new RegularEdge());
    graph.addEdge(actionProfile, mapping, new RegularEdge());
    return graph;
  }

  private Graph<Profile, RegularEdge> matchBranchGraph(RegularEdge branchEdge) {
    Graph<Profile, RegularEdge> graph = new SimpleDirectedGraph<>(RegularEdge.class);
    JobProfileNode job = new JobProfileNode("job", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    ActionProfileNode action = new ActionProfileNode("action", "UPDATE", "INSTANCE", 0);
    graph.addVertex(job);
    graph.addVertex(match);
    graph.addVertex(action);
    graph.addEdge(job, match, new RegularEdge());
    graph.addEdge(match, action, branchEdge);
    return graph;
  }

  private Graph<Profile, RegularEdge> matchGraph(String incomingRecordType) {
    Graph<Profile, RegularEdge> graph = new SimpleDirectedGraph<>(RegularEdge.class);
    JobProfileNode job = new JobProfileNode("job", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match", incomingRecordType, "INSTANCE", 0);
    graph.addVertex(job);
    graph.addVertex(match);
    graph.addEdge(job, match, new RegularEdge());
    return graph;
  }

  private int repoId(Path path) {
    String name = path.getFileName().toString();
    return Integer.parseInt(name.substring(3, name.length() - 4));
  }
}
