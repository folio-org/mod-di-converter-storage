package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import static org.folio.profile.ProfileTree.orderedChildren;
import static org.folio.profile.ProfileTree.text;

import java.util.List;
import java.util.Optional;

/**
 * Blocks MATCH INSTANCE branches that try to create an Item before Holdings exists in that branch.
 */
public class MatchInstanceCreateItemRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "match-instance-create-item-without-holdings";

  private static final String MESSAGE = "MATCH INSTANCE followed by CREATE ITEM on a MATCH branch requires "
    + "CREATE HOLDINGS or MATCH HOLDINGS earlier in the same branch.";
  private static final String CITATION = "job-profile-wrangler/JIRA-ITEM-CREATION-LIMITATION.md; "
    + ".claude/docs/job-profiles.md";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks MATCH INSTANCE -> CREATE ITEM on a MATCH branch when no Holdings context exists first.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (matches(snapshot, false, false, false)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean matches(JsonNode node, boolean afterMatchInstance, boolean onMatchBranch, boolean hasHoldingsContext) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    String type = text(node, "contentType", "profileType");
    JsonNode content = node.path("content");

    boolean nextAfterMatchInstance = afterMatchInstance;
    boolean nextHasHoldingsContext = hasHoldingsContext;

    if ("MATCH_PROFILE".equals(type)) {
      String existingRecordType = text(content, "existingRecordType");
      if ("INSTANCE".equals(existingRecordType)) {
        nextAfterMatchInstance = true;
        nextHasHoldingsContext = false;
      } else if (nextAfterMatchInstance && "HOLDINGS".equals(existingRecordType)) {
        nextHasHoldingsContext = true;
      }
    } else if ("ACTION_PROFILE".equals(type)) {
      String action = text(content, "action");
      String folioRecord = text(content, "folioRecord");
      if (nextAfterMatchInstance && "CREATE".equals(action) && "HOLDINGS".equals(folioRecord)) {
        nextHasHoldingsContext = true;
      }
      if (nextAfterMatchInstance && onMatchBranch && !nextHasHoldingsContext
        && "CREATE".equals(action) && "ITEM".equals(folioRecord)) {
        return true;
      }
    }

    List<JsonNode> orderedChildren = orderedChildren(node);
    if (!orderedChildren.isEmpty()) {
      boolean siblingHoldingsContext = nextHasHoldingsContext;
      for (JsonNode child : orderedChildren) {
        boolean childOnMatchBranch = nextAfterMatchInstance
          ? resolveMatchBranch(child, onMatchBranch)
          : onMatchBranch;

        if (matches(child, nextAfterMatchInstance, childOnMatchBranch, siblingHoldingsContext)) {
          return true;
        }

        if (nextAfterMatchInstance && childOnMatchBranch && createsOrMatchesHoldings(child)) {
          siblingHoldingsContext = true;
        }
      }
    }

    return false;
  }

  private boolean resolveMatchBranch(JsonNode child, boolean inherited) {
    String reaction = text(child, "reactTo", "reactionStatus");
    return switch (reaction) {
      case "MATCH" -> true;
      case "NON_MATCH" -> false;
      default -> inherited;
    };
  }
  private boolean createsOrMatchesHoldings(JsonNode node) {
    String type = text(node, "contentType", "profileType");
    JsonNode content = node.path("content");
    if ("MATCH_PROFILE".equals(type)) {
      return "HOLDINGS".equals(text(content, "existingRecordType"));
    }
    if ("ACTION_PROFILE".equals(type)) {
      return "CREATE".equals(text(content, "action"))
        && "HOLDINGS".equals(text(content, "folioRecord"));
    }
    return false;
  }}
