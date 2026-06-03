package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import static org.folio.profile.ProfileTree.orderedChildren;
import static org.folio.profile.ProfileTree.text;

import java.util.List;
import java.util.Optional;

/**
 * Blocks CREATE HOLDINGS paths that have no earlier Instance context for the stack to attach to.
 */
public class CreateHoldingsWithoutInstanceContextRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "create-holdings-without-instance-context";

  private static final String MESSAGE = "CREATE HOLDINGS requires a prior CREATE INSTANCE or MATCH INSTANCE in the "
    + "same execution branch.";
  private static final String CITATION = "mod-inventory CreateHoldingEventHandler extracts instanceId from Instance "
    + "context or MARC additional subfield $i.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks CREATE HOLDINGS action paths that have no prior Instance context.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasCreateHoldingsWithoutInstanceContext(snapshot, false)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasCreateHoldingsWithoutInstanceContext(JsonNode node, boolean hasInstanceContext) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    String type = text(node, "contentType", "profileType");
    JsonNode content = node.path("content");
    boolean currentHasInstanceContext = hasInstanceContext;

    if ("ACTION_PROFILE".equals(type)) {
      String action = text(content, "action");
      String folioRecord = text(content, "folioRecord");
      if ("CREATE".equals(action) && "HOLDINGS".equals(folioRecord) && !currentHasInstanceContext) {
        return true;
      }
      if ("CREATE".equals(action) && "INSTANCE".equals(folioRecord)) {
        currentHasInstanceContext = true;
      }
    }

    List<JsonNode> orderedChildren = orderedChildren(node);
    if (orderedChildren.isEmpty()) {
      return false;
    }

    boolean currentMatchesInstance = "MATCH_PROFILE".equals(type)
      && "INSTANCE".equals(text(content, "existingRecordType"));
    boolean siblingHasInstanceContext = currentHasInstanceContext;
    boolean matchBranchHasInstanceContext = currentMatchesInstance || currentHasInstanceContext;
    boolean nonMatchBranchHasInstanceContext = currentHasInstanceContext;

    for (JsonNode child : orderedChildren) {
      String reaction = text(child, "reactTo", "reactionStatus");
      boolean childHasInstanceContext = currentMatchesInstance
        ? branchInstanceContext(reaction, matchBranchHasInstanceContext, nonMatchBranchHasInstanceContext,
          siblingHasInstanceContext)
        : siblingHasInstanceContext;

      if (hasCreateHoldingsWithoutInstanceContext(child, childHasInstanceContext)) {
        return true;
      }

      if (providesSiblingInstanceContext(child, childHasInstanceContext)) {
        if (currentMatchesInstance) {
          if ("MATCH".equals(reaction)) {
            matchBranchHasInstanceContext = true;
          } else if ("NON_MATCH".equals(reaction)) {
            nonMatchBranchHasInstanceContext = true;
          } else {
            siblingHasInstanceContext = true;
          }
        } else {
          siblingHasInstanceContext = true;
        }
      }
    }

    return false;
  }

  private boolean branchInstanceContext(
      String reaction,
      boolean matchBranchHasInstanceContext,
      boolean nonMatchBranchHasInstanceContext,
      boolean siblingHasInstanceContext) {
    return switch (reaction) {
      case "MATCH" -> matchBranchHasInstanceContext;
      case "NON_MATCH" -> nonMatchBranchHasInstanceContext;
      default -> siblingHasInstanceContext;
    };
  }

  private boolean providesSiblingInstanceContext(JsonNode node, boolean inheritedInstanceContext) {
    String type = text(node, "contentType", "profileType");
    JsonNode content = node.path("content");
    if ("ACTION_PROFILE".equals(type)) {
      return "CREATE".equals(text(content, "action")) && "INSTANCE".equals(text(content, "folioRecord"));
    }
    if ("MATCH_PROFILE".equals(type)) {
      return inheritedInstanceContext || "INSTANCE".equals(text(content, "existingRecordType"));
    }
    return inheritedInstanceContext;
  }
}
