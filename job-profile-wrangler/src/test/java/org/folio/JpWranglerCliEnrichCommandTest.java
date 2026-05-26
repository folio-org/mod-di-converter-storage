package org.folio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.marc4j.MarcStreamReader;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JpWranglerCliEnrichCommandTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

  @Test
  public void writeEnrichedRecordsAtomicallyWritesReadableMarcFile() throws Exception {
    JpWranglerCli.EnrichCommand command = new JpWranglerCli.EnrichCommand();
    Path output = Files.createTempFile("jp-wrangler-enrich", ".mrc");
    Record record = MARC_FACTORY.newRecord();
    record.addVariableField(MARC_FACTORY.newControlField("001", "wrangler-test"));

    Method writeRecords = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("writeEnrichedRecordsAtomically", List.class, Path.class);
    writeRecords.setAccessible(true);
    writeRecords.invoke(command, List.of(record), output);

    try (var input = Files.newInputStream(output)) {
      MarcStreamReader reader = new MarcStreamReader(input);
      assertTrue(reader.hasNext());
      assertEquals("wrangler-test", reader.next().getControlNumber());
      assertFalse(reader.hasNext());
    }
  }

  @Test
  public void sourceRecordIdEnrichmentDoesNotFallbackToEnvelopeId() throws Exception {
    JpWranglerCli.EnrichCommand command = new JpWranglerCli.EnrichCommand();
    Method extractEnrichValue = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("extractEnrichValue", com.fasterxml.jackson.databind.JsonNode.class,
        JpWranglerCli.EnrichCommand.EnrichType.class);
    extractEnrichValue.setAccessible(true);

    Object value = extractEnrichValue.invoke(command,
      OBJECT_MAPPER.readTree("{\"id\":\"source-record-wrapper-id\"}"),
      JpWranglerCli.EnrichCommand.EnrichType.SOURCE_RECORD_ID);

    assertEquals(null, value);
  }

  private Object parseEnrichField(JpWranglerCli.EnrichCommand command, String fieldSpec) throws Exception {
    Method parseEnrichField = JpWranglerCli.EnrichCommand.class
      .getDeclaredMethod("parseEnrichField", String.class);
    parseEnrichField.setAccessible(true);
    return parseEnrichField.invoke(command, fieldSpec);
  }
}
