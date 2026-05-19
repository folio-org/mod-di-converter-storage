package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GenerationReportWriterTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void reportPathIsDeterministicAndJsonParsesStrictly() throws Exception {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    GenerationReportWriter writer = new GenerationReportWriter();
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
        List.of(),
        GenerationOutcome.Generated.INSTANCE
      )),
      new MinimalMarcRecordBuilder.ReferenceDataContext("loc-1", "mat-1", "loan-1")
    );

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    Path reportPath = writer.write(outputBase, report, new PrintStream(stdout, true, StandardCharsets.UTF_8), false);

    assertEquals(outputBase.resolveSibling("records-report.json"), reportPath);
    assertTrue(Files.exists(reportPath));
    JsonNode json = OBJECT_MAPPER.readTree(Files.readString(reportPath));
    assertEquals("generated", json.get("overallOutcome").get("type").asText());
    assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Destination files:"));
  }

  @Test
  public void generatorGapSummarySaysNoFilesWereWritten() throws Exception {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    GenerationReportWriter writer = new GenerationReportWriter();
    GenerationReport report = GenerationReport.of(
      "profile-1",
      "Profile One",
      "2026-05-19T20:00:00Z",
      new GenerationOutcome.GeneratorGap(0, "Job->Create Holdings", "REFERENCE_DATA_MISSING", "Missing locations"),
      List.of(new PathOutcome(
        0,
        "Job->Create Holdings",
        "NONE",
        null,
        List.of(new PathOutcome.DestinationFile("records-import.mrc", "import")),
        List.of(new PathOutcome.FieldWritten("245$a", "Partial title", "instance.title")),
        new GenerationOutcome.GeneratorGap(0, "Job->Create Holdings", "REFERENCE_DATA_MISSING", "Missing locations")
      )),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, "mat-1", "loan-1")
    );

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    writer.write(outputBase, report, new PrintStream(stdout, true, StandardCharsets.UTF_8), true);

    String summary = stdout.toString(StandardCharsets.UTF_8);
    assertTrue(summary.contains("Generation outcome: generator-gap"));
    assertTrue(summary.contains("No MARC files were written."));
    assertTrue(summary.contains("REFERENCE_DATA_MISSING"));
    assertTrue(summary.contains("245$a"));
  }

  @Test
  public void needsEnrichmentSummaryIncludesHint() throws Exception {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    GenerationReportWriter writer = new GenerationReportWriter();
    GenerationOutcome.NeedsEnrichment outcome = new GenerationOutcome.NeedsEnrichment(
      0,
      "Job->Match Instance",
      "match-1",
      "Run jp-wrangler enrich on records-import.mrc"
    );
    GenerationReport report = GenerationReport.of(
      "profile-1",
      "Profile One",
      "2026-05-19T20:00:00Z",
      outcome,
      List.of(new PathOutcome(
        0,
        "Job->Match Instance",
        "MATCH",
        "match-1",
        List.of(new PathOutcome.DestinationFile("records-import.mrc", "import")),
        List.of(),
        outcome
      )),
      new MinimalMarcRecordBuilder.ReferenceDataContext("loc-1", "mat-1", "loan-1")
    );

    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    Path reportPath = writer.write(outputBase, report, new PrintStream(stdout, true, StandardCharsets.UTF_8), false);

    JsonNode json = OBJECT_MAPPER.readTree(Files.readString(reportPath));
    assertEquals("needs-enrichment", json.get("overallOutcome").get("type").asText());
    assertEquals("Run jp-wrangler enrich on records-import.mrc", json.get("overallOutcome").get("hint").asText());
    assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("Run jp-wrangler enrich on records-import.mrc"));
  }
}
