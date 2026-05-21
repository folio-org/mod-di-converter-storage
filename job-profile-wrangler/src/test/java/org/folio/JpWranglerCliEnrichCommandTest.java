package org.folio;

import org.junit.Test;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JpWranglerCliEnrichCommandTest {
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

  @Test
  public void enrichRecordReplacesExistingTargetField() throws Exception {
    JpWranglerCli.EnrichCommand command = new JpWranglerCli.EnrichCommand();
    Object spec = parseEnrichField(command, "999ff$i");
    Record record = MARC_FACTORY.newRecord();
    DataField placeholder = MARC_FACTORY.newDataField("999", 'f', 'f');
    placeholder.addSubfield(MARC_FACTORY.newSubfield('i', "placeholder"));
    record.addVariableField(placeholder);

    Method enrichRecord = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("enrichRecord", Record.class, String.class, spec.getClass());
    enrichRecord.setAccessible(true);
    enrichRecord.invoke(command, record, "real-instance-id", spec);

    assertEquals(1, record.getVariableFields("999").size());
    DataField enriched = (DataField) record.getVariableField("999");
    assertEquals('f', enriched.getIndicator1());
    assertEquals('f', enriched.getIndicator2());
    assertEquals("real-instance-id", enriched.getSubfield('i').getData());
  }

  @Test
  public void sourceRecordTypeValidationAcceptsSourceStorageEnumsOnly() throws Exception {
    JpWranglerCli.EnrichCommand command = new JpWranglerCli.EnrichCommand();
    Method isValidSourceRecordType = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("isValidSourceRecordType", String.class);
    isValidSourceRecordType.setAccessible(true);

    assertTrue((Boolean) isValidSourceRecordType.invoke(command, "MARC_BIB"));
    assertTrue((Boolean) isValidSourceRecordType.invoke(command, "MARC_AUTHORITY"));
    assertFalse((Boolean) isValidSourceRecordType.invoke(command, "MARC_BIBLIOGRAPHIC"));
  }

  private Object parseEnrichField(JpWranglerCli.EnrichCommand command, String fieldSpec) throws Exception {
    Method parseEnrichField = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("parseEnrichField", String.class);
    parseEnrichField.setAccessible(true);
    return parseEnrichField.invoke(command, fieldSpec);
  }
}
