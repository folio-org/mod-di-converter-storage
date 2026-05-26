package org.folio.hydration;

import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.StaticValueDetails;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.folio.rest.jaxrs.model.MatchDetail.MatchCriterion.EXACTLY_MATCHES;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.STATIC_VALUE;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;
import static org.folio.rest.jaxrs.model.StaticValueDetails.StaticValueType.TEXT;

/**
 * Factory for creating default matchDetails for different record type combinations.
 * Provides standard match criteria templates based on incoming/existing record types.
 */
public final class MatchDetailsFactory {

  /**
   * Default UUID for the "System control number" identifier type.
   * This is a common UUID used across many FOLIO tenants, but may vary by installation.
   * When possible, use {@link #createMatchDetailsForRecordTypes(String, String, String)}
   * with a dynamically fetched identifier type ID from the tenant's reference data.
   *
   * <p><strong>Warning:</strong> If this UUID does not match the tenant's actual
   * "System control number" identifier type, match operations will fail silently
   * (no matches found), potentially causing CREATE operations when UPDATE was intended.
   *
   * @see org.folio.http.ReferenceDataManager#getIdByName(String, String) to lookup by name
   */
  public static final String DEFAULT_SYSTEM_CONTROL_NUMBER_TYPE_ID = "7e591197-f335-4afb-bc6d-a6d76ca3bace";

  /**
   * Name of the identifier type used for system control number matching.
   */
  public static final String SYSTEM_CONTROL_NUMBER_TYPE_NAME = "System control number";

  private MatchDetailsFactory() {
  }

  /**
   * Creates default matchDetails for the given incoming and existing record type combination.
   * Uses the default system control number identifier type ID.
   *
   * @param incomingRecordType the incoming record type string (e.g., "MARC_BIBLIOGRAPHIC")
   * @param existingRecordType the existing record type string (e.g., "INSTANCE")
   * @return list of MatchDetail for the record type combination
   * @throws IllegalArgumentException if the record type combination is not supported
   * @see #createMatchDetailsForRecordTypes(String, String, String) for tenant-specific identifier types
   */
  public static List<MatchDetail> createMatchDetailsForRecordTypes(
      String incomingRecordType, String existingRecordType) {
    return createMatchDetailsForRecordTypes(incomingRecordType, existingRecordType,
        DEFAULT_SYSTEM_CONTROL_NUMBER_TYPE_ID);
  }

  /**
   * Creates default matchDetails for the given incoming and existing record type combination.
   * Allows specifying a tenant-specific identifier type ID for system control number matching.
   *
   * @param incomingRecordType the incoming record type string (e.g., "MARC_BIBLIOGRAPHIC")
   * @param existingRecordType the existing record type string (e.g., "INSTANCE")
   * @param systemControlNumberTypeId the UUID of the "System control number" identifier type
   *        (use {@link org.folio.http.ReferenceDataManager#getIdByName} to fetch dynamically)
   * @return list of MatchDetail for the record type combination
   * @throws IllegalArgumentException if the record type combination is not supported
   */
  public static List<MatchDetail> createMatchDetailsForRecordTypes(
      String incomingRecordType, String existingRecordType, String systemControlNumberTypeId) {

    if (incomingRecordType == null || existingRecordType == null) {
      throw new IllegalArgumentException(
          "Incoming and existing record types must not be null");
    }

    return switch (incomingRecordType) {
      case "MARC_BIBLIOGRAPHIC" -> createMarcBibMatchDetails(existingRecordType, systemControlNumberTypeId);
      case "MARC_HOLDINGS" -> createMarcHoldingsMatchDetails(existingRecordType);
      case "MARC_AUTHORITY" -> createMarcAuthorityMatchDetails(existingRecordType);
      case "STATIC_VALUE" -> createStaticValueMatchDetails(existingRecordType);
      default -> throw new IllegalArgumentException(
          String.format("Unsupported incoming record type: %s", incomingRecordType));
    };
  }

  private static List<MatchDetail> createMarcBibMatchDetails(String existingRecordType,
      String systemControlNumberTypeId) {
    return switch (existingRecordType) {
      case "INSTANCE" -> List.of(createIdentifierMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.INSTANCE,
          systemControlNumberTypeId));
      // SRS rewrites stored bib 001 to the Instance HRID during create-instance imports.
      // Match bib-to-bib updates on the stable SRS source record id instead.
      case "MARC_BIBLIOGRAPHIC" -> List.of(createSrsMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.MARC_BIBLIOGRAPHIC));
      case "HOLDINGS" -> List.of(createFormerIdsMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.HOLDINGS,
          "004",
          "holdings.formerIds[]"));
      case "ITEM" -> List.of(createFormerIdsMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.ITEM,
          "001",
          "item.formerIds[]"));
      default -> throw new IllegalArgumentException(
          String.format("Unsupported existing record type '%s' for incoming type MARC_BIBLIOGRAPHIC",
              existingRecordType));
    };
  }

  private static List<MatchDetail> createMarcHoldingsMatchDetails(String existingRecordType) {
    return switch (existingRecordType) {
      case "HOLDINGS" -> List.of(createMatchDetail(
          EntityType.MARC_HOLDINGS,
          EntityType.HOLDINGS,
          "004",
          "holdings.hrid"));
      case "MARC_HOLDINGS" -> List.of(createSrsMatchDetail(
          EntityType.MARC_HOLDINGS,
          EntityType.MARC_HOLDINGS));
      default -> throw new IllegalArgumentException(
          String.format("Unsupported existing record type '%s' for incoming type MARC_HOLDINGS",
              existingRecordType));
    };
  }

  private static List<MatchDetail> createMarcAuthorityMatchDetails(String existingRecordType) {
    return switch (existingRecordType) {
      case "MARC_AUTHORITY" -> List.of(createSrsMatchDetail(
          EntityType.MARC_AUTHORITY,
          EntityType.MARC_AUTHORITY));
      case "AUTHORITY" -> List.of(createMatchDetail(
          EntityType.MARC_AUTHORITY,
          EntityType.AUTHORITY,
          "001",
          "authority.naturalId"));
      default -> throw new IllegalArgumentException(
          String.format("Unsupported existing record type '%s' for incoming type MARC_AUTHORITY",
              existingRecordType));
    };
  }

  private static List<MatchDetail> createStaticValueMatchDetails(String existingRecordType) {
    return switch (existingRecordType) {
      case "INSTANCE" -> List.of(createStaticValueMatchDetail(
          EntityType.INSTANCE,
          "false",
          "instance.discoverySuppress"));
      case "HOLDINGS" -> List.of(createStaticValueMatchDetail(
          EntityType.HOLDINGS,
          "false",
          "holdingsrecord.discoverySuppress"));
      case "ITEM" -> List.of(createStaticValueMatchDetail(
          EntityType.ITEM,
          "Available",
          "item.status.name"));
      default -> throw new IllegalArgumentException(
          String.format("Unsupported existing record type '%s' for incoming type STATIC_VALUE",
              existingRecordType));
    };
  }

  private static MatchDetail createStaticValueMatchDetail(
      EntityType existingRecordType,
      String staticText,
      String existingFieldPath) {

    return new MatchDetail()
        .withIncomingRecordType(EntityType.STATIC_VALUE)
        .withExistingRecordType(existingRecordType)
        .withIncomingMatchExpression(createStaticValueMatchExpression(staticText))
        .withMatchCriterion(EXACTLY_MATCHES)
        .withExistingMatchExpression(createExistingMatchExpression(existingFieldPath));
  }

  private static MatchDetail createMatchDetail(
      EntityType incomingRecordType,
      EntityType existingRecordType,
      String incomingMarcField,
      String existingFieldPath) {

    return new MatchDetail()
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)
        .withIncomingMatchExpression(createMarcMatchExpression(incomingMarcField))
        .withMatchCriterion(EXACTLY_MATCHES)
        .withExistingMatchExpression(createExistingMatchExpression(existingFieldPath));
  }

  /**
   * Creates a MatchDetail for MARC-to-INSTANCE matching using the 001 field
   * matched against instance.identifiers[].value with "System control number" type.
   * This supports UPDATE scenarios where the system generates HRIDs but preserves
   * the 001 value in the identifiers array.
   *
   * @param incomingRecordType the incoming record type
   * @param existingRecordType the existing record type
   * @param systemControlNumberTypeId the UUID of the "System control number" identifier type
   */
  private static MatchDetail createIdentifierMatchDetail(
      EntityType incomingRecordType,
      EntityType existingRecordType,
      String systemControlNumberTypeId) {

    return new MatchDetail()
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)
        .withIncomingMatchExpression(createMarcMatchExpression("001"))
        .withMatchCriterion(EXACTLY_MATCHES)
        .withExistingMatchExpression(createIdentifierMatchExpression(systemControlNumberTypeId));
  }

  /**
   * Creates a MatchExpression for matching against instance.identifiers[].value
   * filtered by the specified identifier type.
   *
   * @param identifierTypeId the UUID of the identifier type (e.g., "System control number")
   */
  private static MatchExpression createIdentifierMatchExpression(String identifierTypeId) {
    return new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(Arrays.asList(
            new Field().withLabel("field").withValue("instance.identifiers[].value"),
            new Field().withLabel("identifierTypeId").withValue(identifierTypeId)));
  }

  /**
   * Creates a MatchDetail for SRS MARC-to-MARC matching using the 999 ff $s field.
   * This pattern matches the UUID stored in 999 ff $s between incoming and existing MARC records.
   */
  private static MatchDetail createSrsMatchDetail(
      EntityType incomingRecordType,
      EntityType existingRecordType) {

    MatchExpression srsMatchExpression = createSrs999MatchExpression();

    return new MatchDetail()
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)
        .withIncomingMatchExpression(srsMatchExpression)
        .withMatchCriterion(EXACTLY_MATCHES)
        .withExistingMatchExpression(srsMatchExpression);
  }

  /**
   * Creates a MatchDetail for matching MARC fields to formerIds arrays.
   * This is used for UPDATE scenarios where the system auto-generates HRIDs but preserves
   * the incoming MARC field value in the formerIds array (simple string array).
   */
  private static MatchDetail createFormerIdsMatchDetail(
      EntityType incomingRecordType,
      EntityType existingRecordType,
      String marcField,
      String formerIdsPath) {

    return new MatchDetail()
        .withIncomingRecordType(incomingRecordType)
        .withExistingRecordType(existingRecordType)
        .withIncomingMatchExpression(createMarcMatchExpression(marcField))
        .withMatchCriterion(EXACTLY_MATCHES)
        .withExistingMatchExpression(createExistingMatchExpression(formerIdsPath));
  }

  /**
   * Creates a MatchExpression for MARC fields with the standard field structure.
   */
  private static MatchExpression createMarcMatchExpression(String marcField) {
    return new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(Arrays.asList(
            new Field().withLabel("field").withValue(marcField),
            new Field().withLabel("indicator1").withValue(""),
            new Field().withLabel("indicator2").withValue(""),
            new Field().withLabel("recordSubfield").withValue("")));
  }

  /**
   * Creates a MatchExpression for the SRS 999 ff $s field (UUID matching).
   * This is the standard pattern for matching MARC records in source-record-storage.
   */
  private static MatchExpression createSrs999MatchExpression() {
    return new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(Arrays.asList(
            new Field().withLabel("field").withValue("999"),
            new Field().withLabel("indicator1").withValue("f"),
            new Field().withLabel("indicator2").withValue("f"),
            new Field().withLabel("recordSubfield").withValue("s")));
  }

  /**
   * Creates a generic static-value expression. The wrangler stores profile shape,
   * so the exported value only needs to be syntactically valid for FOLIO import.
   */
  private static MatchExpression createStaticValueMatchExpression(String text) {
    return new MatchExpression()
        .withDataValueType(STATIC_VALUE)
        .withFields(Collections.emptyList())
        .withStaticValueDetails(new StaticValueDetails()
            .withStaticValueType(TEXT)
            .withText(text)
            .withNumber(""));
  }

  /**
   * Creates a MatchExpression for existing record fields.
   */
  private static MatchExpression createExistingMatchExpression(String fieldPath) {
    return new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(List.of(
            new Field().withLabel("field").withValue(fieldPath)));
  }
}
