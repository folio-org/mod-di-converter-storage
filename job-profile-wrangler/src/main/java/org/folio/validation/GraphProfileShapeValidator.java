package org.folio.validation;

import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.validation.rules.MatchModifyMarcBibRule;
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
  private static final String MATCH_MODIFY_MARC_BIB_MESSAGE = "MATCH followed directly by MODIFY MARC_BIBLIOGRAPHIC "
    + "is rejected by mod-di-converter-storage when creating the job profile.";

  public Optional<BlockedUnsupportedWorkflow> validate(Graph<Profile, RegularEdge> graph) {
    Objects.requireNonNull(graph, "graph");

    Optional<BlockedUnsupportedWorkflow> actionWithoutMapping = graph.vertexSet().stream()
      .filter(ActionProfileNode.class::isInstance)
      .map(ActionProfileNode.class::cast)
      .filter(this::requiresMappingChild)
      .filter(action -> !hasMappingChild(graph, action))
      .findFirst()
      .map(action -> new BlockedUnsupportedWorkflow(ACTION_MAPPING_MISSING_RULE,
        ACTION_MAPPING_MISSING_MESSAGE.formatted(action.id(), action.action(), action.folioRecord())));
    if (actionWithoutMapping.isPresent()) {
      return actionWithoutMapping;
    }

    return graph.vertexSet().stream()
      .filter(MatchProfileNode.class::isInstance)
      .filter(match -> hasDirectModifyMarcBibChild(graph, match))
      .findFirst()
      .map(match -> new BlockedUnsupportedWorkflow(MatchModifyMarcBibRule.RULE_NAME,
        MATCH_MODIFY_MARC_BIB_MESSAGE));
  }

  private boolean hasMappingChild(Graph<Profile, RegularEdge> graph, ActionProfileNode action) {
    return graph.outgoingEdgesOf(action).stream()
      .map(graph::getEdgeTarget)
      .anyMatch(MappingProfileNode.class::isInstance);
  }

  private boolean requiresMappingChild(ActionProfileNode action) {
    return !"DELETE".equals(action.action());
  }

  private boolean hasDirectModifyMarcBibChild(Graph<Profile, RegularEdge> graph, Profile match) {
    return graph.outgoingEdgesOf(match).stream()
      .map(graph::getEdgeTarget)
      .filter(ActionProfileNode.class::isInstance)
      .map(ActionProfileNode.class::cast)
      .anyMatch(action -> "MODIFY".equals(action.action())
        && "MARC_BIBLIOGRAPHIC".equals(action.folioRecord()));
  }
}
