package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.Profile;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
  public void marcBibliographicModifyPathRemainsUnsupported() {
    try {
      MinimalMarcRecordBuilder.buildRecordForPath(path("MODIFY", "MARC_BIBLIOGRAPHIC"), 1, null, null);
      fail("Expected GeneratorGapException");
    } catch (GeneratorGapException e) {
      assertEquals(GeneratorGapException.Reason.UNSUPPORTED_ACTION, e.reason());
      assertEquals("MODIFY MARC_BIBLIOGRAPHIC", e.detail());
    }
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
