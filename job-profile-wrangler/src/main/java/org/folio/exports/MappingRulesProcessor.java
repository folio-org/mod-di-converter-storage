package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.http.FolioClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Processes mapping rules from mod-source-record-manager to reverse-engineer
 * required MARC fields and extract target inventory fields.
 *
 * This component fetches mapping rules via the /mapping-metadata/type/{recordType} API
 * and parses the JSON structure to determine what inventory fields are affected.
 */
public class MappingRulesProcessor {

  private static final Logger LOGGER = LogManager.getLogger(MappingRulesProcessor.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FolioClient folioClient;

  // Map FOLIO record types to mapping rule record types
  private static final Map<String, String> RECORD_TYPE_MAPPING = Map.of(
    "INSTANCE", "marc-bib",
    "HOLDINGS", "marc-holdings",
    "ITEM", "marc-bib"  // Items are created from bibliographic records
  );

  public MappingRulesProcessor(FolioClient folioClient) {
    this.folioClient = folioClient;
  }

  /**
   * Analyzes mapping rules for the given record types and extracts target inventory fields.
   *
   * @param recordTypes set of FOLIO record types (INSTANCE, HOLDINGS, ITEM)
   * @return set of target inventory fields that will be affected
   */
  public Set<String> analyzeMappingRules(Set<String> recordTypes) {
    Set<String> targetFields = new HashSet<>();

    for (String recordType : recordTypes) {
      String mappingRuleRecordType = RECORD_TYPE_MAPPING.get(recordType);
      if (mappingRuleRecordType != null) {
        Set<String> fieldsForType = extractTargetFieldsForRecordType(mappingRuleRecordType);
        targetFields.addAll(fieldsForType);
        LOGGER.info("Found {} target fields for record type {}", fieldsForType.size(), recordType);
      }
    }

    return targetFields;
  }

  /**
   * Extracts target inventory fields from mapping rules for a specific record type.
   *
   * @param recordType the mapping rule record type (marc-bib, marc-holdings, marc-authority)
   * @return set of target inventory fields
   */
  private Set<String> extractTargetFieldsForRecordType(String recordType) {
    try {
      Optional<JsonNode> mappingMetadata = fetchMappingRules(recordType);
      if (mappingMetadata.isEmpty()) {
        LOGGER.warn("No mapping metadata found for record type: {}", recordType);
        return Collections.emptySet();
      }

      return parseTargetFields(mappingMetadata.get());
    } catch (Exception e) {
      LOGGER.error("Error extracting target fields for record type: {}", recordType, e);
      return Collections.emptySet();
    }
  }

  /**
   * Fetches mapping rules from mod-source-record-manager API.
   *
   * @param recordType the record type (marc-bib, marc-holdings, marc-authority)
   * @return optional JSON response containing mapping metadata
   */
  private Optional<JsonNode> fetchMappingRules(String recordType) {
    LOGGER.info("Fetching mapping rules for record type: {}", recordType);
    return folioClient.getMappingMetadata(recordType);
  }

  /**
   * Parses mapping rules JSON to extract target inventory fields.
   *
   * @param mappingMetadata the mapping metadata JSON response
   * @return set of target inventory fields
   */
  private Set<String> parseTargetFields(JsonNode mappingMetadata) {
    Set<String> targetFields = new HashSet<>();

    try {
      // Parse the mappingRules field which contains encoded JSON
      JsonNode mappingRulesNode = mappingMetadata.get("mappingRules");
      if (mappingRulesNode != null && mappingRulesNode.isTextual()) {
        String mappingRulesJson = mappingRulesNode.asText();
        JsonNode mappingRules = OBJECT_MAPPER.readTree(mappingRulesJson);

        // Extract target fields from the mapping rules structure
        extractTargetFieldsRecursively(mappingRules, targetFields);
      }
    } catch (IOException e) {
      LOGGER.error("Error parsing mapping rules JSON", e);
    }

    return targetFields;
  }

  /**
   * Recursively extracts target fields from mapping rules JSON structure.
   *
   * @param node the current JSON node to process
   * @param targetFields the set to collect target fields
   */
  private void extractTargetFieldsRecursively(JsonNode node, Set<String> targetFields) {
    if (node.isObject()) {
      // Look for "target" field
      JsonNode targetNode = node.get("target");
      if (targetNode != null && targetNode.isTextual()) {
        String target = targetNode.asText();
        if (!target.isEmpty()) {
          targetFields.add(target);
        }
      }

      // Recursively process all object fields
      node.fields().forEachRemaining(entry ->
        extractTargetFieldsRecursively(entry.getValue(), targetFields));
    } else if (node.isArray()) {
      // Recursively process all array elements
      node.forEach(element -> extractTargetFieldsRecursively(element, targetFields));
    }
  }

  /**
   * Determines required MARC fields based on target inventory fields.
   *
   * @param targetFields set of target inventory fields
   * @return set of required MARC fields and subfields
   */
  public Set<String> getRequiredMarcFields(Set<String> targetFields) {
    // TODO: Implement reverse mapping from inventory fields to MARC fields
    // This would require analyzing the mapping rules to determine which MARC fields
    // are needed to populate the given inventory fields
    LOGGER.info("Getting required MARC fields for {} target fields", targetFields.size());
    return Collections.emptySet();
  }

  /**
   * Determines the reference data type for a given inventory field.
   *
   * @param inventoryField the inventory field name
   * @return the reference data type or null if not a reference field
   */
  public String determineReferenceType(String inventoryField) {
    // Map inventory fields to their corresponding reference data endpoints
    return REFERENCE_FIELD_MAPPINGS.get(inventoryField);
  }

  // Map of inventory fields to their reference data types
  private static final Map<String, String> REFERENCE_FIELD_MAPPINGS = Map.of(
    "instanceTypeId", "instance-types",
    "modeOfIssuanceId", "modes-of-issuance",
    "instanceFormatIds", "instance-formats",
    "statusId", "instance-statuses",
    "statisticalCodeIds", "statistical-codes",
    "natureOfContentTermIds", "nature-of-content-terms"
  );

  /**
   * Fetches mapping rules and analyzes them to build a reverse mapping index.
   * This allows determining which MARC fields populate which inventory fields.
   *
   * @param recordType the record type (e.g., "marc-bib")
   * @return MappingRulesAnalysis containing the reverse mapping
   */
  public MappingRulesAnalysis fetchAndAnalyze(String recordType) {
    MappingRulesAnalysis.Builder builder = MappingRulesAnalysis.builder();

    Optional<JsonNode> mappingMetadata = fetchMappingRules(recordType);
    if (mappingMetadata.isEmpty()) {
      LOGGER.warn("No mapping metadata found for record type: {}", recordType);
      return builder.build();
    }

    try {
      JsonNode metadata = mappingMetadata.get();
      JsonNode mappingRulesNode = metadata.get("mappingRules");

      if (mappingRulesNode != null && mappingRulesNode.isTextual()) {
        String mappingRulesJson = mappingRulesNode.asText();
        JsonNode mappingRules = OBJECT_MAPPER.readTree(mappingRulesJson);

        // Build reverse mapping from the rules
        buildReverseMapping(mappingRules, builder);
      }

      // Add required fields based on FOLIO instance requirements
      addRequiredInstanceFields(builder);

    } catch (IOException e) {
      LOGGER.error("Error parsing mapping rules JSON for {}", recordType, e);
    }

    MappingRulesAnalysis analysis = builder.build();
    LOGGER.info("Built mapping analysis with {} mapped fields, {} required fields",
      analysis.getAllMappedInventoryFields().size(),
      analysis.getRequiredInventoryFields().size());

    return analysis;
  }

  /**
   * Builds the reverse mapping from inventory fields to MARC field specs.
   *
   * @param mappingRules the mapping rules JSON
   * @param builder the builder to populate
   */
  private void buildReverseMapping(JsonNode mappingRules, MappingRulesAnalysis.Builder builder) {
    // Iterate through each MARC tag in the mapping rules
    Iterator<String> tagIterator = mappingRules.fieldNames();
    while (tagIterator.hasNext()) {
      String tag = tagIterator.next();
      JsonNode tagRules = mappingRules.get(tag);

      if (tagRules.isArray()) {
        for (JsonNode rule : tagRules) {
          processRule(tag, rule, builder);
        }
      }
    }
  }

  /**
   * Processes a single mapping rule and adds it to the builder.
   *
   * @param tag the MARC tag
   * @param rule the rule JSON node
   * @param builder the builder to populate
   */
  private void processRule(String tag, JsonNode rule, MappingRulesAnalysis.Builder builder) {
    String target = rule.path("target").asText();
    if (target == null || target.isEmpty()) {
      return;
    }

    // Extract subfields
    List<String> subfields = new ArrayList<>();
    JsonNode subfieldsNode = rule.path("subfield");
    if (subfieldsNode.isArray()) {
      for (JsonNode sf : subfieldsNode) {
        subfields.add(sf.asText());
      }
    } else if (subfieldsNode.isTextual()) {
      subfields.add(subfieldsNode.asText());
    }

    // Extract conditions
    List<MappingRulesAnalysis.MappingCondition> conditions = new ArrayList<>();
    JsonNode rulesNode = rule.path("rules");
    if (rulesNode.isArray()) {
      for (JsonNode conditionRule : rulesNode) {
        JsonNode conditionsNode = conditionRule.path("conditions");
        if (conditionsNode.isArray()) {
          for (JsonNode condition : conditionsNode) {
            String type = condition.path("type").asText();
            String refDataType = determineReferenceTypeFromCondition(type);
            String expectedValue = condition.path("value").asText();

            if (refDataType != null) {
              builder.addRequiredReferenceDataType(refDataType);
            }

            conditions.add(new MappingRulesAnalysis.MappingCondition(
              type, refDataType, expectedValue
            ));
          }
        }
      }
    }

    MappingRulesAnalysis.MarcFieldSpec spec = new MappingRulesAnalysis.MarcFieldSpec(
      tag, subfields, conditions, target
    );
    builder.addMarcFieldSpec(target, spec);
  }

  /**
   * Determines the reference data type from a condition type.
   *
   * @param conditionType the condition type string
   * @return the reference data type or null if not a reference
   */
  private String determineReferenceTypeFromCondition(String conditionType) {
    if (conditionType == null) {
      return null;
    }
    // Extract reference data type from condition type like "set_instance_type_id"
    if (conditionType.contains("instance_type")) {
      return "instance-types";
    } else if (conditionType.contains("mode_of_issuance")) {
      return "modes-of-issuance";
    } else if (conditionType.contains("instance_format")) {
      return "instance-formats";
    }
    return null;
  }

  /**
   * Adds the required inventory fields for a valid FOLIO Instance.
   *
   * @param builder the builder to populate
   */
  private void addRequiredInstanceFields(MappingRulesAnalysis.Builder builder) {
    // Title is required for all instances
    builder.addRequiredField("title");
    // instanceTypeId is required
    builder.addRequiredField("instanceTypeId");
    // source is required
    builder.addRequiredField("source");
  }
}