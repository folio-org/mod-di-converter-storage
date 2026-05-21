package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class StrictRecordWriterTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void generatedRunWritesImportFileAndRemovesStaleFoundationFile() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");
    Files.writeString(foundation, "stale");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(categorized(path("CREATE", "INSTANCE"))), List.of(), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertFalse(Files.exists(foundation));
    assertTrue(Files.exists(importFile));
    assertEquals(1, result.importRecords().size());
    assertEquals(1, result.pathOutcomes().size());
    assertEquals("import", result.pathOutcomes().get(0).destinationFiles().get(0).role());
  }

  @Test
  public void missingLocationForHoldingsClassifiesGeneratorGapAndWritesNoFiles() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(categorized(path("CREATE", "HOLDINGS"))), List.of(), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATOR_GAP, result.overallOutcome().label());
    assertFalse(Files.exists(foundation));
    assertFalse(Files.exists(importFile));
    assertEquals(1, result.pathOutcomes().size());
    assertEquals(GenerationOutcome.GENERATOR_GAP, result.pathOutcomes().get(0).outcome().label());
    GenerationOutcome.GeneratorGap gap = (GenerationOutcome.GeneratorGap) result.overallOutcome();
    assertEquals("REFERENCE_DATA_MISSING", gap.reason());
    assertTrue(gap.message().contains("locations"));
  }

  @Test
  public void updateMarcBibliographicWritesFoundationAndImportRecords() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("UPDATE", "MARC_BIBLIOGRAPHIC"))), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertTrue(Files.exists(foundation));
    assertTrue(Files.exists(importFile));
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(
      result.foundationRecords().get(0).getControlNumber(),
      result.importRecords().get(0).getControlNumber());
  }

  @Test
  public void deleteMarcAuthorityWritesFoundationAndImportRecords() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(), List.of(categorized(path("DELETE", "MARC_AUTHORITY")))),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(
      result.foundationRecords().get(0).getControlNumber(),
      result.importRecords().get(0).getControlNumber());
    assertEquals('z', result.importRecords().get(0).getLeader().getTypeOfRecord());
  }


  @Test
  public void writeFailureDeletesTempsAndStaleFinalFiles() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");
    Files.writeString(foundation, "stale foundation");
    Files.writeString(importFile, "stale import");

    int[] writes = {0};
    StrictRecordWriter writer = new StrictRecordWriter((path, records) -> {
      writes[0]++;
      if (writes[0] == 2) {
        throw new IOException("simulated second-file failure");
      }
      Files.writeString(path, "temporary marc bytes");
    });

    try {
      writer.write(
        new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("UPDATE", "INSTANCE"))), List.of()),
        new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null),
        outputBase);
      fail("Expected IOException");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("simulated"));
    }

    assertFalse(Files.exists(foundation));
    assertFalse(Files.exists(importFile));
    try (var files = Files.list(temp.getRoot().toPath())) {
      assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp.")));
    }
  }

  private CategorizedPath categorized(JobProfilePath path) {
    return new CategorizedPath(path, ReactTo.NONE, null, MatchCriteria.empty());
  }

  private JobProfilePath path(String action, String folioRecord) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job-1", "MARC", 0),
      new ActionProfileNode("action-1", action, folioRecord, 1),
      new MappingProfileNode("mapping-1", "MARC_BIBLIOGRAPHIC", folioRecord, 2)
    );
    return new JobProfilePath(profiles);
  }
}
