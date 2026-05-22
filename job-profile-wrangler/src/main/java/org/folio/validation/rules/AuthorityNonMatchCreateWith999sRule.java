package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.Optional;

/**
 * Blocks authority create-on-nonmatch profiles whose matcher requires 999 ff $s.
 */
public class AuthorityNonMatchCreateWith999sRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "authority-nonmatch-create-with-999-s-match";

  private static final String MESSAGE = "MATCH MARC_AUTHORITY -> NON_MATCH CREATE AUTHORITY using incoming "
    + "999 ff $s cannot be safely generated: adding 999$s makes authority create invalid, while omitting it "
    + "does not exercise the intended match shape.";
  private static final String CITATION = "mod-source-record-manager rejects incoming authority create records that "
    + "already contain 999ff$s or 999ff$i.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks authority non-match create profiles whose matchDetails require incoming 999 ff $s.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasAuthorityNonMatchCreateWith999s(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasAuthorityNonMatchCreateWith999s(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return false;
    }

    if (isMarcAuthorityMatch(node) && incomingMatchUses999s(node) && hasNonMatchCreateAuthority(node)) {
      return true;
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return false;
    }
    for (JsonNode child : children) {
      if (hasAuthorityNonMatchCreateWith999s(child)) {
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

  private boolean incomingMatchUses999s(JsonNode matchNode) {
    JsonNode matchDetails = matchNode.path("content").path("matchDetails");
    if (!matchDetails.isArray()) {
      return false;
    }
    for (JsonNode matchDetail : matchDetails) {
      JsonNode fields = matchDetail.path("incomingMatchExpression").path("fields");
      if (fieldSpecMatches999s(fields)) {
        return true;
      }
    }
    return false;
  }

  private boolean fieldSpecMatches999s(JsonNode fields) {
    if (!fields.isArray()) {
      return false;
    }

    String field = "";
    String indicator1 = "";
    String indicator2 = "";
    String subfield = "";
    for (JsonNode fieldNode : fields) {
      String label = text(fieldNode, "label");
      String value = text(fieldNode, "value");
      switch (label) {
        case "field" -> field = value;
        case "indicator1" -> indicator1 = value;
        case "indicator2" -> indicator2 = value;
        case "recordSubfield" -> subfield = value;
        default -> {
          // Ignore other match-expression labels.
        }
      }
    }
    return "999".equals(field) && "f".equals(indicator1) && "f".equals(indicator2) && "s".equals(subfield);
  }

  private boolean hasNonMatchCreateAuthority(JsonNode matchNode) {
    JsonNode children = children(matchNode);
    if (!children.isArray()) {
      return false;
    }
    for (JsonNode child : children) {
      JsonNode content = child.path("content");
      if ("NON_MATCH".equals(text(child, "reactTo", "reactionStatus"))
        && "ACTION_PROFILE".equals(text(child, "contentType", "profileType"))
        && "CREATE".equals(text(content, "action"))
        && "AUTHORITY".equals(text(content, "folioRecord"))) {
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
