package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks direct MATCH -> MODIFY MARC_BIBLIOGRAPHIC branches that DICS rejects at job-profile creation.
 */
public class MatchModifyMarcBibRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "match-modify-marc-bib";

  private static final String MESSAGE = "MATCH followed directly by MODIFY MARC_BIBLIOGRAPHIC is rejected by "
    + "mod-di-converter-storage when creating the job profile.";
  private static final String CITATION = "mod-di-converter-storage profile validation returns "
    + "'Modify action cannot be used right after a Match'.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks MATCH -> MODIFY MARC_BIBLIOGRAPHIC profiles rejected by converter-storage.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasMatchModifyMarcBib(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasMatchModifyMarcBib(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    JsonNode children = children(node);
    if (isMatchProfile(node) && children.isArray()) {
      for (JsonNode child : children) {
        if (isModifyMarcBibAction(child)) {
          return true;
        }
      }
    }

    if (!children.isArray()) {
      return false;
    }
    for (JsonNode child : children) {
      if (hasMatchModifyMarcBib(child)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMatchProfile(JsonNode node) {
    return "MATCH_PROFILE".equals(text(node, "contentType", "profileType"));
  }

  private boolean isModifyMarcBibAction(JsonNode node) {
    if (!"ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      return false;
    }

    JsonNode content = node.path("content");
    return "MODIFY".equals(text(content, "action"))
      && "MARC_BIBLIOGRAPHIC".equals(text(content, "folioRecord"));
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
