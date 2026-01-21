package org.folio.exports;

import java.util.*;

/**
 * Holds the reverse mapping analysis from inventory fields to MARC field specifications.
 * This is used to determine which MARC fields are needed to populate specific inventory fields.
 * This class is immutable - use the Builder to construct instances.
 */
public final class MappingRulesAnalysis {

  /**
   * Represents a MARC field specification with tag, subfields, and optional conditions.
   */
  public record MarcFieldSpec(
    String tag,
    List<String> subfields,
    List<MappingCondition> conditions,
    String targetField
  ) {
    public MarcFieldSpec(String tag, List<String> subfields, String targetField) {
      this(tag, subfields, Collections.emptyList(), targetField);
    }

    public MarcFieldSpec {
      subfields = List.copyOf(subfields);
      conditions = List.copyOf(conditions);
    }
  }

  /**
   * Represents a mapping condition that may require reference data.
   */
  public record MappingCondition(
    String type,
    String referenceDataType,
    String expectedValue
  ) {}

  private final Map<String, List<MarcFieldSpec>> inventoryToMarcMapping;
  private final Set<String> requiredInventoryFields;
  private final Set<String> requiredReferenceDataTypes;

  private MappingRulesAnalysis(Builder builder) {
    Map<String, List<MarcFieldSpec>> tempMapping = new HashMap<>();
    for (var entry : builder.inventoryToMarcMapping.entrySet()) {
      tempMapping.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    this.inventoryToMarcMapping = Map.copyOf(tempMapping);
    this.requiredInventoryFields = Set.copyOf(builder.requiredInventoryFields);
    this.requiredReferenceDataTypes = Set.copyOf(builder.requiredReferenceDataTypes);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the MARC field specifications that populate a given inventory field.
   *
   * @param inventoryField the target inventory field name
   * @return list of MARC field specs that can populate this field
   */
  public List<MarcFieldSpec> getMarcFieldsForTarget(String inventoryField) {
    return inventoryToMarcMapping.getOrDefault(inventoryField, Collections.emptyList());
  }

  /**
   * Checks if reference data is required for a specific condition type.
   *
   * @param conditionType the condition type to check
   * @return true if reference data is required
   */
  public boolean requiresReferenceData(String conditionType) {
    return requiredReferenceDataTypes.contains(conditionType);
  }

  /**
   * Gets all inventory fields that have MARC mappings.
   *
   * @return set of inventory field names
   */
  public Set<String> getAllMappedInventoryFields() {
    return inventoryToMarcMapping.keySet();
  }

  /**
   * Gets the set of required inventory fields.
   *
   * @return set of required inventory field names
   */
  public Set<String> getRequiredInventoryFields() {
    return requiredInventoryFields;
  }

  /**
   * Gets the set of reference data types needed.
   *
   * @return set of reference data type names
   */
  public Set<String> getRequiredReferenceDataTypes() {
    return requiredReferenceDataTypes;
  }

  /**
   * Gets all unique MARC tags that are mapped.
   *
   * @return set of MARC tag numbers
   */
  public Set<String> getAllMarcTags() {
    Set<String> tags = new HashSet<>();
    for (List<MarcFieldSpec> specs : inventoryToMarcMapping.values()) {
      for (MarcFieldSpec spec : specs) {
        tags.add(spec.tag());
      }
    }
    return tags;
  }

  @Override
  public String toString() {
    return "MappingRulesAnalysis{" +
      "mappedFields=" + inventoryToMarcMapping.size() +
      ", requiredFields=" + requiredInventoryFields.size() +
      ", refDataTypes=" + requiredReferenceDataTypes.size() +
      '}';
  }

  /**
   * Builder for constructing MappingRulesAnalysis instances.
   */
  public static final class Builder {
    private final Map<String, List<MarcFieldSpec>> inventoryToMarcMapping = new HashMap<>();
    private final Set<String> requiredInventoryFields = new HashSet<>();
    private final Set<String> requiredReferenceDataTypes = new HashSet<>();

    private Builder() {}

    /**
     * Adds a MARC field spec for an inventory field.
     *
     * @param inventoryField the target inventory field
     * @param spec the MARC field specification
     * @return this builder
     */
    public Builder addMarcFieldSpec(String inventoryField, MarcFieldSpec spec) {
      inventoryToMarcMapping.computeIfAbsent(inventoryField, k -> new ArrayList<>()).add(spec);
      return this;
    }

    /**
     * Marks an inventory field as required.
     *
     * @param inventoryField the field to mark as required
     * @return this builder
     */
    public Builder addRequiredField(String inventoryField) {
      requiredInventoryFields.add(inventoryField);
      return this;
    }

    /**
     * Adds a required reference data type.
     *
     * @param referenceDataType the reference data type needed
     * @return this builder
     */
    public Builder addRequiredReferenceDataType(String referenceDataType) {
      requiredReferenceDataTypes.add(referenceDataType);
      return this;
    }

    /**
     * Builds an immutable MappingRulesAnalysis instance.
     *
     * @return the built MappingRulesAnalysis
     */
    public MappingRulesAnalysis build() {
      return new MappingRulesAnalysis(this);
    }
  }
}
