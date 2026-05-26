package org.folio.exports;

import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.StaticValueDetails;
import org.junit.Test;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MatchExpressionHandlerTest {
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

  @Test
  public void staticValueReturnsPayloadText() {
    MatchDetail detail = new MatchDetail()
      .withIncomingMatchExpression(new MatchExpression()
        .withDataValueType(MatchExpression.DataValueType.STATIC_VALUE)
        .withStaticValueDetails(new StaticValueDetails()
          .withStaticValueType(StaticValueDetails.StaticValueType.TEXT)
          .withText("WRG-001")));

    String value = new MatchExpressionHandler(null).getValueFromMatchDetailOrExistingRecord(detail, MARC_FACTORY.newRecord());

    assertEquals("WRG-001", value);
  }

  @Test
  public void emptyIndicatorsDoNotConstrainDataFieldExtraction() {
    Record record = MARC_FACTORY.newRecord();
    var field035 = MARC_FACTORY.newDataField("035", ' ', ' ');
    field035.addSubfield(MARC_FACTORY.newSubfield('a', "(OCoLC)123456"));
    record.addVariableField(field035);
    MatchDetail detail = new MatchDetail()
      .withIncomingMatchExpression(new MatchExpression()
        .withDataValueType(MatchExpression.DataValueType.VALUE_FROM_RECORD)
        .withFields(List.of(
          new Field().withLabel("field").withValue("035"),
          new Field().withLabel("indicator1").withValue(""),
          new Field().withLabel("indicator2").withValue(""),
          new Field().withLabel("recordSubfield").withValue("a"))));

    String value = new MatchExpressionHandler(null).getValueFromMatchDetailOrExistingRecord(detail, record);

    assertEquals("(OCoLC)123456", value);
  }
}
