package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks paired authority update/create branches that the current stack cannot route in one import.
 */
public class PairedAuthorityUpdateCreateRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "paired-authority-update-create";

  private static final String MESSAGE = "MATCH MARC_AUTHORITY with MATCH -> UPDATE MARC_AUTHORITY and "
    + "NON_MATCH -> CREATE AUTHORITY cannot be exercised in one stack import: update records require 999ff$s/$i "
    + "for matching, but the create branch makes mod-source-record-manager reject any incoming authority record "
    + "that already contains 999ff$s or 999ff$i.";
  private static final String CITATION = "mod-source-record-manager ChangeEngineServiceImpl "
    + "addErrorMessageWhen999ffFieldExistsOnCreateAction is profile-wide rather than branch-aware.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks paired MARC authority update/create match profiles that cannot be imported in one stack run.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasPairedAuthorityUpdateCreate(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasPairedAuthorityUpdateCreate(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    if (isMarcAuthorityMatch(node) && hasMatchUpdateAuthority(node) && hasNonMatchCreateAuthority(node)) {
      return true;
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if (hasPairedAuthorityUpdateCreate(child)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMarcAuthorityMatch(JsonNode node) {
    if (!"MATCH_PROFILE".equals(text(node, "contentType", "profileType"))) {
      return false;
    }

    JsonNode content = node.path("content");
    String incomingRecordType = text(content, "incomingRecordType");
    String existingRecordType = text(content, "existingRecordType");
    if (incomingRecordType.isBlank() || existingRecordType.isBlank()) {
      JsonNode firstMatchDetail = content.path("matchDetails").path(0);
      incomingRecordType = text(firstMatchDetail, "incomingRecordType");
      existingRecordType = text(firstMatchDetail, "existingRecordType");
    }

    return "MARC_AUTHORITY".equals(incomingRecordType) && "MARC_AUTHORITY".equals(existingRecordType);
  }

  private boolean hasMatchUpdateAuthority(JsonNode matchNode) {
    return hasChildAction(matchNode, "MATCH", "UPDATE", "MARC_AUTHORITY");
  }

  private boolean hasNonMatchCreateAuthority(JsonNode matchNode) {
    return hasChildAction(matchNode, "NON_MATCH", "CREATE", "AUTHORITY");
  }

  private boolean hasChildAction(JsonNode node, String reactTo, String action, String folioRecord) {
    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if (!"ACTION_PROFILE".equals(text(child, "contentType", "profileType"))) {
        continue;
      }
      JsonNode content = child.path("content");
      if (reactTo.equals(text(child, "reactTo", "reactionStatus"))
        && action.equals(text(content, "action"))
        && folioRecord.equals(text(content, "folioRecord"))) {
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
