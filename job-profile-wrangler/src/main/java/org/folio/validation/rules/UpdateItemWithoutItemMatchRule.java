package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Blocks UPDATE ITEM paths when FOLIO has not matched an existing Item into event context first.
 */
public class UpdateItemWithoutItemMatchRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "update-item-without-item-match-context";

  private static final String MESSAGE = "UPDATE ITEM requires a prior MATCH ITEM in the same execution branch; "
    + "the update handler needs an existing Item in event context before item mapping runs.";
  private static final String CITATION = "mod-inventory UpdateItemEventHandler checks ITEM context before mapping; "
    + "MatchItemEventHandler/HoldingsItemMatcher populate that context.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks UPDATE ITEM action paths that have no prior Item match context.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasUpdateItemWithoutItemMatch(snapshot, false)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasUpdateItemWithoutItemMatch(JsonNode node, boolean hasItemContext) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    String type = text(node, "contentType", "profileType");
    JsonNode content = node.path("content");
    boolean currentHasItemContext = hasItemContext;

    if ("MATCH_PROFILE".equals(type) && "ITEM".equals(text(content, "existingRecordType"))) {
      currentHasItemContext = true;
    }

    if ("ACTION_PROFILE".equals(type)
      && "UPDATE".equals(text(content, "action"))
      && "ITEM".equals(text(content, "folioRecord"))
      && !currentHasItemContext) {
      return true;
    }

    List<JsonNode> orderedChildren = orderedChildren(node);
    if (orderedChildren.isEmpty()) {
      return false;
    }

    boolean currentMatchesItem = "MATCH_PROFILE".equals(type)
      && "ITEM".equals(text(content, "existingRecordType"));
    boolean siblingHasItemContext = currentHasItemContext;
    boolean matchBranchHasItemContext = currentMatchesItem || currentHasItemContext;
    boolean nonMatchBranchHasItemContext = hasItemContext;

    for (JsonNode child : orderedChildren) {
      String reaction = text(child, "reactTo", "reactionStatus");
      boolean childHasItemContext = currentMatchesItem
        ? branchItemContext(reaction, matchBranchHasItemContext, nonMatchBranchHasItemContext, siblingHasItemContext)
        : siblingHasItemContext;

      if (hasUpdateItemWithoutItemMatch(child, childHasItemContext)) {
        return true;
      }

      if (providesItemContext(child, childHasItemContext)) {
        if (currentMatchesItem) {
          if ("MATCH".equals(reaction)) {
            matchBranchHasItemContext = true;
          } else if ("NON_MATCH".equals(reaction)) {
            nonMatchBranchHasItemContext = true;
          } else {
            siblingHasItemContext = true;
          }
        } else {
          siblingHasItemContext = true;
        }
      }
    }

    return false;
  }

  private boolean branchItemContext(
      String reaction,
      boolean matchBranchHasItemContext,
      boolean nonMatchBranchHasItemContext,
      boolean siblingHasItemContext) {
    return switch (reaction) {
      case "MATCH" -> matchBranchHasItemContext;
      case "NON_MATCH" -> nonMatchBranchHasItemContext;
      default -> siblingHasItemContext;
    };
  }

  private boolean providesItemContext(JsonNode node, boolean inheritedItemContext) {
    String type = text(node, "contentType", "profileType");
    if ("MATCH_PROFILE".equals(type)) {
      return inheritedItemContext || "ITEM".equals(text(node.path("content"), "existingRecordType"));
    }
    return inheritedItemContext;
  }

  private JsonNode children(JsonNode node) {
    JsonNode childSnapshotWrappers = node.path("childSnapshotWrappers");
    if (!childSnapshotWrappers.isMissingNode()) {
      return childSnapshotWrappers;
    }
    return node.path("childrenWrappers");
  }

  private List<JsonNode> orderedChildren(JsonNode node) {
    JsonNode children = children(node);
    if (!children.isArray()) {
      return List.of();
    }

    List<JsonNode> ordered = new ArrayList<>();
    children.forEach(ordered::add);
    ordered.sort(Comparator.comparingInt(this::order));
    return ordered;
  }

  private int order(JsonNode node) {
    return node.path("content").path("order").asInt(node.path("order").asInt(0));
  }

  private String text(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = node.path(fieldName);
      if (!value.isMissingNode() && !value.isNull()) {
        return value.asText();
      }
    }
    return "";
  }
}
