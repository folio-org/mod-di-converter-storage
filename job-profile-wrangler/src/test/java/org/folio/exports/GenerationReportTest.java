package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerationReportTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void serializesV1ShapeWithReferenceDataObjects() throws Exception {
    GenerationReport report = GenerationReport.of(
      "profile-1",
      "Profile One",
      "2026-05-19T20:00:00Z",
      GenerationOutcome.Generated.INSTANCE,
      List.of(new PathOutcome(
        0,
        "Job->Create Instance",
        "NON_MATCH",
        null,
        List.of(new PathOutcome.DestinationFile("records-import.mrc", "import")),
        List.of(new PathOutcome.FieldWritten("245$a", "Sample title", "instance.title")),
        GenerationOutcome.Generated.INSTANCE
      )),
      new MinimalMarcRecordBuilder.ReferenceDataContext("loc-1", "mat-1", "loan-1")
    );

    JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(report));

    assertEquals("profile-1", json.get("profileId").asText());
    assertEquals("Profile One", json.get("profileName").asText());
    assertEquals("generated", json.get("overallOutcome").get("type").asText());
    assertEquals(1, json.get("paths").size());
    assertEquals("loc-1", json.get("referenceData").get("locations").get("id").asText());
    assertEquals("loc-1", json.get("referenceData").get("locations").get("name").asText());
    assertEquals("mat-1", json.get("referenceData").get("materialTypes").get("id").asText());
    assertEquals("loan-1", json.get("referenceData").get("loanTypes").get("id").asText());
  }

  @Test
  public void blockedReportHasEmptyPathsAndNullReferenceDataValues() throws Exception {
    GenerationReport report = GenerationReport.of(
      "profile-1",
      "Profile One",
      "2026-05-19T20:00:00Z",
      new GenerationOutcome.BlockedUnsupportedWorkflow("match-instance-create-item", "Unsupported workflow"),
      List.of(),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, "mat-1", null)
    );

    JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(report));

    assertEquals("blocked-unsupported-workflow", json.get("overallOutcome").get("type").asText());
    assertTrue(json.get("paths").isArray());
    assertEquals(0, json.get("paths").size());
    assertTrue(json.get("referenceData").get("locations").isNull());
    assertEquals("mat-1", json.get("referenceData").get("materialTypes").get("id").asText());
    assertTrue(json.get("referenceData").get("loanTypes").isNull());
  }
}
