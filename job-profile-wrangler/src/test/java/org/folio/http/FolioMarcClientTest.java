package org.folio.http;

import org.junit.Test;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FolioMarcClientTest {
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

  @Test
  @SuppressWarnings("unchecked")
  public void consumedBatchClearsCacheBeforeNextFetch() throws Exception {
    FolioMarcClient client = new FolioMarcClient("http://folio.example", "token");
    Record record = MARC_FACTORY.newRecord();
    record.addVariableField(MARC_FACTORY.newControlField("001", "first"));
    List<Record> recordCache = (List<Record>) field("recordCache").get(client);
    recordCache.add(record);

    Record next = client.getNextRecord();

    assertEquals("first", next.getControlNumber());
    assertTrue(recordCache.isEmpty());
    assertEquals(0, field("currentRecordIndex").get(client));
    assertEquals(1, field("currentOffset").get(client));
  }

  private Field field(String name) throws NoSuchFieldException {
    Field field = FolioMarcClient.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
