package org.folio.validation;

import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;

import java.util.Objects;
import java.util.Optional;

/**
 * Validates repository DOT graphs before they are imported or hydrated into FOLIO.
 */
public class GraphProfileShapeValidator {
  public static final String ACTION_MAPPING_MISSING_RULE = "action-profile-mapping-missing";

  private static final String ACTION_MAPPING_MISSING_MESSAGE = "ACTION profile '%s' (%s %s) has no MAPPING profile "
    + "child; FOLIO cannot associate a bare action profile in a job profile because no profile wrapper is created.";

  public Optional<BlockedUnsupportedWorkflow> validate(Graph<Profile, RegularEdge> graph) {
    Objects.requireNonNull(graph, "graph");

    return graph.vertexSet().stream()
      .filter(ActionProfileNode.class::isInstance)
      .map(ActionProfileNode.class::cast)
      .filter(action -> !hasMappingChild(graph, action))
      .findFirst()
      .map(action -> new BlockedUnsupportedWorkflow(ACTION_MAPPING_MISSING_RULE,
        ACTION_MAPPING_MISSING_MESSAGE.formatted(action.id(), action.action(), action.folioRecord())));
  }

  private boolean hasMappingChild(Graph<Profile, RegularEdge> graph, ActionProfileNode action) {
    return graph.outgoingEdgesOf(action).stream()
      .map(graph::getEdgeTarget)
      .anyMatch(MappingProfileNode.class::isInstance);
  }
}
