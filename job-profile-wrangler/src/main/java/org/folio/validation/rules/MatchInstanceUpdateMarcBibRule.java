package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks MATCH INSTANCE branches that the stack rejects before chunk processing.
 */
public class MatchInstanceUpdateMarcBibRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "match-instance-update-marc-bib";

  private static final String MESSAGE = "MATCH INSTANCE followed by UPDATE MARC_BIBLIOGRAPHIC with a "
    + "MARC_BIBLIOGRAPHIC mapping is rejected by mod-source-record-manager before record parsing.";
  private static final String CITATION = "mod-source-record-manager AbstractChunkProcessingService "
    + "isNotSupportedJobProfileExists.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks MATCH INSTANCE -> UPDATE MARC_BIBLIOGRAPHIC profiles rejected by source-record-manager.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasMatchInstanceUpdateMarcBib(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasMatchInstanceUpdateMarcBib(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    if (isMatchMarcBibToInstance(node)) {
      JsonNode children = children(node);
      if (children.isArray()) {
        for (JsonNode child : children) {
          if (isUpdateMarcBibAction(child) && hasMarcBibToMarcBibMapping(child)) {
            return true;
          }
        }
      }
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if (hasMatchInstanceUpdateMarcBib(child)) {
        return true;
      }
    }
    return false;
  }

  private boolean isMatchMarcBibToInstance(JsonNode node) {
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

    return "MARC_BIBLIOGRAPHIC".equals(incomingRecordType) && "INSTANCE".equals(existingRecordType);
  }

  private boolean isUpdateMarcBibAction(JsonNode node) {
    if (!"ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      return false;
    }

    JsonNode content = node.path("content");
    return "UPDATE".equals(text(content, "action"))
      && "MARC_BIBLIOGRAPHIC".equals(text(content, "folioRecord"));
  }

  private boolean hasMarcBibToMarcBibMapping(JsonNode actionNode) {
    JsonNode children = children(actionNode);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if ("MAPPING_PROFILE".equals(text(child, "contentType", "profileType"))) {
        JsonNode content = child.path("content");
        String incomingRecordType = text(content, "incomingRecordType", "recordType");
        String existingRecordType = text(content, "existingRecordType");
        JsonNode mappingDetails = content.path("mappingDetails");
        if (incomingRecordType.isBlank()) {
          incomingRecordType = text(mappingDetails, "recordType");
        }

        if ("MARC_BIBLIOGRAPHIC".equals(incomingRecordType) && "MARC_BIBLIOGRAPHIC".equals(existingRecordType)) {
          return true;
        }
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
