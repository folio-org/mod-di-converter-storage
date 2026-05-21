package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Record;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
  public void pairedMatchBranchIncludesHoldingsFieldsWhenNonMatchBranchCreatesHoldings() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    MatchedPathPair pair = new MatchedPathPair(
      categorized(pathWithPrefix(job, match, "CREATE", "HOLDINGS"), ReactTo.NON_MATCH, "match-1"),
      categorized(pathWithPrefix(job, match, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1"),
      "match-1"
    );

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(pair), List.of(), List.of(), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(2, result.importRecords().size());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertHoldingsLocation(result.importRecords().get(1), "location-id");
  }

  @Test
  public void pairedMatchBranchReportsGeneratorGapWhenHoldingsBranchNeedsLocation() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    MatchedPathPair pair = new MatchedPathPair(
      categorized(pathWithPrefix(job, match, "CREATE", "HOLDINGS"), ReactTo.NON_MATCH, "match-1"),
      categorized(pathWithPrefix(job, match, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1"),
      "match-1"
    );

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(pair), List.of(), List.of(), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATOR_GAP, result.overallOutcome().label());
    assertEquals(2, result.pathOutcomes().size());
    assertEquals(GenerationOutcome.GENERATOR_GAP, result.pathOutcomes().get(0).outcome().label());
    assertEquals(GenerationOutcome.GENERATOR_GAP, result.pathOutcomes().get(1).outcome().label());
    GenerationOutcome.GeneratorGap gap = (GenerationOutcome.GeneratorGap) result.overallOutcome();
    assertEquals("REFERENCE_DATA_MISSING", gap.reason());
    assertTrue(gap.message().contains("locations"));
  }

  @Test
  public void ancestorMatchBranchIncludesHoldingsFieldsWhenNonMatchSiblingCreatesHoldings() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode outerMatch = new MatchProfileNode("match-outer", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    MatchProfileNode innerMatch = new MatchProfileNode("match-inner", "STATIC_VALUE", "HOLDINGS", 0);

    CategorizedPath createInstance = categorized(pathOf(
      job,
      outerMatch,
      new ActionProfileNode("action-create-instance", "CREATE", "INSTANCE", 0),
      new MappingProfileNode("mapping-instance", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0)
    ), ReactTo.NON_MATCH, "match-outer");
    CategorizedPath createHoldings = categorized(pathOf(
      job,
      outerMatch,
      new ActionProfileNode("action-create-holdings", "CREATE", "HOLDINGS", 1),
      new MappingProfileNode("mapping-holdings", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 0)
    ), ReactTo.NON_MATCH, "match-outer");
    CategorizedPath updateHoldings = categorized(pathOf(
      job,
      outerMatch,
      innerMatch,
      new ActionProfileNode("action-update-holdings", "UPDATE", "HOLDINGS", 0),
      new MappingProfileNode("mapping-update-holdings", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 0)
    ), ReactTo.MATCH, "match-inner");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(createInstance, createHoldings), List.of(updateHoldings), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(3, result.importRecords().size());
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertHoldingsLocation(result.importRecords().get(1), "location-id");
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertHoldingsLocation(result.importRecords().get(2), "location-id");
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

  private CategorizedPath categorized(JobProfilePath path, ReactTo reactTo, String matchProfileId) {
    return new CategorizedPath(path, reactTo, matchProfileId, MatchCriteria.empty());
  }

  private void assertHoldingsLocation(Record record, String locationId) {
    DataField field852 = (DataField) record.getVariableField("852");
    assertNotNull(field852);
    assertEquals(locationId, field852.getSubfield('b').getData());
  }

  private JobProfilePath path(String action, String folioRecord) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job-1", "MARC", 0),
      new ActionProfileNode("action-1", action, folioRecord, 1),
      new MappingProfileNode("mapping-1", "MARC_BIBLIOGRAPHIC", folioRecord, 2)
    );
    return new JobProfilePath(profiles);
  }

  private JobProfilePath pathOf(Profile... profiles) {
    return new JobProfilePath(List.of(profiles));
  }

  private JobProfilePath pathWithPrefix(JobProfileNode job, MatchProfileNode match, String action, String folioRecord) {
    return pathOf(
      job,
      match,
      new ActionProfileNode("action-" + action + "-" + folioRecord, action, folioRecord, 1),
      new MappingProfileNode("mapping-" + folioRecord, "MARC_BIBLIOGRAPHIC", folioRecord, 2)
    );
  }
}
