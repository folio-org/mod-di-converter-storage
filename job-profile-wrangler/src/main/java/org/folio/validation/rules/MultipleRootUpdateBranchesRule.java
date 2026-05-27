package org.folio.validation.rules;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.UnsupportedShapeRule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Blocks root-level update branches that the stack executes against every incoming record.
 */
public class MultipleRootUpdateBranchesRule implements UnsupportedShapeRule {
  public static final String RULE_NAME = "multiple-root-update-branches";

  private static final String MESSAGE = "Multiple root update branches cannot be isolated by generated records; "
    + "the stack may run each root branch against the same incoming record and report duplicate source records.";
  private static final String CITATION = "data-import-processing-core EventManager advances through root job-profile "
    + "children without a branch discriminator unless generated records can vary distinct incoming match fields.";

  @Override
  public String name() {
    return RULE_NAME;
  }

  @Override
  public String description() {
    return "Blocks multiple root update branches unless their incoming match fields can be isolated.";
  }

  @Override
  public String citation() {
    return CITATION;
  }

  @Override
  public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
    if (hasMultipleNonIsolatableRootUpdateBranches(snapshot)) {
      return Optional.of(new BlockedUnsupportedWorkflow(RULE_NAME, MESSAGE));
    }
    return Optional.empty();
  }

  private boolean hasMultipleNonIsolatableRootUpdateBranches(JsonNode snapshot) {
    JsonNode children = children(snapshot);
    if (!children.isArray()) {
      return false;
    }

    List<BranchInfo> branches = new ArrayList<>();
    boolean sawRootMatchBranch = false;
    for (JsonNode child : orderedChildren(children)) {
      if (isRootMarcBibModifyCleanup(child, sawRootMatchBranch)) {
        continue;
      }
      if (hasUpdateLikeAction(child)) {
        branches.add(new BranchInfo(incomingMatchKeys(child), rootUpdateTarget(child)));
      }
      if ("MATCH_PROFILE".equals(text(child, "contentType", "profileType"))) {
        sawRootMatchBranch = true;
      }
    }
    if (branches.size() <= 1) {
      return false;
    }
    if (isCoExecutableInstanceItemUpdateStack(branches)) {
      return false;
    }

    Set<String> seen = new HashSet<>();
    for (BranchInfo branch : branches) {
      if (branch.matchKeys().isEmpty()) {
        return true;
      }
      // MARC 001 is present on every generated record, so isolation depends on the
      // writer's path-local 001 values never reusing a sibling branch's seeded match value.
      for (String key : branch.matchKeys()) {
        if (!seen.add(key)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isCoExecutableInstanceItemUpdateStack(List<BranchInfo> branches) {
    if (branches.size() != 2) {
      return false;
    }
    Set<String> targetTypes = new HashSet<>();
    for (BranchInfo branch : branches) {
      if (!branch.matchKeys().equals(List.of("001|||"))) {
        return false;
      }
      if (branch.updateTarget().isEmpty()) {
        return false;
      }
      if (!Set.of("INSTANCE", "ITEM").contains(branch.updateTarget())) {
        return false;
      }
      if (!targetTypes.add(branch.updateTarget())) {
        return false;
      }
    }
    return targetTypes.contains("INSTANCE") && targetTypes.contains("ITEM");
  }

  private List<String> incomingMatchKeys(JsonNode rootBranch) {
    List<String> keys = new ArrayList<>();
    for (JsonNode matchProfile : matchProfiles(rootBranch)) {
      JsonNode matchDetails = matchProfile.path("content").path("matchDetails");
      if (!matchDetails.isArray()) {
        continue;
      }
      for (JsonNode matchDetail : matchDetails) {
        String key = expressionKey(matchDetail.path("incomingMatchExpression"));
        if (!key.isEmpty()) {
          keys.add(key);
        }
      }
    }
    return keys;
  }

  private List<JsonNode> matchProfiles(JsonNode node) {
    List<JsonNode> matches = new ArrayList<>();
    collectMatchProfiles(node, matches);
    return matches;
  }

  private void collectMatchProfiles(JsonNode node, List<JsonNode> matches) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return;
    }
    if ("MATCH_PROFILE".equals(text(node, "contentType", "profileType"))) {
      matches.add(node);
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return;
    }
    for (JsonNode child : children) {
      collectMatchProfiles(child, matches);
    }
  }

  private String rootUpdateTarget(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return "";
    }
    if ("ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      JsonNode content = node.path("content");
      if ("UPDATE".equals(text(content, "action"))) {
        return text(content, "folioRecord");
      }
    }

    JsonNode children = children(node);
    if (!children.isArray()) {
      return "";
    }
    for (JsonNode child : children) {
      String target = rootUpdateTarget(child);
      if (!target.isEmpty()) {
        return target;
      }
    }
    return "";
  }

  private String expressionKey(JsonNode expression) {
    JsonNode fields = expression.path("fields");
    if (!fields.isArray() || fields.isEmpty()) {
      return "";
    }

    String field = "";
    String indicator1 = "";
    String indicator2 = "";
    String subfield = "";
    for (JsonNode fieldSpec : fields) {
      String label = text(fieldSpec, "label");
      String value = text(fieldSpec, "value").trim();
      if ("field".equals(label)) {
        field = value;
      } else if ("indicator1".equals(label)) {
        indicator1 = value;
      } else if ("indicator2".equals(label)) {
        indicator2 = value;
      } else if ("recordSubfield".equals(label)) {
        subfield = value;
      }
    }

    if (field.isEmpty()) {
      return "";
    }
    return field + "|" + indicator1 + "|" + indicator2 + "|" + subfield;
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

  private boolean isRootMarcBibModifyCleanup(JsonNode node, boolean sawRootMatchBranch) {
    if (!sawRootMatchBranch || !"ACTION_PROFILE".equals(text(node, "contentType", "profileType"))) {
      return false;
    }

    JsonNode content = node.path("content");
    if (!"MODIFY".equals(text(content, "action"))
      || !"MARC_BIBLIOGRAPHIC".equals(text(content, "folioRecord"))) {
      return false;
    }

    JsonNode children = children(node);
    if (!children.isArray() || children.isEmpty()) {
      return false;
    }

    JsonNode mappingContent = children.get(0).path("content");
    return "MAPPING_PROFILE".equals(text(children.get(0), "contentType", "profileType"))
      && "MODIFY".equals(text(mappingContent.path("mappingDetails"), "marcMappingOption"));
  }

  private List<JsonNode> orderedChildren(JsonNode children) {
    List<JsonNode> ordered = new ArrayList<>();
    if (!children.isArray()) {
      return ordered;
    }
    children.forEach(ordered::add);
    ordered.sort(Comparator.comparingInt(child -> child.path("order").asInt(0)));
    return ordered;
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

  private record BranchInfo(List<String> matchKeys, String updateTarget) {}
}
