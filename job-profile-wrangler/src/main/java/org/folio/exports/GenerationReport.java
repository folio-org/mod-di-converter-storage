package org.folio.exports;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable v1 report for a generate command run.
 */
public record GenerationReport(
  String profileId,
  String profileName,
  String runTimestamp,
  GenerationOutcome overallOutcome,
  List<PathOutcome> paths,
  ReferenceData referenceData
) {
  public GenerationReport {
    paths = List.copyOf(paths);
  }

  public record ReferenceData(
    ReferenceDataValue locations,
    ReferenceDataValue materialTypes,
    ReferenceDataValue loanTypes
  ) {}

  public record ReferenceDataValue(
    String id,
    String name
  ) {}

  public static GenerationReport of(
      String profileId,
      String profileName,
      String runTimestamp,
      GenerationOutcome overallOutcome,
      List<PathOutcome> paths,
      MinimalMarcRecordBuilder.ReferenceDataContext referenceData) {
    return new GenerationReport(
      profileId,
      profileName,
      runTimestamp,
      overallOutcome,
      paths == null ? List.of() : paths,
      referenceData(referenceData)
    );
  }

  public static ReferenceData referenceData(MinimalMarcRecordBuilder.ReferenceDataContext context) {
    if (context == null) {
      return new ReferenceData(null, null, null);
    }
    return new ReferenceData(
      value(context.locationId()),
      value(context.materialTypeId()),
      value(context.loanTypeId())
    );
  }

  private static ReferenceDataValue value(String id) {
    return id == null || id.isBlank() ? null : new ReferenceDataValue(id, id);
  }

  /**
   * Compatibility collector for older call sites that still pass a report builder
   * into MinimalMarcRecordBuilder. New generate wiring uses StrictRecordWriter
   * with a null builder, but keeping this API avoids broad churn in this unit.
   */
  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private final List<PathOutcome.FieldWritten> fieldsWritten = new ArrayList<>();

    private Builder() {}

    public Builder outputPath(String outputPath) {
      return this;
    }

    public Builder addFieldGeneration(String pathId, String fieldTag, String value, String mapsTo) {
      fieldsWritten.add(new PathOutcome.FieldWritten(fieldTag, value, mapsTo));
      return this;
    }

    public Builder addReferenceData(String pathId, String fieldTag, String displayName, String referenceType) {
      return this;
    }

    public Builder startNewRecord() {
      return this;
    }

    public List<PathOutcome.FieldWritten> fieldsWritten() {
      return List.copyOf(fieldsWritten);
    }

    public GenerationReport build() {
      return new GenerationReport(
        null,
        null,
        null,
        GenerationOutcome.Generated.INSTANCE,
        List.of(),
        new ReferenceData(null, null, null)
      );
    }
  }
}
