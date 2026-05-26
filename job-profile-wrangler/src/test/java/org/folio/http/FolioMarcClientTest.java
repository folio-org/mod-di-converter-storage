package org.folio.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.Test;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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

  @Test
  public void sourceRecordRequestsIncludeTenantAndOkapiUrlHeaders() throws Exception {
    AtomicReference<String> tenantHeader = new AtomicReference<>();
    AtomicReference<String> okapiUrlHeader = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/source-storage/source-records", exchange -> {
      tenantHeader.set(exchange.getRequestHeaders().getFirst("x-okapi-tenant"));
      okapiUrlHeader.set(exchange.getRequestHeaders().getFirst("x-okapi-url"));
      byte[] body = "{\"totalRecords\":0,\"sourceRecords\":[]}".getBytes();
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try {
      FolioMarcClient client = new FolioMarcClient(
        "http://localhost:" + server.getAddress().getPort(),
        "token",
        "diku",
        "http://okapi.example",
        null);

      IOException error = org.junit.Assert.assertThrows(IOException.class, client::getNextRecord);

      assertTrue(error.getMessage().contains("No MARC records found"));
      assertEquals("diku", tenantHeader.get());
      assertEquals("http://okapi.example", okapiUrlHeader.get());
    } finally {
      server.stop(0);
    }
  }

  private Field field(String name) throws NoSuchFieldException {
    Field field = FolioMarcClient.class.getDeclaredField(name);
    field.setAccessible(true);
    return field;
  }
}
