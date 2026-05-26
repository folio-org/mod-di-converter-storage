package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;
import org.junit.Test;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MinimalMarcRecordBuilderTest {
  @Test
  public void referenceDataContextAllowsNullFields() {
    MinimalMarcRecordBuilder.ReferenceDataContext context =
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, null, null);

    assertEquals(null, context.locationId());
    assertEquals(null, context.materialTypeId());
    assertEquals(null, context.loanTypeId());
  }

  @Test
  public void instanceOnlyPathDoesNotRequireReferenceData() {
    MinimalMarcRecordBuilder.BuildResult result =
      MinimalMarcRecordBuilder.buildRecordForPath(path("CREATE", "INSTANCE"), 1, null, null);

    assertNotNull(result.record());
  }

  @Test
  public void holdingsPathThrowsGeneratorGapWhenLocationMissing() {
    MinimalMarcRecordBuilder.ReferenceDataContext refData =
      new MinimalMarcRecordBuilder.ReferenceDataContext(null, "material-type-id", "loan-type-id");

    try {
      MinimalMarcRecordBuilder.buildRecordForPath(path("CREATE", "HOLDINGS"), 1, null, refData);
      fail("Expected GeneratorGapException");
    } catch (GeneratorGapException e) {
      assertEquals(GeneratorGapException.Reason.REFERENCE_DATA_MISSING, e.reason());
      assertEquals("locations", e.detail());
    }
  }

  @Test
  public void itemPathThrowsGeneratorGapWhenMaterialTypeMissing() {
    MinimalMarcRecordBuilder.ReferenceDataContext refData =
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", null, "loan-type-id");

    try {
      MinimalMarcRecordBuilder.buildRecordForPath(path("CREATE", "ITEM"), 1, null, refData);
      fail("Expected GeneratorGapException");
    } catch (GeneratorGapException e) {
      assertEquals(GeneratorGapException.Reason.REFERENCE_DATA_MISSING, e.reason());
      assertEquals("material-types", e.detail());
    }
  }

  @Test
  public void itemUpdateVariantIncludesHoldingsFieldsForSiblingCreateHoldingsBranches() {
    MinimalMarcRecordBuilder.ReferenceDataContext refData =
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id");
    JobProfilePath itemPath = path("CREATE", "ITEM");
    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(itemPath, 1, null, refData, null,
        java.util.Set.of("HOLDINGS"));

    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBase(base.record(), itemPath, 2, null, refData, null);

    assertNotNull(update.record().getVariableField("852"));
    assertNotNull(update.record().getVariableField("945"));
  }

  @Test
  public void itemPrerequisiteAddsHoldingsFieldsEvenWhenHoldingsIsNotExplicitlyRequested() {
    MinimalMarcRecordBuilder.ReferenceDataContext refData =
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id");
    JobProfilePath instancePath = path("CREATE", "INSTANCE");

    MinimalMarcRecordBuilder.BuildResult result =
      MinimalMarcRecordBuilder.buildRecordForPathWithPrerequisites(instancePath, 1, null, refData, null,
        java.util.Set.of("ITEM"));

    assertNotNull(result.record().getVariableField("852"));
    assertNotNull(result.record().getVariableField("945"));
  }

  @Test
  public void itemUpdatePrerequisiteAddsHoldingsFieldsEvenWhenHoldingsIsNotExplicitlyRequested() {
    MinimalMarcRecordBuilder.ReferenceDataContext refData =
      new MinimalMarcRecordBuilder.ReferenceDataContext("location-id", "material-type-id", "loan-type-id");
    JobProfilePath updatePath = path("UPDATE", "INSTANCE");
    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(path("CREATE", "INSTANCE"), 1, null, null);

    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBaseWithPrerequisites(
        base.record(), updatePath, 2, null, refData, null, java.util.Set.of("ITEM"));

    assertNotNull(update.record().getVariableField("852"));
    assertNotNull(update.record().getVariableField("945"));
  }

  @Test
  public void marcBibliographicUpdatePathBuildsFoundationAndUpdateRecord() {
    JobProfilePath marcBibUpdatePath = path("UPDATE", "MARC_BIBLIOGRAPHIC");

    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(marcBibUpdatePath, 1, null, null);
    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBase(base.record(), marcBibUpdatePath, 2, null, null, null);

    assertNotNull(base.record());
    assertNotNull(update.record());
    assertEquals(base.record().getControlNumber(), update.record().getControlNumber());
    assertNotNull(update.record().getVariableField("500"));
  }

  @Test
  public void generatedBibLeaderUsesFullEncodingLevel() {
    MinimalMarcRecordBuilder.BuildResult result =
      MinimalMarcRecordBuilder.buildRecordForPath(path("CREATE", "INSTANCE"), 1, null, null);

    assertEquals(' ', result.record().getLeader().toString().charAt(17));
  }

  @Test
  public void updateRecordDoesNotDuplicateGenerated008WhenMatchCriteriaIncludes008() {
    JobProfilePath updatePath = path("UPDATE", "MARC_BIBLIOGRAPHIC");
    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(updatePath, 1, null, null);
    MatchCriteria matchCriteria = new MatchCriteria(
      "match-1",
      List.of(new MatchCriteria.MatchFieldSpec("008", "", "", "", null)),
      List.of()
    );

    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBase(base.record(), updatePath, 2, null, null, matchCriteria);

    long field008Count = update.record().getVariableFields().stream()
      .filter(ControlField.class::isInstance)
      .map(ControlField.class::cast)
      .filter(field -> "008".equals(field.getTag()))
      .count();
    assertEquals(1, field008Count);
  }

  @Test
  public void marcBibliographicModifyPathBuildsFoundationAndUpdateRecord() {
    JobProfilePath marcBibModifyPath = path("MODIFY", "MARC_BIBLIOGRAPHIC");

    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(marcBibModifyPath, 1, null, null);
    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBase(base.record(), marcBibModifyPath, 2, null, null, null);

    assertNotNull(base.record());
    assertNotNull(update.record());
    assertEquals(base.record().getControlNumber(), update.record().getControlNumber());
    assertNotNull(update.record().getVariableField("500"));
    DataField title = (DataField) update.record().getVariableField("245");
    assertTrue(title.getSubfield('a').getData().contains("MODIFY MARC_BIBLIOGRAPHIC"));
  }

  @Test
  public void inventoryModifyPathRemainsUnsupported() {
    try {
      MinimalMarcRecordBuilder.buildRecordForPath(path("MODIFY", "INSTANCE"), 1, null, null);
      fail("Expected GeneratorGapException");
    } catch (GeneratorGapException e) {
      assertEquals(GeneratorGapException.Reason.UNSUPPORTED_ACTION, e.reason());
      assertEquals("MODIFY INSTANCE", e.detail());
    }
  }

  @Test
  public void createAuthorityPathBuildsAuthorityMarcRecord() {
    MinimalMarcRecordBuilder.BuildResult result =
      MinimalMarcRecordBuilder.buildRecordForPath(authorityPath("CREATE", "AUTHORITY"), 1, null, null);

    assertEquals('z', result.record().getLeader().getTypeOfRecord());
    assertNotNull(result.record().getVariableField("001"));
    assertNotNull(result.record().getVariableField("008"));
    assertNotNull(result.record().getVariableField("010"));
    assertNotNull(result.record().getVariableField("150"));
    assertEquals(null, result.record().getVariableField("245"));
    assertEquals(null, result.record().getVariableField("336"));
    assertEquals(null, result.record().getVariableField("999"));
  }

  @Test
  public void createAuthorityPathDoesNotAddForbidden999MatchFields() {
    MatchCriteria matchCriteria = new MatchCriteria(
      "match-1",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "s", null)),
      List.of()
    );

    MinimalMarcRecordBuilder.BuildResult result =
      MinimalMarcRecordBuilder.buildRecordForPath(authorityPath("CREATE", "AUTHORITY"), 1, null, null, matchCriteria);

    assertEquals(null, result.record().getVariableField("999"));
  }


  @Test
  public void deleteAuthorityVariantPreservesControlNumberAndDoesNotInvent999() {
    JobProfilePath deletePath = authorityPath("DELETE", "MARC_AUTHORITY");
    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(deletePath, 1, null, null);

    MinimalMarcRecordBuilder.BuildResult delete =
      MinimalMarcRecordBuilder.buildDeleteRecordFromBase(base.record(), deletePath, 2, null, null, null);

    assertEquals(base.record().getControlNumber(), delete.record().getControlNumber());
    assertEquals('z', delete.record().getLeader().getTypeOfRecord());
    assertNotNull(delete.record().getVariableField("150"));
    assertEquals(null, delete.record().getVariableField("999"));
  }

  @Test
  public void updateMarcAuthorityPathBuildsAuthorityFoundationAndUpdateVariant() {
    JobProfilePath updatePath = authorityPath("UPDATE", "MARC_AUTHORITY");

    MinimalMarcRecordBuilder.BuildResult base =
      MinimalMarcRecordBuilder.buildRecordForPath(updatePath, 1, null, null);
    MinimalMarcRecordBuilder.BuildResult update =
      MinimalMarcRecordBuilder.buildUpdateRecordFromBase(base.record(), updatePath, 2, null, null, null);

    assertEquals(base.record().getControlNumber(), update.record().getControlNumber());
    assertEquals('z', update.record().getLeader().getTypeOfRecord());
    assertNotNull(update.record().getVariableField("010"));
    assertNotNull(update.record().getVariableField("040"));
    assertEquals(null, update.record().getVariableField("245"));
    assertEquals(null, update.record().getVariableField("336"));
    DataField heading = (DataField) update.record().getVariableField("150");
    assertTrue(heading.getSubfield('a').getData().contains("Updated authority heading"));
  }

  private JobProfilePath path(String action, String folioRecord) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job-1", "MARC", 0),
      new ActionProfileNode("action-1", action, folioRecord, 1),
      new MappingProfileNode("mapping-1", "MARC_BIBLIOGRAPHIC", folioRecord, 2)
    );
    return new JobProfilePath(profiles);
  }

  private JobProfilePath authorityPath(String action, String folioRecord) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job-1", "MARC", 0),
      new ActionProfileNode("action-1", action, folioRecord, 1),
      new MappingProfileNode("mapping-1", "MARC_AUTHORITY", folioRecord, 2)
    );
    return new JobProfilePath(profiles);
  }
}
