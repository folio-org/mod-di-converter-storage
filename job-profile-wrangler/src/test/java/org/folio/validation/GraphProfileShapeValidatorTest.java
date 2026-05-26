package org.folio.validation;

import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.EntityType;
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

  @Test
  public void validateRejectsMatchInstanceCreateItemWithoutHoldingsContext() {
    Graph<Profile, RegularEdge> graph = new DefaultDirectedGraph<>(RegularEdge.class);
    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.value(),
      EntityType.INSTANCE.value(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.CREATE.value(),
      ActionProfile.FolioRecord.ITEM.value(), 0);
    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.value(),
      EntityType.ITEM.value(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());

    var result = new GraphProfileShapeValidator().validate(graph);

    assertTrue(result.isPresent());
    assertEquals("match-instance-create-item-without-holdings", result.get().rule());
  }

  @Test
  public void validateAllowsMatchInstanceCreateItemAfterHoldingsContext() {
    Graph<Profile, RegularEdge> graph = new DefaultDirectedGraph<>(RegularEdge.class);
    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.value(),
      EntityType.INSTANCE.value(), 0);
    Profile holdingsAction = new ActionProfileNode("3", ActionProfile.Action.CREATE.value(),
      ActionProfile.FolioRecord.HOLDINGS.value(), 0);
    Profile holdingsMapping = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.value(),
      EntityType.HOLDINGS.value(), 0);
    Profile itemAction = new ActionProfileNode("5", ActionProfile.Action.CREATE.value(),
      ActionProfile.FolioRecord.ITEM.value(), 1);
    Profile itemMapping = new MappingProfileNode("6", EntityType.MARC_BIBLIOGRAPHIC.value(),
      EntityType.ITEM.value(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(holdingsAction);
    graph.addVertex(holdingsMapping);
    graph.addVertex(itemAction);
    graph.addVertex(itemMapping);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, holdingsAction, new MatchRelationshipEdge());
    graph.addEdge(holdingsAction, holdingsMapping, new RegularEdge());
    graph.addEdge(matchProfile, itemAction, new MatchRelationshipEdge());
    graph.addEdge(itemAction, itemMapping, new RegularEdge());

    var result = new GraphProfileShapeValidator().validate(graph);

    assertTrue(result.isEmpty());
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
