package org.folio.hydration;

import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.RepeatableSubfieldMapping;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Factory for creating default mappingDetails for different record types.
 * Field lists match ui-data-import/src/settings/MappingProfiles/initialDetails/*.js
 * Empty values use SRM's default marc-bib translation rules.
 */
public final class MappingDetailsFactory {

  private MappingDetailsFactory() {
    // Utility class
  }

  /**
   * Creates default mappingDetails for INSTANCE record type.
   * Field list matches ui-data-import/src/settings/MappingProfiles/initialDetails/INSTANCE.js
   */
  public static MappingDetail createInstanceMappingDetails() {
    return new MappingDetail()
      .withName("instance")
      .withRecordType(EntityType.INSTANCE)
      .withMappingFields(Arrays.asList(
        // Administrative fields
        createField("discoverySuppress", "instance.discoverySuppress", true),
        createField("staffSuppress", "instance.staffSuppress", true),
        createField("previouslyHeld", "instance.previouslyHeld", true),
        createField("hrid", "instance.hrid", false),
        createField("source", "instance.source", false),
        createField("catalogedDate", "instance.catalogedDate", true),
        createField("statusId", "instance.statusId", true),
        createField("modeOfIssuanceId", "instance.modeOfIssuanceId", false),

        // Statistical codes
        createFieldWithSubfields("statisticalCodeIds", "instance.statisticalCodeIds[]", true,
          createSubfield(0, "instance.statisticalCodeIds[]",
            createSubfieldField("statisticalCodeId", "instance.statisticalCodeIds[]", true))),

        // Administrative notes
        createFieldWithSubfields("administrativeNotes", "instance.administrativeNotes[]", true,
          createSubfield(0, "instance.administrativeNotes[]",
            createSubfieldField("administrativeNote", "instance.administrativeNotes[]", true))),

        // Title fields
        createField("title", "instance.title", false),
        createFieldWithSubfields("alternativeTitles", "instance.alternativeTitles[]", false,
          createSubfield(0, "instance.alternativeTitles[]",
            createSubfieldField("alternativeTitleTypeId", "instance.alternativeTitles[].alternativeTitleTypeId", false),
            createSubfieldField("alternativeTitle", "instance.alternativeTitles[].alternativeTitle", false))),
        createField("indexTitle", "instance.indexTitle", false),

        // Series
        createFieldWithSubfields("series", "instance.series[]", false,
          createSubfield(0, "instance.series[]",
            createSubfieldField("source", "instance.series[]", false))),

        // Preceding titles
        createFieldWithSubfields("precedingTitles", "instance.precedingTitles[]", false,
          createSubfield(0, "instance.precedingTitles[]",
            createSubfieldField("precedingTitlesTitle", "instance.precedingTitles[].title", true),
            createSubfieldField("precedingTitlesHrid", "instance.precedingTitles[].hrid", true),
            createSubfieldField("precedingTitlesIsbn", "instance.precedingTitles[].identifiers[].value", true),
            createSubfieldField("precedingTitlesIssn", "instance.precedingTitles[].identifiers[].value", true))),

        // Succeeding titles
        createFieldWithSubfields("succeedingTitles", "instance.succeedingTitles[]", false,
          createSubfield(0, "instance.succeedingTitles[]",
            createSubfieldField("succeedingTitlesTitle", "instance.succeedingTitles[].title", true),
            createSubfieldField("succeedingTitlesHrid", "instance.succeedingTitles[].hrid", true),
            createSubfieldField("succeedingTitlesIsbn", "instance.succeedingTitles[].identifiers[].value", true),
            createSubfieldField("succeedingTitlesIssn", "instance.succeedingTitles[].identifiers[].value", true))),

        // Identifiers
        createFieldWithSubfields("identifiers", "instance.identifiers[]", false,
          createSubfield(0, "instance.identifiers[]",
            createSubfieldField("identifierTypeId", "instance.identifiers[].identifierTypeId", false),
            createSubfieldField("value", "instance.identifiers[].value", false))),

        // Contributors
        createFieldWithSubfields("contributors", "instance.contributors[]", false,
          createSubfield(0, "instance.contributors[]",
            createSubfieldField("contributorName", "instance.contributors[].name", false),
            createSubfieldField("contributorNameTypeId", "instance.contributors[].contributorNameTypeId", false),
            createSubfieldField("contributorTypeId", "instance.contributors[].contributorTypeId", false),
            createSubfieldField("contributorTypeText", "instance.contributors[].contributorTypeText", false),
            createSubfieldField("primary", "instance.contributors[].primary", false))),

        // Publication
        createFieldWithSubfields("publication", "instance.publication[]", false,
          createSubfield(0, "instance.publication[]",
            createSubfieldField("publisher", "instance.publication[].publisher", false),
            createSubfieldField("role", "instance.publication[].role", false),
            createSubfieldField("place", "instance.publication[].place", false),
            createSubfieldField("dateOfPublication", "instance.publication[].dateOfPublication", false))),

        // Editions
        createFieldWithSubfields("editions", "instance.editions[]", false,
          createSubfield(0, "instance.editions[]",
            createSubfieldField("edition", "instance.editions[]", false))),

        // Physical descriptions
        createFieldWithSubfields("physicalDescriptions", "instance.physicalDescriptions[]", false,
          createSubfield(0, "instance.physicalDescriptions[]",
            createSubfieldField("physicalDescription", "instance.physicalDescriptions[]", false))),

        // Type fields
        createField("instanceTypeId", "instance.instanceTypeId", false),
        createFieldWithSubfields("natureOfContentTermIds", "instance.natureOfContentTermIds[]", true,
          createSubfield(0, "instance.natureOfContentTermIds[]",
            createSubfieldField("natureOfContentTermId", "instance.natureOfContentTermIds[]", true))),
        createFieldWithSubfields("instanceFormatIds", "instance.instanceFormatIds[]", false,
          createSubfield(0, "instance.instanceFormatIds[]",
            createSubfieldField("instanceFormatId", "instance.instanceFormatIds[]", false))),
        createFieldWithSubfields("languages", "instance.languages[]", false,
          createSubfield(0, "instance.languages[]",
            createSubfieldField("languageId", "instance.languages[]", false))),

        // Publication frequency and range
        createFieldWithSubfields("publicationFrequency", "instance.publicationFrequency[]", false,
          createSubfield(0, "instance.publicationFrequency[]",
            createSubfieldField("publicationFrequency", "instance.publicationFrequency[]", false))),
        createFieldWithSubfields("publicationRange", "instance.publicationRange[]", false,
          createSubfield(0, "instance.publicationRange[]",
            createSubfieldField("publicationRange", "instance.publicationRange[]", false))),

        // Notes
        createFieldWithSubfields("notes", "instance.notes[]", false,
          createSubfield(0, "instance.notes[]",
            createSubfieldField("noteType", "instance.notes[].instanceNoteTypeId", false),
            createSubfieldField("note", "instance.notes[].note", false),
            createSubfieldField("staffOnly", "instance.notes[].staffOnly", false))),

        // Electronic access
        createFieldWithSubfields("electronicAccess", "instance.electronicAccess[]", false,
          createSubfield(0, "instance.electronicAccess[]",
            createSubfieldField("relationshipId", "instance.electronicAccess[].relationshipId", false),
            createSubfieldField("uri", "instance.electronicAccess[].uri", false),
            createSubfieldField("linkText", "instance.electronicAccess[].linkText", false),
            createSubfieldField("materialsSpecification", "instance.electronicAccess[].materialsSpecification", false),
            createSubfieldField("publicNote", "instance.electronicAccess[].publicNote", false))),

        // Subjects
        createFieldWithSubfields("subjects", "instance.subjects[]", false,
          createSubfield(0, "instance.subjects[]",
            createSubfieldField("subject", "instance.subjects[]", false))),

        // Classifications
        createFieldWithSubfields("classifications", "instance.classifications[]", false,
          createSubfield(0, "instance.classifications[]",
            createSubfieldField("classificationTypeId", "instance.classifications[].classificationTypeId", false),
            createSubfieldField("classificationNumber", "instance.classifications[].classificationNumber", false))),

        // Parent instances
        createFieldWithSubfields("parentInstances", "instance.parentInstances[]", true,
          createSubfield(0, "instance.parentInstances[]",
            createSubfieldField("superInstanceId", "instance.parentInstances[].superInstanceId", true),
            createSubfieldField("instanceRelationshipTypeId", "instance.parentInstances[].instanceRelationshipTypeId", true))),

        // Child instances
        createFieldWithSubfields("childInstances", "instance.childInstances[]", true,
          createSubfield(0, "instance.childInstances[]",
            createSubfieldField("subInstanceId", "instance.childInstances[].subInstanceId", true),
            createSubfieldField("instanceRelationshipTypeId", "instance.childInstances[].instanceRelationshipTypeId", true)))
      ));
  }

  /**
   * Creates default mappingDetails for HOLDINGS record type.
   * Field list matches ui-data-import/src/settings/MappingProfiles/initialDetails/HOLDINGS.js
   */
  public static MappingDetail createHoldingsMappingDetails() {
    return new MappingDetail()
      .withName("holdings")
      .withRecordType(EntityType.HOLDINGS)
      .withMappingFields(Arrays.asList(
        createField("discoverySuppress", "holdings.discoverySuppress", true),
        createField("hrid", "holdings.discoverySuppress", false),

        // Former IDs
        createFieldWithSubfields("formerIds", "holdings.formerIds[]", true,
          createSubfield(0, "holdings.formerIds[]",
            createSubfieldField("formerId", "holdings.formerIds[]", true, "004"))),

        createField("holdingsTypeId", "holdings.holdingsTypeId", true, "\"Monograph\""),

        // Statistical codes
        createFieldWithSubfields("statisticalCodeIds", "holdings.statisticalCodeIds[]", true,
          createSubfield(0, "holdings.statisticalCodeIds[]",
            createSubfieldField("statisticalCodeId", "holdings.statisticalCodeIds[]", true))),

        // Administrative notes
        createFieldWithSubfields("administrativeNotes", "holdings.administrativeNotes[]", true,
          createSubfield(0, "holdings.administrativeNotes[]",
            createSubfieldField("administrativeNote", "holdings.administrativeNotes[]", true))),

        // Location
        createField("permanentLocationId", "holdings.permanentLocationId", true, "852$b"),
        createField("temporaryLocationId", "holdings.temporaryLocationId", true),
        createField("shelvingOrder", "holdings.shelvingOrder", true),
        createField("shelvingTitle", "holdings.shelvingTitle", true),
        createField("copyNumber", "holdings.copyNumber", true),

        // Call number
        createField("callNumberTypeId", "holdings.callNumberTypeId", true),
        createField("callNumberPrefix", "holdings.callNumberPrefix", true, "852$k"),
        createField("callNumber", "holdings.callNumber", true, "852$h"),
        createField("callNumberSuffix", "holdings.callNumberSuffix", true, "852$m"),
        createField("numberOfItems", "holdings.numberOfItems", true),

        // Holdings statements
        createFieldWithSubfields("holdingsStatements", "holdings.holdingsStatements[]", true,
          createSubfield(0, "holdings.holdingsStatements[]",
            createSubfieldField("statement", "holdings.holdingsStatements[].statement", true),
            createSubfieldField("note", "holdings.holdingsStatements[].note", true),
            createSubfieldField("staffNote", "holdings.holdingsStatements[].staffNote", true))),

        // Holdings statements for supplements
        createFieldWithSubfields("holdingsStatementsForSupplements", "holdings.holdingsStatementsForSupplements[]", true,
          createSubfield(0, "holdings.holdingsStatementsForSupplements[]",
            createSubfieldField("statement", "holdings.holdingsStatementsForSupplements[].statement", true),
            createSubfieldField("note", "holdings.holdingsStatementsForSupplements[].note", true),
            createSubfieldField("staffNote", "holdings.holdingsStatementsForSupplements[].staffNote", true))),

        // Holdings statements for indexes
        createFieldWithSubfields("holdingsStatementsForIndexes", "holdings.holdingsStatementsForIndexes[]", true,
          createSubfield(0, "holdings.holdingsStatementsForIndexes[]",
            createSubfieldField("statement", "holdings.holdingsStatementsForIndexes[].statement", true),
            createSubfieldField("note", "holdings.holdingsStatementsForIndexes[].note", true),
            createSubfieldField("staffNote", "holdings.holdingsStatementsForIndexes[].staffNote", true))),

        // Policies
        createField("illPolicyId", "holdings.illPolicyId", true),
        createField("digitizationPolicy", "holdings.digitizationPolicy", true),
        createField("retentionPolicy", "holdings.retentionPolicy", true),

        // Notes
        createFieldWithSubfields("notes", "holdings.notes[]", true,
          createSubfield(0, "holdings.notes[]",
            createSubfieldFieldRequired("noteType", "holdings.notes[].holdingsNoteTypeId", true, true),
            createSubfieldFieldRequired("note", "holdings.notes[].note", true, true),
            createSubfieldField("staffOnly", "holdings.notes[].staffOnly", true))),

        // Electronic access
        createFieldWithSubfields("electronicAccess", "holdings.electronicAccess[]", true,
          createSubfield(0, "holdings.electronicAccess[]",
            createSubfieldField("relationshipId", "holdings.electronicAccess[].relationshipId", true),
            createSubfieldFieldRequired("uri", "holdings.electronicAccess[].uri", true, true),
            createSubfieldField("linkText", "holdings.electronicAccess[].linkText", true),
            createSubfieldField("materialsSpecification", "holdings.electronicAccess[].materialsSpecification", true),
            createSubfieldField("publicNote", "holdings.electronicAccess[].publicNote", true))),

        // Receiving history
        createFieldWithSubfields("receivingHistory.entries", "holdings.receivingHistory.entries[]", true,
          createSubfield(0, "holdings.receivingHistory.entries[]",
            createSubfieldField("publicDisplay", "holdings.receivingHistory.entries[].publicDisplay", true),
            createSubfieldField("enumeration", "holdings.receivingHistory.entries[].enumeration", true),
            createSubfieldField("chronology", "holdings.receivingHistory.entries[].chronology", true)))
      ));
  }

  /**
   * Creates default mappingDetails for ITEM record type.
   * Field list matches ui-data-import/src/settings/MappingProfiles/initialDetails/ITEM.js
   */
  public static MappingDetail createItemMappingDetails() {
    return new MappingDetail()
      .withName("item")
      .withRecordType(EntityType.ITEM)
      .withMappingFields(Arrays.asList(
        createField("discoverySuppress", "item.discoverySuppress", true),
        createField("hrid", "item.hrid", true),
        createField("barcode", "item.barcode", true, "945$b"),
        createField("accessionNumber", "item.accessionNumber", true),
        createField("itemIdentifier", "item.itemIdentifier", true),

        // Former IDs
        createFieldWithSubfields("formerIds", "item.formerIds[]", true,
          createSubfield(0, "item.formerIds[]",
            createSubfieldField("formerId", "item.formerIds[]", true))),

        // Statistical codes
        createFieldWithSubfields("statisticalCodeIds", "item.statisticalCodeIds[]", true,
          createSubfield(0, "item.statisticalCodeIds[]",
            createSubfieldField("statisticalCodeId", "item.statisticalCodeIds[]", true))),

        // Administrative notes
        createFieldWithSubfields("administrativeNotes", "item.administrativeNotes[]", true,
          createSubfield(0, "item.administrativeNotes[]",
            createSubfieldField("administrativeNote", "item.administrativeNotes[]", true))),

        // Material type
        createField("materialType.id", "item.materialType.id", true, "945$m"),
        createField("copyNumber", "item.copyNumber", true),

        // Call number
        createField("itemLevelCallNumberTypeId", "item.itemLevelCallNumberTypeId", true),
        createField("itemLevelCallNumberPrefix", "item.itemLevelCallNumberPrefix", true),
        createField("itemLevelCallNumber", "item.itemLevelCallNumber", true),
        createField("itemLevelCallNumberSuffix", "item.itemLevelCallNumberSuffix", true),

        // Pieces
        createField("numberOfPieces", "item.numberOfPieces", true),
        createField("descriptionOfPieces", "item.descriptionOfPieces", true),

        // Enumeration and chronology
        createField("enumeration", "item.enumeration", true),
        createField("chronology", "item.chronology", true),
        createField("volume", "item.volume", true),

        // Year caption
        createFieldWithSubfields("yearCaption", "item.yearCaption[]", true,
          createSubfield(0, "item.yearCaption[]",
            createSubfieldField("yearCaption", "item.yearCaption[]", true))),

        // Missing pieces
        createField("numberOfMissingPieces", "item.numberOfMissingPieces", true),
        createField("missingPieces", "item.missingPieces", true),
        createField("missingPiecesDate", "item.missingPiecesDate", true),

        // Damaged status
        createField("itemDamagedStatusId", "item.itemDamagedStatusId", true),
        createField("itemDamagedStatusDate", "item.itemDamagedStatusDate", true),

        // Notes
        createFieldWithSubfields("notes", "item.notes[]", true,
          createSubfield(0, "item.notes[]",
            createSubfieldFieldRequired("itemNoteTypeId", "item.notes[].itemNoteTypeId", true, true),
            createSubfieldFieldRequired("note", "item.notes[].note", true, true),
            createSubfieldField("staffOnly", "item.notes[].staffOnly", true))),

        // Loan types
        createField("permanentLoanType.id", "item.permanentLoanType.id", true, "945$t"),
        createField("temporaryLoanType.id", "item.temporaryLoanType.id", true),

        // Status
        createField("status.name", "item.status.name", true, "945$a"),

        // Circulation notes
        createFieldWithSubfields("circulationNotes", "item.circulationNotes[]", true,
          createSubfield(0, "item.circulationNotes[]",
            createSubfieldField("noteType", "item.circulationNotes[].noteType", true),
            createSubfieldField("note", "item.circulationNotes[].note", true),
            createSubfieldField("staffOnly", "item.circulationNotes[].staffOnly", true))),

        // Locations
        createField("permanentLocation.id", "item.permanentLocation.id", true, "945$h"),
        createField("temporaryLocation.id", "item.temporaryLocation.id", true),

        // Electronic access
        createFieldWithSubfields("electronicAccess", "item.electronicAccess[]", true,
          createSubfield(0, "item.electronicAccess[]",
            createSubfieldField("relationshipId", "item.electronicAccess[].relationshipId", true),
            createSubfieldFieldRequired("uri", "item.electronicAccess[].uri", true, true),
            createSubfieldField("linkText", "item.electronicAccess[].linkText", true),
            createSubfieldField("materialsSpecification", "item.electronicAccess[].materialsSpecification", true),
            createSubfieldField("publicNote", "item.electronicAccess[].publicNote", true)))
      ));
  }

  /**
   * Creates default mappingDetails for MARC_BIBLIOGRAPHIC record type.
   * MARC records don't use the same field structure, so return empty mappingFields.
   */
  public static MappingDetail createMarcBibliographicMappingDetails() {
    return new MappingDetail()
      .withName("marcBibliographic")
      .withRecordType(EntityType.MARC_BIBLIOGRAPHIC)
      .withMappingFields(Collections.emptyList());
  }

  /**
   * Creates mappingDetails for a MARC_BIBLIOGRAPHIC update action.
   * The stack's MARC modifier requires marcMappingOption even when there are no field-level details.
   */
  public static MappingDetail createMarcBibliographicUpdateMappingDetails() {
    return createMarcBibliographicMappingDetails(MappingDetail.MarcMappingOption.UPDATE);
  }

  public static MappingDetail createMarcBibliographicModifyMappingDetails() {
    return createMarcBibliographicMappingDetails(MappingDetail.MarcMappingOption.MODIFY);
  }

  public static MappingDetail createMarcBibliographicMappingDetails(MappingDetail.MarcMappingOption option) {
    return createMarcBibliographicMappingDetails()
      .withMarcMappingOption(option)
      .withMarcMappingDetails(Collections.emptyList());
  }

  public static MappingDetail createMarcAuthorityMappingDetails() {
    return new MappingDetail()
      .withName("marcAuthority")
      .withRecordType(EntityType.MARC_AUTHORITY)
      .withMappingFields(Collections.emptyList())
      .withMarcMappingDetails(Collections.emptyList());
  }

  public static MappingDetail createMarcAuthorityUpdateMappingDetails() {
    return createMarcAuthorityMappingDetails(MappingDetail.MarcMappingOption.UPDATE);
  }

  public static MappingDetail createMarcAuthorityMappingDetails(MappingDetail.MarcMappingOption option) {
    return createMarcAuthorityMappingDetails()
      .withMarcMappingOption(option);
  }

  public static MappingDetail createMarcHoldingsMappingDetails() {
    return new MappingDetail()
      .withName("marcHoldings")
      .withRecordType(EntityType.MARC_HOLDINGS)
      .withMappingFields(Collections.emptyList())
      .withMarcMappingDetails(Collections.emptyList());
  }

  public static MappingDetail createMarcHoldingsUpdateMappingDetails() {
    return createMarcHoldingsMappingDetails(MappingDetail.MarcMappingOption.UPDATE);
  }

  public static MappingDetail createMarcHoldingsMappingDetails(MappingDetail.MarcMappingOption option) {
    return createMarcHoldingsMappingDetails()
      .withMarcMappingOption(option);
  }

  /**
   * Creates default mappingDetails for AUTHORITY record type.
   * Field list mirrors the data-import authority mapping shape used by mod-entities-links.
   * The fields are disabled because authority values are derived by the MARC authority mapper.
   */
  public static MappingDetail createAuthorityMappingDetails() {
    return new MappingDetail()
      .withName("authority")
      .withRecordType(EntityType.AUTHORITY)
      .withMappingFields(Arrays.asList(
        createField("personalName", "authority.personalName", false),
        createField("sftPersonalName", "authority.sftPersonalName[]", false),
        createField("saftPersonalName", "authority.saftPersonalName[]", false),
        createField("corporateName", "authority.corporateName", false),
        createField("sftCorporateName", "authority.sftCorporateName[]", false),
        createField("saftCorporateName", "authority.saftCorporateName[]", false),
        createField("meetingName", "authority.meetingName[]", false),
        createField("sftMeetingName", "authority.sftMeetingName[]", false),
        createField("saftMeetingName", "authority.saftMeetingName[]", false),
        createField("uniformTitle", "authority.uniformTitle", false),
        createField("sftUniformTitle", "authority.sftUniformTitle[]", false),
        createField("saftUniformTitle", "authority.saftUniformTitle[]", false),
        createField("topicalTerm", "authority.topicalTerm", false),
        createField("sftTopicalTerm", "authority.sftTopicalTerm[]", false),
        createField("saftTopicalTerm", "authority.saftTopicalTerm[]", false),
        createField("subjectHeadings", "authority.subjectHeadings", false),
        createField("geographicName", "authority.geographicName", false),
        createField("sftGeographicTerm", "authority.sftGeographicTerm[]", false),
        createField("saftGeographicTerm", "authority.saftGeographicTerm[]", false),
        createField("genre", "authority.genre", false),
        createField("identifiers", "authority.identifiers[]", false),
        createField("notes", "authority.notes[]", false)
      ))
      .withMarcMappingDetails(Collections.emptyList());
  }

  /**
   * Returns the appropriate MappingDetail for the given existing record type.
   *
   * @param existingRecordType the record type string (e.g., "INSTANCE", "HOLDINGS", "ITEM")
   * @return the MappingDetail for the record type, or null if not supported
   */
  public static MappingDetail createMappingDetailsForRecordType(String existingRecordType) {
    if (existingRecordType == null) {
      return null;
    }
    return switch (existingRecordType) {
      case "INSTANCE" -> createInstanceMappingDetails();
      case "HOLDINGS" -> createHoldingsMappingDetails();
      case "ITEM" -> createItemMappingDetails();
      case "AUTHORITY" -> createAuthorityMappingDetails();
      case "MARC_BIBLIOGRAPHIC" -> createMarcBibliographicMappingDetails();
      case "MARC_AUTHORITY" -> createMarcAuthorityMappingDetails();
      case "MARC_HOLDINGS" -> createMarcHoldingsMappingDetails();
      default -> null;
    };
  }

  private static MappingRule createField(String name, String path, boolean enabled) {
    return createField(name, path, enabled, "");
  }

  private static MappingRule createField(String name, String path, boolean enabled, String value) {
    return new MappingRule()
      .withName(name)
      .withPath(path)
      .withValue(value)
      .withEnabled(enabled ? "true" : "false")
      .withSubfields(Collections.emptyList());
  }

  private static MappingRule createFieldWithSubfields(String name, String path, boolean enabled,
                                                      RepeatableSubfieldMapping... subfields) {
    return new MappingRule()
      .withName(name)
      .withPath(path)
      .withValue("")
      .withEnabled(enabled ? "true" : "false")
      .withSubfields(Arrays.asList(subfields));
  }

  private static RepeatableSubfieldMapping createSubfield(int order, String path, MappingRule... fields) {
    return new RepeatableSubfieldMapping()
      .withOrder(order)
      .withPath(path)
      .withFields(Arrays.asList(fields));
  }

  private static MappingRule createSubfieldField(String name, String path, boolean enabled) {
    return createSubfieldField(name, path, enabled, "");
  }

  private static MappingRule createSubfieldField(String name, String path, boolean enabled, String value) {
    return new MappingRule()
      .withName(name)
      .withPath(path)
      .withValue(value)
      .withEnabled(enabled ? "true" : "false");
  }

  private static MappingRule createSubfieldFieldRequired(String name, String path, boolean enabled, boolean required) {
    return new MappingRule()
      .withName(name)
      .withPath(path)
      .withValue("")
      .withEnabled(enabled ? "true" : "false")
      .withRequired(required);
  }
}
