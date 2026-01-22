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
 * for successful Instance, Holdings, and Item creation in FOLIO.
 * This class is stateless - all methods are pure functions.
 *
 * Required fields for valid Instance:
 * - Leader: establishes record type and encoding
 * - 001: control number (hrid)
 * - 008: fixed-length data element (dates, language)
 * - 245$a: title (REQUIRED)
 * - 336$b: instance type code (maps to instanceTypeId - REQUIRED)
 *
 * Required fields for Holdings (when path creates HOLDINGS):
 * - 852$b: permanentLocationId (REQUIRED)
 * - 852$h: callNumber (optional)
 *
 * Required fields for Items (when path creates ITEM):
 * - 945$h: permanentLocation.id (REQUIRED)
 * - 945$a: status.name (REQUIRED - default "Available")
 * - 945$m: materialType.id (REQUIRED)
 * - 945$t: permanentLoanType.id (REQUIRED)
 * - 945$b: barcode (optional)
 *
 * <p><strong>Note:</strong> The 852 and 945 field mappings assume the FOLIO tenant has
 * mapping profiles configured to map these subfields to Holdings/Item fields. The
 * subfield-to-field mappings shown above must match the tenant's mapping profile
 * configuration. See {@code MappingDetails.mappingFields} in the mapping profile for
 * the actual field mappings used.
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

  // Default item status
  private static final String DEFAULT_ITEM_STATUS = "Available";

  private MinimalMarcRecordBuilder() {
  }

  /**
   * Context containing reference data UUIDs needed for Holdings and Items.
   * These UUIDs are fetched from the FOLIO tenant's reference data.
   *
   * @param locationId UUID from /locations endpoint
   * @param materialTypeId UUID from /material-types endpoint
   * @param loanTypeId UUID from /loan-types endpoint
   */
  public record ReferenceDataContext(
      String locationId,
      String materialTypeId,
      String loanTypeId
  ) {
    /**
     * Creates a ReferenceDataContext, validating that required fields are present.
     * @throws IllegalArgumentException if any required field is null or empty
     */
    public ReferenceDataContext {
      if (locationId == null || locationId.isBlank()) {
        throw new IllegalArgumentException("locationId is required");
      }
      if (materialTypeId == null || materialTypeId.isBlank()) {
        throw new IllegalArgumentException("materialTypeId is required");
      }
      if (loanTypeId == null || loanTypeId.isBlank()) {
        throw new IllegalArgumentException("loanTypeId is required");
      }
    }
  }

  /**
   * Result of building a MARC record, containing the record and optional report builder updates.
   */
  public record BuildResult(Record record, int recordNumber) {}

  /**
   * Builds a minimal MARC record for a given execution path through the job profile.
   * This overload does not include Holdings/Item fields.
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPath(JobProfilePath path, int recordNumber, GenerationReport.Builder reportBuilder) {
    return buildRecordForPath(path, recordNumber, reportBuilder, null);
  }

  /**
   * Builds a minimal MARC record for a given execution path through the job profile.
   * When refData is provided and the path creates Holdings/Items, appropriate MARC fields are added.
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPath(JobProfilePath path, int recordNumber,
      GenerationReport.Builder reportBuilder, ReferenceDataContext refData) {
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

    // Add Holdings and Item fields if reference data is available and path creates them
    if (refData != null) {
      boolean createsHoldings = pathCreatesRecordType(path, "HOLDINGS");
      boolean createsItems = pathCreatesRecordType(path, "ITEM");

      if (createsHoldings || createsItems) {
        // Add 852 for holdings
        if (createsHoldings) {
          String callNumber = "TEST " + shortId;
          addHoldingsFields(record, refData, callNumber, pathId, reportBuilder);
        }

        // Add 945 for items (with all required fields)
        if (createsItems) {
          String barcode = "TEST-" + shortId;
          addItemFields(record, refData, barcode, pathId, reportBuilder);
        }
      }
    }

    // Log generation
    LOGGER.info("Generated minimal MARC record {} for path: {}", recordNumber, summarizePath(path));

    return new BuildResult(record, recordNumber);
  }

  /**
   * Builds an UPDATE variant of a MARC record based on an existing base record.
   * The UPDATE record preserves the 001 (control number) from the base record so it will
   * MATCH during import, while modifying the title and adding a note to distinguish it.
   * This overload does not include Holdings/Item fields.
   *
   * @param baseRecord the foundation record whose 001 should be preserved
   * @param path the job profile execution path (for UPDATE action)
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @return a BuildResult containing the update variant MARC record
   */
  public static BuildResult buildUpdateRecordFromBase(
      Record baseRecord,
      JobProfilePath path,
      int recordNumber,
      GenerationReport.Builder reportBuilder) {
    return buildUpdateRecordFromBase(baseRecord, path, recordNumber, reportBuilder, null);
  }

  /**
   * Builds an UPDATE variant of a MARC record based on an existing base record.
   * The UPDATE record preserves the 001 (control number) from the base record so it will
   * MATCH during import, while modifying the title and adding a note to distinguish it.
   * When refData is provided and the path creates Holdings/Items, appropriate MARC fields are added.
   *
   * @param baseRecord the foundation record whose 001 should be preserved
   * @param path the job profile execution path (for UPDATE action)
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @return a BuildResult containing the update variant MARC record
   */
  public static BuildResult buildUpdateRecordFromBase(
      Record baseRecord,
      JobProfilePath path,
      int recordNumber,
      GenerationReport.Builder reportBuilder,
      ReferenceDataContext refData) {

    String pathId = path.getPathId();
    boolean verbose = reportBuilder != null;

    Record record = FACTORY.newRecord();

    // Extract the 001 from the base record to preserve for matching
    String originalUuid = baseRecord.getControlNumber();
    String shortId = originalUuid.substring(0, 8);

    // Set Leader (same as base)
    Leader leader = FACTORY.newLeader(DEFAULT_LEADER);
    record.setLeader(leader);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "Leader", DEFAULT_LEADER, "modeOfIssuanceId");
    }

    // Add 001 - Control Number (PRESERVED from base record for MATCH)
    ControlField field001 = FACTORY.newControlField("001", originalUuid);
    record.addVariableField(field001);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "001", originalUuid, "instance.hrid (PRESERVED for MATCH)");
    }

    // Add 008 - Fixed-Length Data Elements
    String field008Value = generate008Field();
    ControlField field008 = FACTORY.newControlField("008", field008Value);
    record.addVariableField(field008);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "008", field008Value, "languages, dates");
    }

    // Add 245 - Title Statement with "UPDATED:" prefix
    String title = generateUpdateTitle(path, shortId);
    DataField field245 = FACTORY.newDataField("245", '1', '0');
    field245.addSubfield(FACTORY.newSubfield('a', title));
    record.addVariableField(field245);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "245$a", title, "instance.title (MODIFIED for UPDATE)");
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
    }

    // Add 500 - General Note to mark this as an update test record
    String note = "UPDATE TEST RECORD - Modified from original test record " + shortId;
    DataField field500 = FACTORY.newDataField("500", ' ', ' ');
    field500.addSubfield(FACTORY.newSubfield('a', note));
    record.addVariableField(field500);
    if (verbose) {
      reportBuilder.addFieldGeneration(pathId, "500$a", note, "instance.notes (UPDATE marker)");
    }

    // Add Holdings and Item fields if reference data is available and path creates them
    if (refData != null) {
      boolean createsHoldings = pathCreatesRecordType(path, "HOLDINGS");
      boolean createsItems = pathCreatesRecordType(path, "ITEM");

      if (createsHoldings || createsItems) {
        // Add 852 for holdings
        if (createsHoldings) {
          String callNumber = "TEST " + shortId + " UPDATED";
          addHoldingsFields(record, refData, callNumber, pathId, reportBuilder);
        }

        // Add 945 for items (with all required fields)
        if (createsItems) {
          String barcode = "TEST-" + shortId + "-UPD";
          addItemFields(record, refData, barcode, pathId, reportBuilder);
        }
      }
    }

    // Log generation
    LOGGER.info("Generated UPDATE variant MARC record {} for path: {}", recordNumber, summarizePath(path));

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
   * Generates a title for UPDATE variant records with "UPDATED:" prefix.
   *
   * @param path the execution path (UPDATE action)
   * @param shortId short identifier matching the base record
   * @return generated title string for update record
   */
  private static String generateUpdateTitle(JobProfilePath path, String shortId) {
    StringBuilder title = new StringBuilder("UPDATED: Test Record ");
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
      parts.add(ProfileDisplayUtils.getProfileDisplayName(profile));
    }
    return String.join(" -> ", parts);
  }

  /**
   * Checks if the path contains an action profile that creates the specified record type.
   *
   * @param path the job profile execution path
   * @param recordType the FOLIO record type to check for (e.g., "HOLDINGS", "ITEM")
   * @return true if the path creates the specified record type
   */
  private static boolean pathCreatesRecordType(JobProfilePath path, String recordType) {
    for (Profile profile : path.getProfiles()) {
      if (profile instanceof ActionProfileNode actionProfile) {
        if ("CREATE".equals(actionProfile.action()) && recordType.equals(actionProfile.folioRecord())) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Adds Holdings fields (852) to the MARC record.
   * Field 852 is the standard Location/Call Number field.
   *
   * @param record the MARC record to modify
   * @param refData reference data context containing valid UUIDs
   * @param callNumber the call number to use (optional)
   * @param pathId the path identifier for reporting
   * @param reportBuilder optional report builder for verbose output
   */
  private static void addHoldingsFields(Record record, ReferenceDataContext refData,
      String callNumber, String pathId, GenerationReport.Builder reportBuilder) {
    DataField field852 = FACTORY.newDataField("852", ' ', ' ');
    // $b - Location (permanentLocationId) - REQUIRED
    field852.addSubfield(FACTORY.newSubfield('b', refData.locationId()));
    // $h - Call number
    if (callNumber != null && !callNumber.isBlank()) {
      field852.addSubfield(FACTORY.newSubfield('h', callNumber));
    }
    record.addVariableField(field852);

    if (reportBuilder != null) {
      reportBuilder.addFieldGeneration(pathId, "852$b", refData.locationId(),
          "holdings.permanentLocationId (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "852$b", refData.locationId(), "locations");
      if (callNumber != null && !callNumber.isBlank()) {
        reportBuilder.addFieldGeneration(pathId, "852$h", callNumber, "holdings.callNumber");
      }
    }

    LOGGER.debug("Added Holdings fields (852) with locationId: {}", refData.locationId());
  }

  /**
   * Adds Item fields (945) to the MARC record.
   * Field 945 is a local/institutional field used for item data.
   * Each occurrence of 945 creates one item - this leverages FOLIO's mapping profile
   * capability where a repeatable MARC field can generate multiple inventory records
   * (configured via {@code MappingDetails.mappingFields[].repeatableFieldAction = 'EXTEND_EXISTING'}).
   *
   * @param record the MARC record to modify
   * @param refData reference data context containing valid UUIDs
   * @param barcode the item barcode (should be unique)
   * @param pathId the path identifier for reporting
   * @param reportBuilder optional report builder for verbose output
   */
  private static void addItemFields(Record record, ReferenceDataContext refData,
      String barcode, String pathId, GenerationReport.Builder reportBuilder) {
    DataField field945 = FACTORY.newDataField("945", ' ', ' ');
    // $h - Location UUID (permanentLocation.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('h', refData.locationId()));
    // $b - Barcode (should be unique)
    if (barcode != null && !barcode.isBlank()) {
      field945.addSubfield(FACTORY.newSubfield('b', barcode));
    }
    // $a - Status (status.name) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('a', DEFAULT_ITEM_STATUS));
    // $m - Material type UUID (materialType.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('m', refData.materialTypeId()));
    // $t - Loan type UUID (permanentLoanType.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('t', refData.loanTypeId()));
    record.addVariableField(field945);

    if (reportBuilder != null) {
      reportBuilder.addFieldGeneration(pathId, "945$h", refData.locationId(),
          "item.permanentLocation.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$h", refData.locationId(), "locations");
      if (barcode != null && !barcode.isBlank()) {
        reportBuilder.addFieldGeneration(pathId, "945$b", barcode, "item.barcode");
      }
      reportBuilder.addFieldGeneration(pathId, "945$a", DEFAULT_ITEM_STATUS,
          "item.status.name (REQUIRED)");
      reportBuilder.addFieldGeneration(pathId, "945$m", refData.materialTypeId(),
          "item.materialType.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$m", refData.materialTypeId(), "material-types");
      reportBuilder.addFieldGeneration(pathId, "945$t", refData.loanTypeId(),
          "item.permanentLoanType.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$t", refData.loanTypeId(), "loan-types");
    }

    LOGGER.debug("Added Item fields (945) with locationId: {}, materialTypeId: {}, loanTypeId: {}",
        refData.locationId(), refData.materialTypeId(), refData.loanTypeId());
  }
}
