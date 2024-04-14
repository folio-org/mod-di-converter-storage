package org.folio.recordgen;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import org.folio.http.FolioClient;
import org.junit.Before;
import org.junit.Test;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;
import org.marc4j.marc.VariableField;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.when;

public class RecordGeneratorTest {

  @Mock
  private FolioClient client;

  @Mock
  private RoundRobinIdentifiers roundRobinIdentifiers;


  private RecordGenerator recordGenerator;

  @Before
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    recordGenerator = new RecordGenerator(client, roundRobinIdentifiers);
  }

  @Test
  public void testGenerateWithEmptyJobProfileSnapshot() throws IOException {
    when(client.getJobProfileSnapshot(anyString())).thenReturn(Optional.empty());

    Optional<Collection<Record>> result = recordGenerator.generate("jobProfileId");

    assertTrue(result.isEmpty());
  }

  @Test
  public void testGenerateWithJobProfileSnapshot() throws IOException {
    final RoundRobinIdentifier roundRobinIdentifier = RoundRobinIdentifier.builder()
      .instanceId(UUID.randomUUID().toString())
      .holdingsId(UUID.randomUUID().toString())
      .itemId(UUID.randomUUID().toString())
      .authorityId(UUID.randomUUID().toString())
      .sourceRecordId(UUID.randomUUID().toString())
      .build();
    String snapshot = Resources.toString(Resources.getResource("record_gen_job_profile_snapshot.json"), StandardCharsets.UTF_8);
    JsonNode jobProfileSnapshot = OBJECT_MAPPER.readTree(snapshot);
    String instanceString = Resources.toString(Resources.getResource("instance_response.json"), StandardCharsets.UTF_8);
    JsonNode instanceJson = OBJECT_MAPPER.readTree(instanceString);
    String holdingsString = Resources.toString(Resources.getResource("holdings_response.json"), StandardCharsets.UTF_8);
    JsonNode holdingsJson = OBJECT_MAPPER.readTree(holdingsString);
    String sourceRecordString = Resources.toString(Resources.getResource("source_record_response.json"), StandardCharsets.UTF_8);
    JsonNode sourceRecordJson = OBJECT_MAPPER.readTree(sourceRecordString);

    when(roundRobinIdentifiers.get()).thenReturn(roundRobinIdentifier);
    when(client.getJobProfileSnapshot(anyString())).thenReturn(Optional.of(jobProfileSnapshot));
    when(client.getInstance(anyString())).thenReturn(Optional.of(instanceJson));
    when(client.getHoldings(anyString())).thenReturn(Optional.of(holdingsJson));
    when(client.getSourceRecordBySRSId(anyString())).thenReturn(Optional.of(sourceRecordJson));

    Optional<Collection<Record>> result = recordGenerator.generate("jobProfileId");

    assertNotNull(result);
    assertTrue(result.isPresent());
    Collection<Record> records = result.get();
    assertEquals(3, records.size());
    Record record = records.iterator().next();

    VariableField variableField008 = record.getVariableField("008");
    assertNotNull(variableField008);
    assertTrue(variableField008 instanceof ControlField);
    ControlField controlField008 = (ControlField) variableField008;
    assertNotNull(controlField008);
    assertNotNull(controlField008.getData());
  }
}
