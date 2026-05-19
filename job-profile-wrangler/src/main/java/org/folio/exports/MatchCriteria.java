package org.folio.exports;

import java.util.Collections;
import java.util.List;

/**
 * Represents match criteria extracted from a job profile's match profile.
 * Used to generate appropriate MARC fields for matching during import.
 *
 * @param matchProfileId the ID of the match profile these criteria came from
 * @param matchFields MARC field specifications for incoming record matching
 * @param nonMarcMatches specifications for matching on non-MARC fields (e.g., instance.hrid)
 */
public record MatchCriteria(
    String matchProfileId,
    List<MatchFieldSpec> matchFields,
    List<NonMarcMatchSpec> nonMarcMatches
) {
  /**
   * Specification for a MARC field used in matching.
   * Supports both control fields (001-009) and data fields with subfields.
   *
   * @param fieldTag MARC field tag (e.g., "001", "035", "999")
   * @param indicator1 first MARC indicator value as a single character string
   *                   (e.g., "f" for a specific indicator, " " for blank/undefined, null if not specified)
   * @param indicator2 second MARC indicator value as a single character string
   *                   (e.g., "f" for a specific indicator, " " for blank/undefined, null if not specified)
   * @param subfieldCode subfield code for data fields (e.g., "a", "i"), null for control fields
   * @param staticValue value to use for STATIC_VALUE match type, null otherwise
   */
  public record MatchFieldSpec(
      String fieldTag,
      String indicator1,
      String indicator2,
      String subfieldCode,
      String staticValue
  ) {
    /**
     * Checks if this is a control field (001-009).
     *
     * @return true if this is a control field, false if it's a data field
     */
    public boolean isControlField() {
      if (fieldTag == null || fieldTag.length() != 3) {
        return false;
      }
      try {
        int tag = Integer.parseInt(fieldTag);
        return tag >= 1 && tag <= 9;
      } catch (NumberFormatException e) {
        return false;
      }
    }

    /**
     * Checks if this field has a static value configured.
     *
     * @return true if a static value is set
     */
    public boolean hasStaticValue() {
      return staticValue != null && !staticValue.isBlank();
    }
  }

  /**
   * Specification for matching on non-MARC FOLIO fields.
   * These require post-import enrichment because the values (like instance UUIDs or HRIDs)
   * don't exist in the incoming MARC record and must be looked up from FOLIO after the
   * foundation records are imported. The enrich command queries FOLIO to retrieve these
   * values and populates the target MARC field before the update import.
   *
   * @param existingField the FOLIO field to match against (e.g., "instance.hrid", "instance.id")
   * @param targetMarcField the MARC field to populate with the value (e.g., "999")
   * @param targetSubfield the subfield code to populate (e.g., "i")
   * @param targetIndicator1 first indicator for the target field
   * @param targetIndicator2 second indicator for the target field
   */
  public record NonMarcMatchSpec(
      String existingField,
      String targetMarcField,
      String targetSubfield,
      String targetIndicator1,
      String targetIndicator2
  ) {}

  /**
   * Creates an empty MatchCriteria instance.
   *
   * @return an empty MatchCriteria with no match specifications
   */
  public static MatchCriteria empty() {
    return new MatchCriteria(null, Collections.emptyList(), Collections.emptyList());
  }

  /**
   * Checks if this MatchCriteria has no match specifications.
   *
   * @return true if both matchFields and nonMarcMatches are empty
   */
  public boolean isEmpty() {
    return (matchFields == null || matchFields.isEmpty()) &&
           (nonMarcMatches == null || nonMarcMatches.isEmpty());
  }

  /**
   * Checks if this MatchCriteria has any non-MARC match specifications
   * that require enrichment after foundation import.
   *
   * @return true if there are non-MARC matches that need enrichment
   */
  public boolean hasNonMarcMatches() {
    return nonMarcMatches != null && !nonMarcMatches.isEmpty();
  }

  /**
   * Checks if this MatchCriteria has any MARC field specifications.
   *
   * @return true if there are MARC field match specifications
   */
  public boolean hasMarcMatches() {
    return matchFields != null && !matchFields.isEmpty();
  }
}
