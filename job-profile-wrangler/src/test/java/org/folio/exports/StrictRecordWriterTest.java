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
  public void matchSiblingCreateHoldingsRecordIncludesLaterItemFields() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    CategorizedPath createHoldings = categorized(
      pathWithPrefix(job, match, "CREATE", "HOLDINGS"), ReactTo.MATCH, "match-1");
    CategorizedPath createItem = categorized(
      pathWithPrefix(job, match, "CREATE", "ITEM"), ReactTo.MATCH, "match-1");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(createHoldings, createItem), List.of(), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(2, result.foundationRecords().size());
    assertEquals(2, result.importRecords().size());
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(1), "location-id");
    assertItemFields(result.importRecords().get(1));
  }

  @Test
  public void matchUpdateRecordIncludesSameBranchCreateHoldingsAndItemFields() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    CategorizedPath updateInstance = categorized(
      pathWithPrefix(job, match, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1");
    CategorizedPath createHoldings = categorized(
      pathWithPrefix(job, match, "CREATE", "HOLDINGS"), ReactTo.MATCH, "match-1");
    CategorizedPath createItem = categorized(
      pathWithPrefix(job, match, "CREATE", "ITEM"), ReactTo.MATCH, "match-1");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(createHoldings, createItem), List.of(updateInstance), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(3, result.foundationRecords().size());
    assertEquals(3, result.importRecords().size());
    assertHoldingsLocation(result.foundationRecords().get(2), "location-id");
    assertItemFields(result.foundationRecords().get(2));
    assertHoldingsLocation(result.importRecords().get(2), "location-id");
    assertItemFields(result.importRecords().get(2));
  }

  @Test
  public void unpairedUpdateHoldingsFoundationIsCompatibleWithFullInventorySeedProfile() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("UPDATE", "HOLDINGS"))), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertItemFields(result.foundationRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
  }

  @Test
  public void trailingMarcBibModifyDoesNotHideHoldingsUpdatePrerequisites() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 0);
    CategorizedPath updateHoldingsThenCleanup = categorized(pathOf(
      job,
      match,
      new ActionProfileNode("action-update-holdings", "UPDATE", "HOLDINGS", 0),
      new MappingProfileNode("mapping-update-holdings", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 0),
      new ActionProfileNode("action-modify-marc", "MODIFY", "MARC_BIBLIOGRAPHIC", 1),
      new MappingProfileNode("mapping-modify-marc", "MARC_BIBLIOGRAPHIC", "MARC_BIBLIOGRAPHIC", 0)
    ), ReactTo.MATCH, "match-1");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updateHoldingsThenCleanup), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertItemFields(result.foundationRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
  }

  @Test
  public void unpairedUpdateItemFoundationIncludesHoldingsAndItemFields() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("UPDATE", "ITEM"))), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertItemFields(result.foundationRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
  }

  @Test
  public void siblingMatchUpdatesCollapseIntoOneExecutableRecordShape() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "MARC_BIBLIOGRAPHIC", 0);
    MatchCriteria matchCriteria = new MatchCriteria("match-1",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "s", null)),
      List.of());
    CategorizedPath updateInstance = new CategorizedPath(
      pathWithPrefix(job, match, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1", matchCriteria);
    CategorizedPath updateHoldings = new CategorizedPath(
      pathWithPrefix(job, match, "UPDATE", "HOLDINGS"), ReactTo.MATCH, "match-1", matchCriteria);

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updateInstance, updateHoldings), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(2, result.pathOutcomes().size());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertItemFields(result.foundationRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
  }

  @Test
  public void rootInstanceAndItemUpdatesSharing001CollapseWithMarcCleanup() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode itemMatch = new MatchProfileNode("match-item", "MARC_BIBLIOGRAPHIC", "ITEM", 0);
    MatchProfileNode instanceMatch = new MatchProfileNode("match-instance", "MARC_BIBLIOGRAPHIC", "INSTANCE", 2);
    CategorizedPath updateItem = new CategorizedPath(
      pathWithPrefix(job, itemMatch, "UPDATE", "ITEM"), ReactTo.MATCH, "match-item",
      incoming001Criteria("match-item", "item.formerIds[]"));
    CategorizedPath cleanupMarc = categorized(path("MODIFY", "MARC_BIBLIOGRAPHIC"));
    CategorizedPath updateInstance = new CategorizedPath(
      pathWithPrefix(job, instanceMatch, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-instance",
      incoming001Criteria("match-instance", "instance.identifiers[].value.identifier-type-id"));

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updateItem, cleanupMarc, updateInstance), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(3, result.pathOutcomes().size());
    assertEquals(
      result.foundationRecords().get(0).getControlNumber(),
      result.importRecords().get(0).getControlNumber());
    assertHoldingsLocation(result.foundationRecords().get(0), "location-id");
    assertItemFields(result.foundationRecords().get(0));
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
    assertNotNull(result.importRecords().get(0).getVariableField("500"));
  }

  @Test
  public void rootCreateStackDoesNotProcessMarcCleanupAgainWithCoExecutableUpdates() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode itemMatch = new MatchProfileNode("match-item", "MARC_BIBLIOGRAPHIC", "ITEM", 0);
    MatchProfileNode instanceMatch = new MatchProfileNode("match-instance", "MARC_BIBLIOGRAPHIC", "INSTANCE", 2);
    CategorizedPath updateItem = new CategorizedPath(
      pathWithPrefix(job, itemMatch, "UPDATE", "ITEM"), ReactTo.MATCH, "match-item",
      incoming001Criteria("match-item", "item.formerIds[]"));
    CategorizedPath cleanupMarc = categorized(path("MODIFY", "MARC_BIBLIOGRAPHIC"));
    CategorizedPath updateInstance = new CategorizedPath(
      pathWithPrefix(job, instanceMatch, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-instance",
      incoming001Criteria("match-instance", "instance.identifiers[].value.identifier-type-id"));

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(
        List.of(),
        List.of(
          categorized(path("CREATE", "INSTANCE")),
          categorized(path("CREATE", "HOLDINGS")),
          categorized(path("CREATE", "ITEM"))),
        List.of(updateItem, cleanupMarc, updateInstance),
        List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(2, result.importRecords().size());
    assertEquals(6, result.pathOutcomes().size());
  }

  @Test
  public void coExecutableInventoryUpdatesCanGroupAcrossInterveningNonCoExecutableUpdate() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode itemMatch = new MatchProfileNode("match-item", "MARC_BIBLIOGRAPHIC", "ITEM", 0);
    MatchProfileNode instanceMatch = new MatchProfileNode("match-instance", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1);
    MatchProfileNode holdingsMatch = new MatchProfileNode("match-holdings", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 2);
    MatchCriteria holdingsCriteria = new MatchCriteria("match-holdings",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "s", null)),
      List.of());
    CategorizedPath updateInstance = new CategorizedPath(
      pathWithPrefix(job, instanceMatch, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-instance",
      incoming001Criteria("match-instance", "instance.identifiers[].value.identifier-type-id"));
    CategorizedPath updateHoldings = new CategorizedPath(
      pathWithPrefix(job, holdingsMatch, "UPDATE", "HOLDINGS"), ReactTo.MATCH, "match-holdings", holdingsCriteria);
    CategorizedPath updateItem = new CategorizedPath(
      pathWithPrefix(job, itemMatch, "UPDATE", "ITEM"), ReactTo.MATCH, "match-item",
      incoming001Criteria("match-item", "item.formerIds[]"));

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updateInstance, updateHoldings, updateItem), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(2, result.foundationRecords().size());
    assertEquals(2, result.importRecords().size());
    assertEquals(3, result.pathOutcomes().size());
  }

  @Test
  public void sameMatchProfileUpdateSiblingsStillCollapseWhenNoInventoryPairExists() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    MatchCriteria criteria = incoming001Criteria("match-1", "instance.identifiers[].value.identifier-type-id");
    CategorizedPath updateInstance = new CategorizedPath(
      pathWithPrefix(job, match, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1", criteria);
    CategorizedPath updateHoldings = new CategorizedPath(
      pathWithPrefix(job, match, "UPDATE", "HOLDINGS"), ReactTo.MATCH, "match-1", criteria);

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updateInstance, updateHoldings), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(1, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(2, result.pathOutcomes().size());
  }

  @Test
  public void pathOrderMatchesGroupedSiblingUpdateOutcomeOrder() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    StrictRecordWriter writer = new StrictRecordWriter();
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode firstMatch = new MatchProfileNode("match-1", "MARC_BIBLIOGRAPHIC", "MARC_BIBLIOGRAPHIC", 0);
    MatchProfileNode secondMatch = new MatchProfileNode("match-2", "MARC_BIBLIOGRAPHIC", "MARC_BIBLIOGRAPHIC", 1);
    MatchCriteria firstCriteria = new MatchCriteria("match-1",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "s", null)),
      List.of());
    MatchCriteria secondCriteria = new MatchCriteria("match-2",
      List.of(new MatchCriteria.MatchFieldSpec("001", null, null, null, null)),
      List.of());
    CategorizedPath firstUpdateInstance = new CategorizedPath(
      pathWithPrefix(job, firstMatch, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-1", firstCriteria);
    CategorizedPath secondUpdateInstance = new CategorizedPath(
      pathWithPrefix(job, secondMatch, "UPDATE", "INSTANCE"), ReactTo.MATCH, "match-2", secondCriteria);
    CategorizedPath firstUpdateHoldings = new CategorizedPath(
      pathWithPrefix(job, firstMatch, "UPDATE", "HOLDINGS"), ReactTo.MATCH, "match-1", firstCriteria);
    CategorizedPaths paths = new CategorizedPaths(
      List.of(), List.of(), List.of(firstUpdateInstance, secondUpdateInstance, firstUpdateHoldings), List.of());

    List<CategorizedPath> orderedPaths = writer.pathOrder(paths);
    StrictRecordWriter.WriteResult result = writer.write(
      paths,
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(orderedPaths.size(), result.pathOutcomes().size());
    for (int i = 0; i < orderedPaths.size(); i++) {
      assertEquals(orderedPaths.get(i).path().getPathId(), result.pathOutcomes().get(i).pathId());
      assertEquals(i, result.pathOutcomes().get(i).pathIndex());
    }
  }

  @Test
  public void explicitMatchIdDoesNotShareBranchWithNullMatchIdEvenWithCommonAncestor() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode outerMatch = new MatchProfileNode("match-outer", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0);
    MatchProfileNode innerMatch = new MatchProfileNode("match-inner", "STATIC_VALUE", "HOLDINGS", 0);
    CategorizedPath updateInstance = categorized(pathOf(
      job,
      outerMatch,
      innerMatch,
      new ActionProfileNode("action-update-instance", "UPDATE", "INSTANCE", 0),
      new MappingProfileNode("mapping-update-instance", "MARC_BIBLIOGRAPHIC", "INSTANCE", 0)
    ), ReactTo.MATCH, "match-inner");
    CategorizedPath createHoldings = categorized(pathOf(
      job,
      outerMatch,
      new ActionProfileNode("action-create-holdings", "CREATE", "HOLDINGS", 0),
      new MappingProfileNode("mapping-holdings", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 0)
    ), ReactTo.MATCH, null);

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(createHoldings), List.of(updateInstance), List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(null, result.foundationRecords().get(1).getVariableField("852"));
    assertEquals(null, result.importRecords().get(1).getVariableField("852"));
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
  public void modifyMarcBibliographicWritesFoundationAndImportRecords() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("MODIFY", "MARC_BIBLIOGRAPHIC"))), List.of()),
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
  public void rootCreateSiblingsStayGroupedWhenProfileAlsoModifiesMarcBibliographic() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(
        List.of(),
        List.of(
          categorized(path("CREATE", "INSTANCE")),
          categorized(path("CREATE", "HOLDINGS")),
          categorized(path("CREATE", "ITEM"))),
        List.of(categorized(path("MODIFY", "MARC_BIBLIOGRAPHIC"))),
        List.of()),
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id"),
      outputBase);

    assertEquals(GenerationOutcome.GENERATED, result.overallOutcome().label());
    assertEquals(0, result.foundationRecords().size());
    assertEquals(1, result.importRecords().size());
    assertEquals(4, result.pathOutcomes().size());
    assertHoldingsLocation(result.importRecords().get(0), "location-id");
    assertItemFields(result.importRecords().get(0));
    assertEquals("UPDATE TEST RECORD - Modified from original test record " +
        result.importRecords().get(0).getControlNumber().substring(0, 8),
      ((org.marc4j.marc.DataField) result.importRecords().get(0).getVariableField("500")).getSubfield('a').getData());
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
  public void matchMarcAuthorityUpdateWritesFoundationAndImportRecords() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");

    JobProfileNode job = new JobProfileNode("job-1", "MARC", 0);
    MatchProfileNode match = new MatchProfileNode("match-1", "MARC_AUTHORITY", "MARC_AUTHORITY", 0);
    CategorizedPath updatePath = categorized(
      authorityPathWithPrefix(job, match, "UPDATE", "MARC_AUTHORITY"),
      ReactTo.MATCH,
      "match-1");

    StrictRecordWriter.WriteResult result = new StrictRecordWriter().write(
      new CategorizedPaths(List.of(), List.of(), List.of(updatePath), List.of()),
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
    assertEquals('z', result.foundationRecords().get(0).getLeader().getTypeOfRecord());
    assertEquals('z', result.importRecords().get(0).getLeader().getTypeOfRecord());
    DataField heading = (DataField) result.importRecords().get(0).getVariableField("150");
    assertTrue(heading.getSubfield('a').getData().contains("Updated authority heading"));
  }


  @Test
  public void writeFailureDeletesTempsAndPreservesFinalFiles() throws IOException {
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

    assertEquals("stale foundation", Files.readString(foundation));
    assertEquals("stale import", Files.readString(importFile));
    try (var files = Files.list(temp.getRoot().toPath())) {
      assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp.")));
    }
  }

  @Test
  public void importMoveFailurePreservesMovedFoundationFile() throws IOException {
    Path outputBase = temp.getRoot().toPath().resolve("records");
    Path foundation = outputBase.resolveSibling("records-foundation.mrc");
    Path importFile = outputBase.resolveSibling("records-import.mrc");
    Files.createDirectory(importFile);
    Files.writeString(importFile.resolve("block-replace"), "directory is not replaceable by a file");

    try {
      new StrictRecordWriter().write(
        new CategorizedPaths(List.of(), List.of(), List.of(categorized(path("UPDATE", "INSTANCE"))), List.of()),
        new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null),
        outputBase);
      fail("Expected IOException");
    } catch (IOException e) {
      assertTrue(Files.exists(foundation));
      assertTrue(Files.isDirectory(importFile));
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

  private void assertItemFields(Record record) {
    DataField field945 = (DataField) record.getVariableField("945");
    assertNotNull(field945);
    assertEquals("location-id", field945.getSubfield('h').getData());
    assertEquals("material-type-id", field945.getSubfield('m').getData());
    assertEquals("loan-type-id", field945.getSubfield('t').getData());
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

  private MatchCriteria incoming001Criteria(String matchProfileId, String existingField) {
    return new MatchCriteria(matchProfileId,
      List.of(new MatchCriteria.MatchFieldSpec("001", "", "", "", null)),
      List.of(new MatchCriteria.NonMarcMatchSpec(existingField, "001", "", "", "")));
  }

  private JobProfilePath authorityPathWithPrefix(
      JobProfileNode job,
      MatchProfileNode match,
      String action,
      String folioRecord) {
    return pathOf(
      job,
      match,
      new ActionProfileNode("action-" + action + "-" + folioRecord, action, folioRecord, 1),
      new MappingProfileNode("mapping-" + folioRecord, "MARC_AUTHORITY", folioRecord, 2)
    );
  }
}
