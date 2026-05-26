package org.folio.validation;

import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.validation.rules.MatchInstanceCreateItemRule;
import org.folio.validation.rules.MatchModifyMarcBibRule;
import org.jgrapht.Graph;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates repository DOT graphs before they are imported or hydrated into FOLIO.
 */
public class GraphProfileShapeValidator {
  public static final String ACTION_MAPPING_MISSING_RULE = "action-profile-mapping-missing";

  private static final String ACTION_MAPPING_MISSING_MESSAGE = "ACTION profile '%s' (%s %s) has no MAPPING profile "
    + "child; FOLIO cannot associate a bare action profile in a job profile because no profile wrapper is created.";
  private static final String MATCH_INSTANCE_CREATE_ITEM_MESSAGE = "MATCH INSTANCE followed by CREATE ITEM on a "
    + "MATCH branch requires CREATE HOLDINGS or MATCH HOLDINGS earlier in the same branch.";
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

    Optional<BlockedUnsupportedWorkflow> itemWithoutHoldings = graph.vertexSet().stream()
      .filter(vertex -> graph.incomingEdgesOf(vertex).isEmpty())
      .filter(root -> matchesItemWithoutHoldings(graph, root, false, false, false))
      .findFirst()
      .map(root -> new BlockedUnsupportedWorkflow(MatchInstanceCreateItemRule.RULE_NAME,
        MATCH_INSTANCE_CREATE_ITEM_MESSAGE));
    if (itemWithoutHoldings.isPresent()) {
      return itemWithoutHoldings;
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

  private boolean matchesItemWithoutHoldings(
      Graph<Profile, RegularEdge> graph,
      Profile node,
      boolean afterMatchInstance,
      boolean onMatchBranch,
      boolean hasHoldingsContext) {
    boolean nextAfterMatchInstance = afterMatchInstance;
    boolean nextHasHoldingsContext = hasHoldingsContext;

    if (node instanceof MatchProfileNode match) {
      if ("INSTANCE".equals(match.existingRecordType())) {
        nextAfterMatchInstance = true;
        nextHasHoldingsContext = false;
      } else if (nextAfterMatchInstance && "HOLDINGS".equals(match.existingRecordType())) {
        nextHasHoldingsContext = true;
      }
    } else if (node instanceof ActionProfileNode action) {
      if (nextAfterMatchInstance && "CREATE".equals(action.action()) && "HOLDINGS".equals(action.folioRecord())) {
        nextHasHoldingsContext = true;
      }
      if (nextAfterMatchInstance && onMatchBranch && !nextHasHoldingsContext
        && "CREATE".equals(action.action()) && "ITEM".equals(action.folioRecord())) {
        return true;
      }
    }

    List<RegularEdge> orderedEdges = graph.outgoingEdgesOf(node).stream()
      .sorted(Comparator.comparingInt(edge -> graph.getEdgeTarget(edge).getOrder()))
      .toList();
    boolean siblingHoldingsContext = nextHasHoldingsContext;
    for (RegularEdge edge : orderedEdges) {
      Profile child = graph.getEdgeTarget(edge);
      boolean childOnMatchBranch = nextAfterMatchInstance ? resolveMatchBranch(edge, onMatchBranch) : onMatchBranch;
      if (matchesItemWithoutHoldings(graph, child, nextAfterMatchInstance, childOnMatchBranch,
        siblingHoldingsContext)) {
        return true;
      }
      if (nextAfterMatchInstance && childOnMatchBranch && createsOrMatchesHoldings(child)) {
        siblingHoldingsContext = true;
      }
    }

    return false;
  }

  private boolean resolveMatchBranch(RegularEdge edge, boolean inherited) {
    return switch (edge.getLabel()) {
      case "MATCH" -> true;
      case "NON_MATCH" -> false;
      default -> inherited;
    };
  }

  private boolean createsOrMatchesHoldings(Profile node) {
    if (node instanceof MatchProfileNode match) {
      return "HOLDINGS".equals(match.existingRecordType());
    }
    if (node instanceof ActionProfileNode action) {
      return "CREATE".equals(action.action()) && "HOLDINGS".equals(action.folioRecord());
    }
    return false;
  }
}
