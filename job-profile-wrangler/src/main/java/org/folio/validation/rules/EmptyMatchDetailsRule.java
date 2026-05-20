package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks match profiles that the data-import matcher cannot execute because no match detail exists.
 */
public class EmptyMatchDetailsRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "match-profile-empty-match-details";

  private static final String MESSAGE = "MATCH profile has empty matchDetails; the stack matcher requires at least "
    + "one match detail.";
  private static final String CITATION = "data-import-processing-core AbstractMatcher requires "
    + "matchProfile.matchDetails[0].";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks MATCH_PROFILE snapshots whose matchDetails array is present but empty.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasEmptyMatchDetails(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasEmptyMatchDetails(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    if ("MATCH_PROFILE".equals(text(node, "contentType", "profileType"))) {
      JsonNode matchDetails = node.path("content").path("matchDetails");
      if (matchDetails.isArray() && matchDetails.size() == 0) {
        return true;
      }
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if (hasEmptyMatchDetails(child)) {
        return true;
      }
    }
    return false;
  }

  private JsonNode children(JsonNode node) {
    JsonNode childSnapshotWrappers = node.path("childSnapshotWrappers");
    if (!childSnapshotWrappers.isMissingNode()) {
      return childSnapshotWrappers;
    }
    return node.path("childrenWrappers");
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
