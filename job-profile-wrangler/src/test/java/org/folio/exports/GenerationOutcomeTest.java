package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GenerationOutcomeTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void labelReturnsStableKebabCaseValues() {
    assertEquals("generated", GenerationOutcome.Generated.INSTANCE.label());
    assertEquals("blocked-unsupported-workflow",
      new GenerationOutcome.BlockedUnsupportedWorkflow("MATCH_INSTANCE_CREATE_ITEM", "Unsupported workflow").label());
    assertEquals("needs-enrichment",
      new GenerationOutcome.NeedsEnrichment(0, "Job->Match", "match-1", "Run enrich").label());
    assertEquals("generator-gap",
      new GenerationOutcome.GeneratorGap(1, "Job->Create", "REFERENCE_DATA_MISSING", "Missing location").label());
    assertEquals("invalid-profile-shape",
      new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "No execution paths found").label());
  }

  @Test
  public void serializationIncludesTypeDiscriminatorAndVariantFields() throws Exception {
    assertSerializedType(GenerationOutcome.Generated.INSTANCE, "generated");

    JsonNode blocked = assertSerializedType(
      new GenerationOutcome.BlockedUnsupportedWorkflow("MATCH_INSTANCE_CREATE_ITEM", "Unsupported workflow"),
      "blocked-unsupported-workflow");
    assertEquals("MATCH_INSTANCE_CREATE_ITEM", blocked.get("rule").asText());
    assertEquals("Unsupported workflow", blocked.get("message").asText());

    JsonNode enrichment = assertSerializedType(
      new GenerationOutcome.NeedsEnrichment(2, "Job->Match", "match-1", "Run enrich"),
      "needs-enrichment");
    assertEquals(2, enrichment.get("pathIndex").asInt());
    assertEquals("match-1", enrichment.get("matchProfileId").asText());
    assertEquals("Run enrich", enrichment.get("hint").asText());

    JsonNode gap = assertSerializedType(
      new GenerationOutcome.GeneratorGap(3, "Job->Create", "REFERENCE_DATA_MISSING", "Missing location"),
      "generator-gap");
    assertEquals("REFERENCE_DATA_MISSING", gap.get("reason").asText());
    assertEquals("Missing location", gap.get("message").asText());

    JsonNode invalid = assertSerializedType(
      new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "No execution paths found"),
      "invalid-profile-shape");
    assertEquals("EMPTY_PATH", invalid.get("reason").asText());
    assertEquals("No execution paths found", invalid.get("message").asText());
  }

  @Test
  public void needsEnrichmentRendersStructuredRecordNumberInHint() {
    GenerationOutcome.NeedsEnrichment outcome = new GenerationOutcome.NeedsEnrichment(
      0,
      "Path",
      "match-1",
      "Run enrich --skip-missing",
      3);

    assertEquals(3, outcome.importRecordNumber().intValue());
    assertEquals("Run enrich --record-number 3 --skip-missing", outcome.hint());
  }

  @Test
  public void pathOutcomeSerializationAlwaysIncludesDestinationFilesList() throws Exception {
    PathOutcome generated = new PathOutcome(
      0,
      "Job->Create Instance",
      "MATCH",
      "match-1",
      List.of(
        new PathOutcome.DestinationFile("records-foundation.mrc", "foundation"),
        new PathOutcome.DestinationFile("records-import.mrc", "import")
      ),
      List.of(new PathOutcome.FieldWritten("245", "Sample title", "instance.title")),
      GenerationOutcome.Generated.INSTANCE
    );

    JsonNode generatedJson = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(generated));
    assertTrue(generatedJson.has("destinationFiles"));
    assertEquals(2, generatedJson.get("destinationFiles").size());
    assertEquals("generated", generatedJson.get("outcome").get("type").asText());

    PathOutcome blocked = new PathOutcome(
      1,
      "Job->Create Item",
      "NON_MATCH",
      "match-2",
      List.of(),
      List.of(),
      new GenerationOutcome.BlockedUnsupportedWorkflow("MATCH_INSTANCE_CREATE_ITEM", "Unsupported workflow")
    );

    JsonNode blockedJson = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(blocked));
    assertTrue(blockedJson.has("destinationFiles"));
    assertTrue(blockedJson.get("destinationFiles").isArray());
    assertEquals(0, blockedJson.get("destinationFiles").size());
    assertEquals("blocked-unsupported-workflow", blockedJson.get("outcome").get("type").asText());
  }

  @Test
  public void recordsHaveValueBasedEqualsAndHashCode() {
    assertRecordEquality(GenerationOutcome.Generated.INSTANCE, new GenerationOutcome.Generated());
    assertRecordEquality(
      new GenerationOutcome.BlockedUnsupportedWorkflow("RULE", "Message"),
      new GenerationOutcome.BlockedUnsupportedWorkflow("RULE", "Message"));
    assertRecordEquality(
      new GenerationOutcome.NeedsEnrichment(0, "Path", "match-1", "Hint"),
      new GenerationOutcome.NeedsEnrichment(0, "Path", "match-1", "Hint"));
    assertRecordEquality(
      new GenerationOutcome.GeneratorGap(1, "Path", "REASON", "Message"),
      new GenerationOutcome.GeneratorGap(1, "Path", "REASON", "Message"));
    assertRecordEquality(
      new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "Message"),
      new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "Message"));

    PathOutcome pathOutcome = new PathOutcome(
      0,
      "Path",
      "MATCH",
      "match-1",
      List.of(new PathOutcome.DestinationFile("records-import.mrc", "import")),
      List.of(new PathOutcome.FieldWritten("245", "Title", "instance.title")),
      GenerationOutcome.Generated.INSTANCE
    );
    PathOutcome samePathOutcome = new PathOutcome(
      0,
      "Path",
      "MATCH",
      "match-1",
      List.of(new PathOutcome.DestinationFile("records-import.mrc", "import")),
      List.of(new PathOutcome.FieldWritten("245", "Title", "instance.title")),
      GenerationOutcome.Generated.INSTANCE
    );

    assertRecordEquality(pathOutcome, samePathOutcome);
    assertFalse(pathOutcome.equals(new PathOutcome(
      1,
      "Path",
      "MATCH",
      "match-1",
      List.of(),
      List.of(),
      GenerationOutcome.Generated.INSTANCE
    )));
  }

  private JsonNode assertSerializedType(GenerationOutcome outcome, String expectedType) throws Exception {
    JsonNode json = OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsString(outcome));
    assertTrue(json.has("type"));
    assertEquals(expectedType, json.get("type").asText());
    return json;
  }

  private void assertRecordEquality(Object left, Object right) {
    assertEquals(left, right);
    assertEquals(left.hashCode(), right.hashCode());
  }
}
