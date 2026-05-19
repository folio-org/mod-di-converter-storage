package org.folio.exports;

/**
 * Typed failure raised when the MARC generator cannot translate an extracted path.
 */
public class GeneratorGapException extends RuntimeException {
  public enum Reason {
    REFERENCE_DATA_MISSING,
    UNKNOWN_MATCH_FIELD,
    UNSUPPORTED_ACTION,
    MAPPING_RULES_UNAVAILABLE
  }

  private final Reason reason;
  private final String detail;

  public GeneratorGapException(Reason reason, String detail, String message) {
    super(message);
    this.reason = reason;
    this.detail = detail;
  }

  public Reason reason() {
    return reason;
  }

  public String detail() {
    return detail;
  }

  public static GeneratorGapException referenceDataMissing(String refType) {
    return new GeneratorGapException(
      Reason.REFERENCE_DATA_MISSING,
      refType,
      "Required reference data is missing: " + refType);
  }

  public static GeneratorGapException unsupportedAction(String action, String folioRecord) {
    String detail = action + " " + folioRecord;
    return new GeneratorGapException(
      Reason.UNSUPPORTED_ACTION,
      detail,
      "Unsupported action for MARC generation: " + detail);
  }

  public static GeneratorGapException mappingRulesUnavailable(String recordType) {
    return new GeneratorGapException(
      Reason.MAPPING_RULES_UNAVAILABLE,
      recordType,
      "Mapping rules unavailable for record type: " + recordType);
  }
}
