package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks root-level update branches that the stack executes against every incoming record.
 */
public class MultipleRootUpdateBranchesRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "multiple-root-update-branches";

  private static final String MESSAGE = "Multiple root update branches cannot be isolated by generated records; "
    + "the stack may run each root branch against the same incoming record and report duplicate source records.";
  private static final String CITATION = "data-import-processing-core EventManager advances through root job-profile "
    + "children without a per-generated-record branch discriminator.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks job profiles with more than one root child branch containing update-like actions.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (rootUpdateBranchCount(snapshot) > 1) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private int rootUpdateBranchCount(JsonNode snapshot) {
    JsonNode children = children(snapshot);
    if (!children.isArray()) {
      return 0;
    }

    int count = 0;
    for (JsonNode child : children) {
      if (hasUpdateLikeAction(child)) {
        count++;
      }
    }
    return count;
  }

  private boolean hasUpdateLikeAction(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    if ("ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      JsonNode content = node.path("content");
      String action = text(content, "action");
      String folioRecord = text(content, "folioRecord");
      if ("UPDATE".equals(action) || ("MODIFY".equals(action) && "MARC_BIBLIOGRAPHIC".equals(folioRecord))) {
        return true;
      }
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }
    for (JsonNode child : children) {
      if (hasUpdateLikeAction(child)) {
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
