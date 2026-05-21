package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks MARC mapping profiles that would fail when MarcRecordModifier switches on a null option.
 */
public class MissingMarcMappingOptionRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "marc-mapping-option-missing";

  private static final String MESSAGE = "MARC mapping profile is missing mappingDetails.marcMappingOption; the stack "
    + "cannot modify or update MARC records without it.";
  private static final String CITATION = "data-import-processing-core MarcRecordModifier requires "
    + "mappingDetails.marcMappingOption.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks MAPPING_PROFILE snapshots for MARC records when marcMappingOption is missing or blank.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasMissingMarcMappingOption(snapshot, false)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasMissingMarcMappingOption(JsonNode node, boolean underDeleteMarcAuthority) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    boolean currentUnderDeleteMarcAuthority = underDeleteMarcAuthority || isDeleteMarcAuthorityAction(node);

    if ("MAPPING_PROFILE".equals(text(node, "contentType", "profileType"))) {
      JsonNode mappingDetails = node.path("content").path("mappingDetails");
      if (!currentUnderDeleteMarcAuthority
        && mappingDetails.isObject() && isMarcRecord(mappingDetails, node.path("content"))
        && text(mappingDetails, "marcMappingOption").isBlank()) {
        return true;
      }
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }

    for (JsonNode child : children) {
      if (hasMissingMarcMappingOption(child, currentUnderDeleteMarcAuthority)) {
        return true;
      }
    }
    return false;
  }

  private boolean isDeleteMarcAuthorityAction(JsonNode node) {
    if (!"ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      return false;
    }
    JsonNode content = node.path("content");
    return "DELETE".equals(text(content, "action"))
      && "MARC_AUTHORITY".equals(text(content, "folioRecord"));
  }

  private boolean isMarcRecord(JsonNode mappingDetails, JsonNode content) {
    return isMarcRecordType(text(mappingDetails, "recordType"))
      || isMarcRecordType(text(content, "existingRecordType"));
  }

  private boolean isMarcRecordType(String recordType) {
    return recordType != null && recordType.startsWith("MARC_");
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
