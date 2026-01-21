package org.folio.exports;

import java.io.PrintStream;
import java.util.*;

/**
 * Tracks and reports field generation details for verbose output.
 * Provides a detailed report of which MARC fields were generated
 * and what inventory fields they map to.
 * This class is immutable - use the Builder to construct instances.
 */
public final class GenerationReport {

  /**
   * Represents a single field generation entry.
   */
  public record FieldEntry(
    String fieldTag,
    String value,
    String mapsTo
  ) {}

  /**
   * Represents reference data usage.
   */
  public record ReferenceEntry(
    String fieldTag,
    String displayName,
    String referenceType
  ) {}

  /**
   * Represents a complete record generation report.
   */
  public record RecordReport(
    String pathId,
    List<FieldEntry> fields,
    List<ReferenceEntry> referenceData
  ) {
    public RecordReport {
      fields = List.copyOf(fields);
      referenceData = List.copyOf(referenceData);
    }
  }

  private final List<RecordReport> recordReports;
  private final String outputPath;

  private GenerationReport(Builder builder) {
    this.recordReports = List.copyOf(builder.recordReports);
    this.outputPath = builder.outputPath;
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Gets the number of records in the report.
   *
   * @return record count
   */
  public int getRecordCount() {
    return recordReports.size();
  }

  /**
   * Gets all record reports.
   *
   * @return collection of record reports
   */
  public List<RecordReport> getRecordReports() {
    return recordReports;
  }

  /**
   * Gets the output path.
   *
   * @return the output path or null if not set
   */
  public String getOutputPath() {
    return outputPath;
  }

  /**
   * Prints the report to standard output.
   */
  public void print() {
    print(System.out);
  }

  /**
   * Prints the report to the specified output stream.
   *
   * @param out the output stream
   */
  public void print(PrintStream out) {
    out.println();
    out.println("=== MARC Record Generation Report ===");
    out.println();

    if (recordReports.isEmpty()) {
      out.println("No records generated.");
      return;
    }

    int recordNum = 0;
    for (RecordReport report : recordReports) {
      recordNum++;
      out.println("--- Record " + recordNum + " ---");
      out.println("Path: " + formatPathId(report.pathId()));
      out.println();
      out.println("Generated Fields:");

      for (FieldEntry field : report.fields()) {
        out.println("  " + field.fieldTag() + ": " + truncateValue(field.value(), 50));
        out.println("      Maps to: " + field.mapsTo());
        out.println();
      }

      if (!report.referenceData().isEmpty()) {
        out.println("Reference Data Used:");
        for (ReferenceEntry ref : report.referenceData()) {
          out.println("  " + ref.fieldTag() + ": \"" + ref.displayName() + "\" from " + ref.referenceType());
        }
        out.println();
      }
    }

    if (outputPath != null) {
      out.println("Output: " + outputPath + " (" + recordReports.size() + " record" +
        (recordReports.size() > 1 ? "s" : "") + ")");
    }
  }

  private String formatPathId(String pathId) {
    return pathId.replace("->", " -> ");
  }

  private String truncateValue(String value, int maxLength) {
    if (value == null) {
      return "null";
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3) + "...";
  }

  /**
   * Builder for constructing GenerationReport instances.
   */
  public static final class Builder {
    private final List<RecordReport> recordReports = new ArrayList<>();
    private String outputPath;

    // Current path being built
    private String currentPathId;
    private final List<FieldEntry> currentFields = new ArrayList<>();
    private final List<ReferenceEntry> currentReferenceData = new ArrayList<>();

    private Builder() {}

    /**
     * Sets the output file path for the report.
     *
     * @param outputPath the output file path
     * @return this builder
     */
    public Builder outputPath(String outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    /**
     * Adds a field generation entry for the current path.
     *
     * @param pathId the path identifier
     * @param fieldTag the MARC field tag
     * @param value the generated value
     * @param mapsTo the inventory field it maps to
     * @return this builder
     */
    public Builder addFieldGeneration(String pathId, String fieldTag, String value, String mapsTo) {
      ensurePath(pathId);
      currentFields.add(new FieldEntry(fieldTag, value, mapsTo));
      return this;
    }

    /**
     * Adds a reference data entry for the current path.
     *
     * @param pathId the path identifier
     * @param fieldTag the MARC field tag
     * @param displayName the display name from reference data
     * @param referenceType the type of reference data
     * @return this builder
     */
    public Builder addReferenceData(String pathId, String fieldTag, String displayName, String referenceType) {
      ensurePath(pathId);
      currentReferenceData.add(new ReferenceEntry(fieldTag, displayName, referenceType));
      return this;
    }

    /**
     * Ensures the path is tracked and commits the previous path if different.
     */
    private void ensurePath(String pathId) {
      if (currentPathId == null) {
        currentPathId = pathId;
      } else if (!currentPathId.equals(pathId)) {
        commitCurrentPath();
        currentPathId = pathId;
      }
    }

    /**
     * Commits the current path's data to the report.
     */
    private void commitCurrentPath() {
      if (currentPathId != null && !currentFields.isEmpty()) {
        recordReports.add(new RecordReport(
          currentPathId,
          new ArrayList<>(currentFields),
          new ArrayList<>(currentReferenceData)
        ));
      }
      currentFields.clear();
      currentReferenceData.clear();
    }

    /**
     * Builds an immutable GenerationReport instance.
     * Commits any pending path data before building.
     *
     * @return the built GenerationReport
     */
    public GenerationReport build() {
      commitCurrentPath();
      return new GenerationReport(this);
    }
  }
}
