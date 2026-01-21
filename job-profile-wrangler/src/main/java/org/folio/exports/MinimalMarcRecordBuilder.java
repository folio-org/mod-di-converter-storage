package org.folio.exports;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.Profile;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.Leader;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds minimal MARC records from scratch with only the required fields
 * for successful Instance creation in FOLIO.
 * This class is stateless - all methods are pure functions.
 *
 * Required fields for valid Instance:
 * - Leader: establishes record type and encoding
 * - 001: control number (hrid)
 * - 008: fixed-length data element (dates, language)
 * - 245$a: title (REQUIRED)
 * - 336$b: instance type code (maps to instanceTypeId - REQUIRED)
 */
public final class MinimalMarcRecordBuilder {
  private static final Logger LOGGER = LogManager.getLogger(MinimalMarcRecordBuilder.class);
  private static final MarcFactory FACTORY = MarcFactory.newInstance();

  // Default leader for bibliographic records
  // Pos 00-04: Record length (calculated by marc4j)
  // Pos 05: Record status 'n' (new)
  // Pos 06: Type of record 'a' (language material)
  // Pos 07: Bibliographic level 'm' (monograph)
  // Pos 08: Type of control ' ' (no specific type)
  // Pos 09: Character coding scheme 'a' (UCS/Unicode)
  // Pos 10: Indicator count '2'
  // Pos 11: Subfield code count '2'
  // Pos 12-16: Base address of data (calculated by marc4j)
  // Pos 17: Encoding level ' ' (full level)
  // Pos 18: Descriptive cataloging form 'i' (ISBD punctuation included)
  // Pos 19: Multipart resource record level ' '
  // Pos 20: Length of the length-of-field portion '4'
  // Pos 21: Length of the starting-character-position portion '5'
  // Pos 22: Length of the implementation-defined portion '0'
  // Pos 23: Undefined entry map character '0'
  private static final String DEFAULT_LEADER = "00000nam a22000007i 4500";

  // Default 008 field template (40 characters)
  // Pos 00-05: Date entered on file (YYMMDD)
  // Pos 06: Type of date 's' (single known date)
  // Pos 07-10: Date 1 (publication year)
  // Pos 11-14: Date 2 (blank for single date)
  // Pos 15-17: Place of publication 'xx ' (no place, unknown)
  // Pos 18-21: Illustrations ' ' (not illustrated)
  // Pos 22: Target audience ' ' (unknown/not specified)
  // Pos 23: Form of item ' ' (none of the following)
  // Pos 24-27: Nature of contents ' ' (no specific nature)
  // Pos 28: Government publication ' ' (not a government publication)
  // Pos 29: Conference publication '0' (not a conference)
  // Pos 30: Festschrift '0' (not a festschrift)
  // Pos 31: Index '0' (no index)
  // Pos 32: Undefined ' '
  // Pos 33: Literary form '0' (not fiction)
  // Pos 34: Biography ' ' (no biographical material)
  // Pos 35-37: Language 'eng'
  // Pos 38: Modified record ' ' (not modified)
  // Pos 39: Cataloging source 'd' (other)
  private static final String FIELD_008_TEMPLATE = "%s%s%s    xx            000 0 eng d";

  // Default instance type code (text)
  private static final String DEFAULT_INSTANCE_TYPE_CODE = "txt";
  private static final String DEFAULT_INSTANCE_TYPE_TERM = "text";
  private static final String DEFAULT_INSTANCE_TYPE_SOURCE = "rdacontent";

  private MinimalMarcRecordBuilder() {
    // Private constructor - use static methods
  }

  /**
   * Result of building a MARC record, containing the record and optional report builder updates.
   */
  public record BuildResult(Record record, int recordNumber) {}

  /**
   * Builds a minimal MARC record for a given execution path through the job profile.
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPath(JobProfilePath path, int recordNumber, GenerationReport.Builder reportBuilder) {
    String pathId = path.getPathId();
    boolean verbose = reportBuilder != null;

    Record record = FACTORY.newRecord();

    // Generate unique identifier for this record
    String uuid = UUID.randomUUID().toString();
    String shortId = uuid.substring(0, 8);

    // Set Leader
    Leader leader = FACTORY.newLeader(DEFAULT_LEADER);
    record.setLeader(leader);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "Leader", DEFAULT_LEADER, "modeOfIssuanceId");
    }

    // Add 001 - Control Number
    ControlField field001 = FACTORY.newControlField("001", uuid);
    record.addVariableField(field001);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "001", uuid, "instance.hrid");
    }

    // Add 008 - Fixed-Length Data Elements
    String field008Value = generate008Field();
    ControlField field008 = FACTORY.newControlField("008", field008Value);
    record.addVariableField(field008);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "008", field008Value, "languages, dates");
    }

    // Add 245 - Title Statement (REQUIRED)
    String title = generateTitle(path, shortId);
    DataField field245 = FACTORY.newDataField("245", '1', '0');
    field245.addSubfield(FACTORY.newSubfield('a', title));
    record.addVariableField(field245);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "245$a", title, "instance.title (REQUIRED)");
    }

    // Add 336 - Content Type (maps to instanceTypeId - REQUIRED)
    DataField field336 = FACTORY.newDataField("336", ' ', ' ');
    field336.addSubfield(FACTORY.newSubfield('a', DEFAULT_INSTANCE_TYPE_TERM));
    field336.addSubfield(FACTORY.newSubfield('b', DEFAULT_INSTANCE_TYPE_CODE));
    field336.addSubfield(FACTORY.newSubfield('2', DEFAULT_INSTANCE_TYPE_SOURCE));
    record.addVariableField(field336);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "336$b", DEFAULT_INSTANCE_TYPE_CODE,
        "instance.instanceTypeId (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "336$b", DEFAULT_INSTANCE_TYPE_TERM, "instance-types");
    }

    // Log generation
    LOGGER.info("Generated minimal MARC record {} for path: {}", recordNumber, summarizePath(path));

    return new BuildResult(record, recordNumber);
  }

  /**
   * Generates the 008 fixed-length data element field.
   *
   * @return 40-character string for 008 field
   */
  private static String generate008Field() {
    LocalDate now = LocalDate.now();

    // Date entered on file (YYMMDD)
    String dateEntered = now.format(DateTimeFormatter.ofPattern("yyMMdd"));

    // Type of date and Date 1 (publication year)
    String typeOfDate = "s";
    String date1 = String.valueOf(now.getYear());

    return String.format(FIELD_008_TEMPLATE, dateEntered, typeOfDate, date1);
  }

  /**
   * Generates a descriptive title based on the job profile path.
   *
   * @param path the execution path
   * @param shortId short identifier for uniqueness
   * @return generated title string
   */
  private static String generateTitle(JobProfilePath path, String shortId) {
    // Create a title that indicates this is a test record and the path it's testing
    StringBuilder title = new StringBuilder("Test Record ");
    title.append(shortId);

    // Add path-specific info if available
    List<String> actions = extractActions(path);
    if (!actions.isEmpty()) {
      title.append(" - ");
      title.append(String.join("/", actions));
    }

    return title.toString();
  }

  /**
   * Extracts action types from the path for title generation.
   */
  private static List<String> extractActions(JobProfilePath path) {
    List<String> actions = new ArrayList<>();
    for (Profile profile : path.getProfiles()) {
      if (profile instanceof ActionProfileNode actionProfile) {
        actions.add(actionProfile.action() + " " + actionProfile.folioRecord());
      }
    }
    return actions;
  }

  /**
   * Creates a human-readable summary of a path.
   */
  private static String summarizePath(JobProfilePath path) {
    if (path.getProfiles().isEmpty()) {
      return "empty path";
    }

    List<String> parts = new ArrayList<>();
    for (Profile profile : path.getProfiles()) {
      parts.add(profile.getName());
    }
    return String.join(" -> ", parts);
  }
}
