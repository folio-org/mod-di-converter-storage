package org.folio.hydration;

import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.folio.rest.jaxrs.model.MatchDetail.MatchCriterion.EXACTLY_MATCHES;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;

/**
 * Factory for creating default matchDetails for different record type combinations.
 * Provides standard match criteria templates based on incoming/existing record types.
 */
public final class MatchDetailsFactory {

  private MatchDetailsFactory() {
    // Utility class
  }

  /**
   * Creates default matchDetails for the given incoming and existing record type combination.
   *
   * @param incomingRecordType the incoming record type string (e.g., "MARC_BIBLIOGRAPHIC")
   * @param existingRecordType the existing record type string (e.g., "INSTANCE")
   * @return list of MatchDetail for the record type combination
   * @throws IllegalArgumentException if the record type combination is not supported
   */
  public static List<MatchDetail> createMatchDetailsForRecordTypes(
      String incomingRecordType, String existingRecordType) {

    if (incomingRecordType == null || existingRecordType == null) {
      throw new IllegalArgumentException(
          "Incoming and existing record types must not be null");
    }

    return switch (incomingRecordType) {
      case "MARC_BIBLIOGRAPHIC" -> createMarcBibMatchDetails(existingRecordType);
      case "MARC_HOLDINGS" -> createMarcHoldingsMatchDetails(existingRecordType);
      case "MARC_AUTHORITY" -> createMarcAuthorityMatchDetails(existingRecordType);
      case "STATIC_VALUE" -> Collections.emptyList(); // Static values don't require match details
      default -> throw new IllegalArgumentException(
          String.format("Unsupported incoming record type: %s", incomingRecordType));
    };
  }

  private static List<MatchDetail> createMarcBibMatchDetails(String existingRecordType) {
    return switch (existingRecordType) {
      case "INSTANCE" -> List.of(createMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.INSTANCE,
          "001",
          "instance.hrid"));
      case "MARC_BIBLIOGRAPHIC" -> List.of(createMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.MARC_BIBLIOGRAPHIC,
          "001",
          "instance.hrid"));
      case "HOLDINGS" -> List.of(createMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.HOLDINGS,
          "004",
          "holdings.hrid"));
      case "ITEM" -> List.of(createMatchDetail(
          EntityType.MARC_BIBLIOGRAPHIC,
          EntityType.ITEM,
          "001",
          "item.hrid"));
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
          "authority.id"));
      default -> throw new IllegalArgumentException(
          String.format("Unsupported existing record type '%s' for incoming type MARC_AUTHORITY",
              existingRecordType));
    };
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
   * Creates a MatchExpression for existing record fields.
   */
  private static MatchExpression createExistingMatchExpression(String fieldPath) {
    return new MatchExpression()
        .withDataValueType(VALUE_FROM_RECORD)
        .withFields(List.of(
            new Field().withLabel("field").withValue(fieldPath)));
  }
}
