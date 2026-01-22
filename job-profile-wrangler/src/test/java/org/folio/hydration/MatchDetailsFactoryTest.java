package org.folio.hydration;

import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.junit.Test;

import java.util.List;

import static org.folio.rest.jaxrs.model.MatchDetail.MatchCriterion.EXACTLY_MATCHES;
import static org.folio.rest.jaxrs.model.MatchExpression.DataValueType.VALUE_FROM_RECORD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MatchDetailsFactoryTest {

  @Test
  public void shouldCreateMatchDetailsForMarcBibToInstance() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_BIBLIOGRAPHIC", "INSTANCE");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_BIBLIOGRAPHIC, detail.getIncomingRecordType());
    assertEquals(EntityType.INSTANCE, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());

    // Verify incoming match expression (001 field)
    assertNotNull(detail.getIncomingMatchExpression());
    assertEquals(VALUE_FROM_RECORD, detail.getIncomingMatchExpression().getDataValueType());
    assertEquals(4, detail.getIncomingMatchExpression().getFields().size());
    assertEquals("field", detail.getIncomingMatchExpression().getFields().get(0).getLabel());
    assertEquals("001", detail.getIncomingMatchExpression().getFields().get(0).getValue());

    // Verify existing match expression (instance.identifiers[].value with identifierTypeId)
    assertNotNull(detail.getExistingMatchExpression());
    assertEquals(VALUE_FROM_RECORD, detail.getExistingMatchExpression().getDataValueType());
    assertEquals(2, detail.getExistingMatchExpression().getFields().size());
    assertEquals("field", detail.getExistingMatchExpression().getFields().get(0).getLabel());
    assertEquals("instance.identifiers[].value", detail.getExistingMatchExpression().getFields().get(0).getValue());
    assertEquals("identifierTypeId", detail.getExistingMatchExpression().getFields().get(1).getLabel());
    assertEquals("7e591197-f335-4afb-bc6d-a6d76ca3bace", detail.getExistingMatchExpression().getFields().get(1).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcBibToMarcBib() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_BIBLIOGRAPHIC", "MARC_BIBLIOGRAPHIC");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_BIBLIOGRAPHIC, detail.getIncomingRecordType());
    assertEquals(EntityType.MARC_BIBLIOGRAPHIC, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());

    // Verify 001 field on both incoming and existing (MARC-to-MARC matching)
    var incomingFields = detail.getIncomingMatchExpression().getFields();
    assertEquals("001", incomingFields.get(0).getValue());
    assertEquals("", incomingFields.get(1).getValue()); // indicator1
    assertEquals("", incomingFields.get(2).getValue()); // indicator2
    assertEquals("", incomingFields.get(3).getValue()); // recordSubfield

    var existingFields = detail.getExistingMatchExpression().getFields();
    assertEquals("001", existingFields.get(0).getValue());
    assertEquals("", existingFields.get(1).getValue()); // indicator1
    assertEquals("", existingFields.get(2).getValue()); // indicator2
    assertEquals("", existingFields.get(3).getValue()); // recordSubfield
  }

  @Test
  public void shouldCreateMatchDetailsForMarcBibToHoldings() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_BIBLIOGRAPHIC", "HOLDINGS");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_BIBLIOGRAPHIC, detail.getIncomingRecordType());
    assertEquals(EntityType.HOLDINGS, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());
    assertEquals("004", detail.getIncomingMatchExpression().getFields().get(0).getValue());
    assertEquals("holdings.formerIds[]", detail.getExistingMatchExpression().getFields().get(0).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcHoldingsToHoldings() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_HOLDINGS", "HOLDINGS");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_HOLDINGS, detail.getIncomingRecordType());
    assertEquals(EntityType.HOLDINGS, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());
    assertEquals("004", detail.getIncomingMatchExpression().getFields().get(0).getValue());
    assertEquals("holdings.hrid", detail.getExistingMatchExpression().getFields().get(0).getValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionForUnsupportedIncomingType() {
    MatchDetailsFactory.createMatchDetailsForRecordTypes("UNSUPPORTED_TYPE", "INSTANCE");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionForUnsupportedExistingType() {
    MatchDetailsFactory.createMatchDetailsForRecordTypes("MARC_BIBLIOGRAPHIC", "UNSUPPORTED_TYPE");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionForNullIncomingType() {
    MatchDetailsFactory.createMatchDetailsForRecordTypes(null, "INSTANCE");
  }

  @Test(expected = IllegalArgumentException.class)
  public void shouldThrowExceptionForNullExistingType() {
    MatchDetailsFactory.createMatchDetailsForRecordTypes("MARC_BIBLIOGRAPHIC", null);
  }

  @Test
  public void shouldHaveCorrectMarcFieldStructure() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_BIBLIOGRAPHIC", "INSTANCE");

    MatchDetail detail = matchDetails.get(0);
    var fields = detail.getIncomingMatchExpression().getFields();

    // Verify all MARC field components are present
    assertEquals(4, fields.size());
    assertEquals("field", fields.get(0).getLabel());
    assertEquals("indicator1", fields.get(1).getLabel());
    assertEquals("indicator2", fields.get(2).getLabel());
    assertEquals("recordSubfield", fields.get(3).getLabel());

    // Verify empty values for indicators and subfield
    assertEquals("", fields.get(1).getValue());
    assertEquals("", fields.get(2).getValue());
    assertEquals("", fields.get(3).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcBibToItem() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_BIBLIOGRAPHIC", "ITEM");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_BIBLIOGRAPHIC, detail.getIncomingRecordType());
    assertEquals(EntityType.ITEM, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());
    assertEquals("001", detail.getIncomingMatchExpression().getFields().get(0).getValue());
    assertEquals("item.formerIds[]", detail.getExistingMatchExpression().getFields().get(0).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcHoldingsToMarcHoldings() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_HOLDINGS", "MARC_HOLDINGS");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_HOLDINGS, detail.getIncomingRecordType());
    assertEquals(EntityType.MARC_HOLDINGS, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());

    // Verify 999 ff $s pattern for both incoming and existing
    var incomingFields = detail.getIncomingMatchExpression().getFields();
    assertEquals("999", incomingFields.get(0).getValue());
    assertEquals("f", incomingFields.get(1).getValue());
    assertEquals("f", incomingFields.get(2).getValue());
    assertEquals("s", incomingFields.get(3).getValue());

    var existingFields = detail.getExistingMatchExpression().getFields();
    assertEquals("999", existingFields.get(0).getValue());
    assertEquals("f", existingFields.get(1).getValue());
    assertEquals("f", existingFields.get(2).getValue());
    assertEquals("s", existingFields.get(3).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcAuthorityToMarcAuthority() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_AUTHORITY", "MARC_AUTHORITY");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_AUTHORITY, detail.getIncomingRecordType());
    assertEquals(EntityType.MARC_AUTHORITY, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());

    // Verify 999 ff $s pattern
    var incomingFields = detail.getIncomingMatchExpression().getFields();
    assertEquals("999", incomingFields.get(0).getValue());
    assertEquals("f", incomingFields.get(1).getValue());
    assertEquals("f", incomingFields.get(2).getValue());
    assertEquals("s", incomingFields.get(3).getValue());
  }

  @Test
  public void shouldCreateMatchDetailsForMarcAuthorityToAuthority() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "MARC_AUTHORITY", "AUTHORITY");

    assertNotNull(matchDetails);
    assertEquals(1, matchDetails.size());

    MatchDetail detail = matchDetails.get(0);
    assertEquals(EntityType.MARC_AUTHORITY, detail.getIncomingRecordType());
    assertEquals(EntityType.AUTHORITY, detail.getExistingRecordType());
    assertEquals(EXACTLY_MATCHES, detail.getMatchCriterion());
    assertEquals("001", detail.getIncomingMatchExpression().getFields().get(0).getValue());
    assertEquals("authority.id", detail.getExistingMatchExpression().getFields().get(0).getValue());
  }

  @Test
  public void shouldReturnEmptyListForStaticValue() {
    List<MatchDetail> matchDetails = MatchDetailsFactory.createMatchDetailsForRecordTypes(
        "STATIC_VALUE", "INSTANCE");

    assertNotNull(matchDetails);
    assertTrue(matchDetails.isEmpty());
  }

  @Test
  public void shouldReturnEmptyListForStaticValueWithAnyExistingType() {
    // STATIC_VALUE should return empty list regardless of existing type
    assertTrue(MatchDetailsFactory.createMatchDetailsForRecordTypes("STATIC_VALUE", "INSTANCE").isEmpty());
    assertTrue(MatchDetailsFactory.createMatchDetailsForRecordTypes("STATIC_VALUE", "HOLDINGS").isEmpty());
    assertTrue(MatchDetailsFactory.createMatchDetailsForRecordTypes("STATIC_VALUE", "ITEM").isEmpty());
    assertTrue(MatchDetailsFactory.createMatchDetailsForRecordTypes("STATIC_VALUE", "MARC_BIBLIOGRAPHIC").isEmpty());
  }
}
