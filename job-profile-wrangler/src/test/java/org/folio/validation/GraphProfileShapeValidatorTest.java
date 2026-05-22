package org.folio.validation;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GraphProfileShapeValidatorTest {

  @Test
  public void validateAllowsLeafDeleteActionWithoutMapping() {
    Graph<Profile, RegularEdge> graph = graphWithLeafAction(ActionProfile.Action.DELETE,
      ActionProfile.FolioRecord.MARC_AUTHORITY);

    var result = new GraphProfileShapeValidator().validate(graph);

    assertTrue(result.isEmpty());
  }

  @Test
  public void validateRejectsLeafCreateActionWithoutMapping() {
    Graph<Profile, RegularEdge> graph = graphWithLeafAction(ActionProfile.Action.CREATE,
      ActionProfile.FolioRecord.INSTANCE);

    var result = new GraphProfileShapeValidator().validate(graph);

    assertTrue(result.isPresent());
    assertEquals(GraphProfileShapeValidator.ACTION_MAPPING_MISSING_RULE, result.get().rule());
  }

  private Graph<Profile, RegularEdge> graphWithLeafAction(ActionProfile.Action action,
                                                          ActionProfile.FolioRecord folioRecord) {
    Graph<Profile, RegularEdge> graph = new DefaultDirectedGraph<>(RegularEdge.class);
    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile actionProfile = new ActionProfileNode("2", action.value(), folioRecord.value(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(actionProfile);
    graph.addEdge(jobProfile, actionProfile, new RegularEdge());
    return graph;
  }
}
