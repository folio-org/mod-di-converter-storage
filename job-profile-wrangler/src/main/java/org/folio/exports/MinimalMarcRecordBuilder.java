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
 * This class uses only static methods. Note that some methods (ISBN, ISSN,
 * OCLC number generation) use randomization and are not deterministic.
 *
 * <p>Required fields for valid Instance:
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

  // Random instance for generating test values (shared for efficiency)
  private static final java.util.Random RANDOM = new java.util.Random();

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
  ) {}

  /**
   * Result of building a MARC record, containing the record and its sequence number.
   *
   * @param record the generated MARC record
   * @param recordNumber the sequence number of this record (1-based)
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
    return buildRecordForPath(path, recordNumber, reportBuilder, null, null);
  }

  /**
   * Builds a minimal MARC record for a given execution path through the job profile.
   * This overload includes Holdings/Item fields but no match criteria.
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPath(JobProfilePath path, int recordNumber,
      GenerationReport.Builder reportBuilder, ReferenceDataContext refData) {
    return buildRecordForPath(path, recordNumber, reportBuilder, refData, null);
  }

  /**
   * Builds a minimal MARC record for a given execution path through the job profile.
   * When refData is provided and the path creates Holdings/Items, appropriate MARC fields are added.
   * When matchCriteria is provided, appropriate match fields are generated.
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @param matchCriteria optional match criteria for generating match fields (may be null)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPath(JobProfilePath path, int recordNumber,
      GenerationReport.Builder reportBuilder, ReferenceDataContext refData, MatchCriteria matchCriteria) {
    return buildRecordForPathWithPrerequisites(path, recordNumber, reportBuilder, refData, matchCriteria, java.util.Set.of());
  }

  /**
   * Builds a minimal MARC record for a given execution path, including fields for prerequisite entities.
   * This method ensures foundation records include all entities needed for a target entity to be created.
   *
   * <p>For example, when creating an ITEM via "MATCH INSTANCE -> CREATE ITEM", the foundation record
   * must create Instance + Holdings (not just Instance) because Items require Holdings to exist.
   * The Holdings is linked to Items via permanentLocationId matching (both use the same locationId).
   *
   * @param path the job profile execution path
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @param matchCriteria optional match criteria for generating match fields (may be null)
   * @param additionalEntities entities that must be included beyond what the path explicitly creates
   *                           (e.g., Set.of("HOLDINGS") when creating ITEM)
   * @return a BuildResult containing the valid minimal MARC record
   */
  public static BuildResult buildRecordForPathWithPrerequisites(JobProfilePath path, int recordNumber,
      GenerationReport.Builder reportBuilder, ReferenceDataContext refData, MatchCriteria matchCriteria,
      java.util.Set<String> additionalEntities) {
    String pathId = path.getPathId();
    boolean verbose = reportBuilder != null;
    validateSupportedActions(path);

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

    // Determine what entities need fields: path-created entities + additional prerequisites
    boolean createsHoldings = pathCreatesRecordType(path, "HOLDINGS");
    boolean createsItems = pathCreatesRecordType(path, "ITEM");
    boolean needsHoldingsFields = createsHoldings ||
        (additionalEntities != null && additionalEntities.contains("HOLDINGS"));
    boolean needsItemFields = createsItems ||
        (additionalEntities != null && additionalEntities.contains("ITEM"));

    String callNumber = "TEST " + shortId;

    // Add 852 for holdings - either path creates Holdings OR Holdings is a prerequisite
    // CRITICAL: Use the same locationId for both Holdings and Items to enable Item-Holdings linkage
    if (needsHoldingsFields) {
      addHoldingsFields(record, requireRefData(refData), callNumber, pathId, reportBuilder);
      if (verbose && additionalEntities != null && additionalEntities.contains("HOLDINGS") && !createsHoldings) {
        LOGGER.info("Added Holdings fields (852) as prerequisite for Item creation");
      }
    }

    // Add 945 for items (with all required fields)
    // CRITICAL: 945$h uses the same refData.locationId() as 852$b for permanentLocationId matching
    if (needsItemFields) {
      String barcode = "TEST-" + shortId;
      addItemFields(record, requireRefData(refData), barcode, pathId, reportBuilder);
    }

    // Add match fields from match criteria (if any)
    if (matchCriteria != null && !matchCriteria.isEmpty()) {
      addMatchFields(record, matchCriteria, pathId, reportBuilder);
    }

    // Log generation with prerequisites info
    if (additionalEntities != null && !additionalEntities.isEmpty()) {
      LOGGER.info("Generated minimal MARC record {} for path: {} (with prerequisites: {})",
        recordNumber, summarizePath(path), additionalEntities);
    } else {
      LOGGER.info("Generated minimal MARC record {} for path: {}", recordNumber, summarizePath(path));
    }

    return new BuildResult(record, recordNumber);
  }

  /**
   * Builds an UPDATE variant of a MARC record based on an existing base record.
   * The UPDATE record preserves the 001 (control number) from the base record so it will
   * MATCH during import, while modifying the title and adding a note to distinguish it.
   * This overload does not include Holdings/Item fields or match criteria.
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
    return buildUpdateRecordFromBase(baseRecord, path, recordNumber, reportBuilder, null, null);
  }

  /**
   * Builds an UPDATE variant of a MARC record based on an existing base record.
   * The UPDATE record preserves the 001 (control number) from the base record so it will
   * MATCH during import, while modifying the title and adding a note to distinguish it.
   * This overload includes Holdings/Item fields but no match criteria.
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
    return buildUpdateRecordFromBase(baseRecord, path, recordNumber, reportBuilder, refData, null);
  }

  /**
   * Builds an UPDATE variant of a MARC record based on an existing base record.
   * The UPDATE record preserves the 001 (control number) from the base record so it will
   * MATCH during import, while modifying the title and adding a note to distinguish it.
   * When refData is provided and the path creates Holdings/Items, appropriate MARC fields are added.
   * When matchCriteria is provided, match fields are preserved from the base record.
   *
   * @param baseRecord the foundation record whose 001 should be preserved
   * @param path the job profile execution path (for UPDATE action)
   * @param recordNumber the record number (1-based)
   * @param reportBuilder optional report builder for verbose output (may be null)
   * @param refData optional reference data context for Holdings/Item fields (may be null)
   * @param matchCriteria optional match criteria for preserving match fields (may be null)
   * @return a BuildResult containing the update variant MARC record
   */
  public static BuildResult buildUpdateRecordFromBase(
      Record baseRecord,
      JobProfilePath path,
      int recordNumber,
      GenerationReport.Builder reportBuilder,
      ReferenceDataContext refData,
      MatchCriteria matchCriteria) {

    String pathId = path.getPathId();
    boolean verbose = reportBuilder != null;
    validateSupportedActions(path);

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

    boolean createsHoldings = pathCreatesRecordType(path, "HOLDINGS");
    boolean createsItems = pathCreatesRecordType(path, "ITEM");

    if (createsHoldings || createsItems) {
      String callNumber = "TEST " + shortId + " UPDATED";
      addHoldingsFields(record, requireRefData(refData), callNumber, pathId, reportBuilder);
    }

    if (createsItems) {
      String barcode = "TEST-" + shortId + "-UPD";
      addItemFields(record, requireRefData(refData), barcode, pathId, reportBuilder);
    }

    // Preserve match fields from base record (for UPDATE records to match)
    if (matchCriteria != null && !matchCriteria.isEmpty()) {
      preserveMatchFields(baseRecord, record, matchCriteria, pathId, reportBuilder);
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
   * Extracts "action folioRecord" pairs from action profiles in the path.
   * For example: ["CREATE INSTANCE", "CREATE HOLDINGS", "CREATE ITEM"]
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

  private static void validateSupportedActions(JobProfilePath path) {
    for (Profile profile : path.getProfiles()) {
      if (profile instanceof ActionProfileNode actionProfile) {
        boolean supportedAction = "CREATE".equals(actionProfile.action()) || "UPDATE".equals(actionProfile.action());
        boolean supportedRecord = "INSTANCE".equals(actionProfile.folioRecord())
            || "HOLDINGS".equals(actionProfile.folioRecord())
            || "ITEM".equals(actionProfile.folioRecord());
        if (!supportedAction || !supportedRecord) {
          throw GeneratorGapException.unsupportedAction(actionProfile.action(), actionProfile.folioRecord());
        }
      }
    }
  }

  private static ReferenceDataContext requireRefData(ReferenceDataContext refData) {
    if (refData == null) {
      throw GeneratorGapException.referenceDataMissing("locations");
    }
    return refData;
  }

  private static String requireReferenceValue(String value, String refType) {
    if (value == null || value.isBlank()) {
      throw GeneratorGapException.referenceDataMissing(refType);
    }
    return value;
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
    String locationId = requireReferenceValue(refData.locationId(), "locations");
    DataField field852 = FACTORY.newDataField("852", ' ', ' ');
    // $b - Location (permanentLocationId) - REQUIRED
    field852.addSubfield(FACTORY.newSubfield('b', locationId));
    // $h - Call number
    if (callNumber != null && !callNumber.isBlank()) {
      field852.addSubfield(FACTORY.newSubfield('h', callNumber));
    }
    record.addVariableField(field852);

    if (reportBuilder != null) {
      reportBuilder.addFieldGeneration(pathId, "852$b", locationId,
          "holdings.permanentLocationId (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "852$b", locationId, "locations");
      if (callNumber != null && !callNumber.isBlank()) {
        reportBuilder.addFieldGeneration(pathId, "852$h", callNumber, "holdings.callNumber");
      }
    }

    LOGGER.debug("Added Holdings fields (852) with locationId: {}", locationId);
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
    String locationId = requireReferenceValue(refData.locationId(), "locations");
    String materialTypeId = requireReferenceValue(refData.materialTypeId(), "material-types");
    String loanTypeId = requireReferenceValue(refData.loanTypeId(), "loan-types");
    DataField field945 = FACTORY.newDataField("945", ' ', ' ');
    // $h - Location UUID (permanentLocation.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('h', locationId));
    // $b - Barcode (should be unique)
    if (barcode != null && !barcode.isBlank()) {
      field945.addSubfield(FACTORY.newSubfield('b', barcode));
    }
    // $a - Status (status.name) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('a', DEFAULT_ITEM_STATUS));
    // $m - Material type UUID (materialType.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('m', materialTypeId));
    // $t - Loan type UUID (permanentLoanType.id) - REQUIRED
    field945.addSubfield(FACTORY.newSubfield('t', loanTypeId));
    record.addVariableField(field945);

    if (reportBuilder != null) {
      reportBuilder.addFieldGeneration(pathId, "945$h", locationId,
          "item.permanentLocation.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$h", locationId, "locations");
      if (barcode != null && !barcode.isBlank()) {
        reportBuilder.addFieldGeneration(pathId, "945$b", barcode, "item.barcode");
      }
      reportBuilder.addFieldGeneration(pathId, "945$a", DEFAULT_ITEM_STATUS,
          "item.status.name (REQUIRED)");
      reportBuilder.addFieldGeneration(pathId, "945$m", materialTypeId,
          "item.materialType.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$m", materialTypeId, "material-types");
      reportBuilder.addFieldGeneration(pathId, "945$t", loanTypeId,
          "item.permanentLoanType.id (REQUIRED)");
      reportBuilder.addReferenceData(pathId, "945$t", loanTypeId, "loan-types");
    }

    LOGGER.debug("Added Item fields (945) with locationId: {}, materialTypeId: {}, loanTypeId: {}",
        locationId, materialTypeId, loanTypeId);
  }

  /**
   * Adds MARC fields required for matching based on the match criteria.
   * Generates appropriate test values for each field type.
   *
   * @param record the MARC record to add match fields to
   * @param matchCriteria the match criteria containing field specifications
   * @param pathId the path identifier for reporting
   * @param reportBuilder optional report builder for verbose output
   */
  private static void addMatchFields(Record record, MatchCriteria matchCriteria,
      String pathId, GenerationReport.Builder reportBuilder) {
    if (matchCriteria == null || !matchCriteria.hasMarcMatches()) {
      return;
    }

    boolean verbose = reportBuilder != null;

    for (MatchCriteria.MatchFieldSpec spec : matchCriteria.matchFields()) {
      // Skip if field already exists (e.g., 001 is always generated)
      if (fieldExists(record, spec)) {
        LOGGER.debug("Skipping match field {} - already exists in record", spec.fieldTag());
        continue;
      }

      String value = spec.hasStaticValue() ? spec.staticValue() : generateMatchValue(spec);

      if (spec.isControlField()) {
        ControlField controlField = FACTORY.newControlField(spec.fieldTag(), value);
        record.addVariableField(controlField);
        if (verbose) {
          reportBuilder.addFieldGeneration(pathId, spec.fieldTag(), value, "match field (control)");
        }
      } else {
        char ind1 = spec.indicator1() != null && !spec.indicator1().isEmpty()
            ? spec.indicator1().charAt(0) : ' ';
        char ind2 = spec.indicator2() != null && !spec.indicator2().isEmpty()
            ? spec.indicator2().charAt(0) : ' ';
        char subfield = spec.subfieldCode() != null && !spec.subfieldCode().isEmpty()
            ? spec.subfieldCode().charAt(0) : 'a';

        DataField dataField = FACTORY.newDataField(spec.fieldTag(), ind1, ind2);
        dataField.addSubfield(FACTORY.newSubfield(subfield, value));
        record.addVariableField(dataField);

        String fieldDesc = spec.fieldTag() + "$" + subfield;
        if (verbose) {
          reportBuilder.addFieldGeneration(pathId, fieldDesc, value, "match field (data)");
        }
      }

      LOGGER.debug("Added match field {}: {}", spec.fieldTag(), value);
    }
  }

  /**
   * Preserves match fields from a base record to an update record.
   * Copies values from the base record so the update record will match.
   *
   * @param baseRecord the foundation record to copy values from
   * @param updateRecord the update record to add match fields to
   * @param matchCriteria the match criteria containing field specifications
   * @param pathId the path identifier for reporting
   * @param reportBuilder optional report builder for verbose output
   */
  private static void preserveMatchFields(Record baseRecord, Record updateRecord,
      MatchCriteria matchCriteria, String pathId, GenerationReport.Builder reportBuilder) {
    if (matchCriteria == null || !matchCriteria.hasMarcMatches()) {
      return;
    }

    boolean verbose = reportBuilder != null;

    for (MatchCriteria.MatchFieldSpec spec : matchCriteria.matchFields()) {
      // Skip 001 - already handled separately
      if ("001".equals(spec.fieldTag())) {
        continue;
      }

      // Extract value from base record
      String value = extractValueFromRecord(baseRecord, spec);
      if (value == null) {
        LOGGER.debug("No value found in base record for match field {}", spec.fieldTag());
        continue;
      }

      if (spec.isControlField()) {
        ControlField controlField = FACTORY.newControlField(spec.fieldTag(), value);
        updateRecord.addVariableField(controlField);
        if (verbose) {
          reportBuilder.addFieldGeneration(pathId, spec.fieldTag(), value, "match field (PRESERVED for MATCH)");
        }
      } else {
        char ind1 = spec.indicator1() != null && !spec.indicator1().isEmpty()
            ? spec.indicator1().charAt(0) : ' ';
        char ind2 = spec.indicator2() != null && !spec.indicator2().isEmpty()
            ? spec.indicator2().charAt(0) : ' ';
        char subfield = spec.subfieldCode() != null && !spec.subfieldCode().isEmpty()
            ? spec.subfieldCode().charAt(0) : 'a';

        DataField dataField = FACTORY.newDataField(spec.fieldTag(), ind1, ind2);
        dataField.addSubfield(FACTORY.newSubfield(subfield, value));
        updateRecord.addVariableField(dataField);

        String fieldDesc = spec.fieldTag() + "$" + subfield;
        if (verbose) {
          reportBuilder.addFieldGeneration(pathId, fieldDesc, value, "match field (PRESERVED for MATCH)");
        }
      }

      LOGGER.debug("Preserved match field {} from base record: {}", spec.fieldTag(), value);
    }
  }

  /**
   * Generates an appropriate test value for a match field based on the field type.
   *
   * @param spec the match field specification
   * @return a generated value appropriate for the field type
   */
  private static String generateMatchValue(MatchCriteria.MatchFieldSpec spec) {
    String tag = spec.fieldTag();

    return switch (tag) {
      case "001" -> UUID.randomUUID().toString();
      case "020" -> generateIsbn();
      case "022" -> generateIssn();
      case "035" -> generateOclcNumber();
      case "999" -> UUID.randomUUID().toString();
      default -> "TEST-" + UUID.randomUUID().toString().substring(0, 8);
    };
  }

  /**
   * Generates a syntactically valid ISBN-13 with proper check digit.
   * Uses the Bookland prefix 978 followed by 9 random digits and a calculated check digit.
   *
   * @return a 13-digit ISBN-13 (e.g., "9780123456789")
   */
  private static String generateIsbn() {
    StringBuilder isbn = new StringBuilder("978");
    for (int i = 0; i < 9; i++) {
      isbn.append(RANDOM.nextInt(10));
    }
    // Calculate check digit
    int sum = 0;
    for (int i = 0; i < 12; i++) {
      int digit = isbn.charAt(i) - '0';
      sum += (i % 2 == 0) ? digit : digit * 3;
    }
    int checkDigit = (10 - (sum % 10)) % 10;
    isbn.append(checkDigit);
    return isbn.toString();
  }

  /**
   * Generates a syntactically valid ISSN with proper check digit.
   * Format is ####-###X where X is a check digit (0-9 or 'X' for 10).
   *
   * @return a 9-character ISSN including hyphen (e.g., "1234-567X")
   */
  private static String generateIssn() {
    StringBuilder issn = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      issn.append(RANDOM.nextInt(10));
    }
    issn.append("-");
    for (int i = 0; i < 3; i++) {
      issn.append(RANDOM.nextInt(10));
    }
    // Calculate check digit
    int sum = 0;
    String digits = issn.toString().replace("-", "");
    for (int i = 0; i < 7; i++) {
      sum += (digits.charAt(i) - '0') * (8 - i);
    }
    int checkDigit = (11 - (sum % 11)) % 11;
    issn.append(checkDigit == 10 ? "X" : checkDigit);
    return issn.toString();
  }

  /**
   * Generates an OCLC control number format.
   *
   * @return an OCLC number in (OCoLC) format
   */
  private static String generateOclcNumber() {
    StringBuilder oclc = new StringBuilder("(OCoLC)");
    for (int i = 0; i < 9; i++) {
      oclc.append(RANDOM.nextInt(10));
    }
    return oclc.toString();
  }

  /**
   * Checks if a field matching the spec already exists in the record.
   *
   * @param record the MARC record to check
   * @param spec the field specification to look for
   * @return true if a matching field exists
   */
  private static boolean fieldExists(Record record, MatchCriteria.MatchFieldSpec spec) {
    if (spec.isControlField()) {
      return record.getVariableField(spec.fieldTag()) != null;
    } else {
      List<DataField> fields = record.getDataFields().stream()
          .filter(f -> f.getTag().equals(spec.fieldTag()))
          .toList();

      for (DataField field : fields) {
        // Check indicators if specified
        if (spec.indicator1() != null && !spec.indicator1().isEmpty()) {
          if (field.getIndicator1() != spec.indicator1().charAt(0)) {
            continue;
          }
        }
        if (spec.indicator2() != null && !spec.indicator2().isEmpty()) {
          if (field.getIndicator2() != spec.indicator2().charAt(0)) {
            continue;
          }
        }
        // Check subfield exists
        if (spec.subfieldCode() != null && !spec.subfieldCode().isEmpty()) {
          if (field.getSubfield(spec.subfieldCode().charAt(0)) != null) {
            return true;
          }
        } else {
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Extracts a value from a record for the given field specification.
   *
   * @param record the MARC record to extract from
   * @param spec the field specification to look for
   * @return the extracted value or null if not found
   */
  private static String extractValueFromRecord(Record record, MatchCriteria.MatchFieldSpec spec) {
    if (spec.isControlField()) {
      ControlField field = (ControlField) record.getVariableField(spec.fieldTag());
      return field != null ? field.getData() : null;
    } else {
      for (DataField field : record.getDataFields()) {
        if (!field.getTag().equals(spec.fieldTag())) {
          continue;
        }
        // Check indicators if specified
        if (spec.indicator1() != null && !spec.indicator1().isEmpty()) {
          if (field.getIndicator1() != spec.indicator1().charAt(0)) {
            continue;
          }
        }
        if (spec.indicator2() != null && !spec.indicator2().isEmpty()) {
          if (field.getIndicator2() != spec.indicator2().charAt(0)) {
            continue;
          }
        }
        // Get subfield value
        char subfieldCode = spec.subfieldCode() != null && !spec.subfieldCode().isEmpty()
            ? spec.subfieldCode().charAt(0) : 'a';
        var subfield = field.getSubfield(subfieldCode);
        if (subfield != null) {
          return subfield.getData();
        }
      }
      return null;
    }
  }
}
