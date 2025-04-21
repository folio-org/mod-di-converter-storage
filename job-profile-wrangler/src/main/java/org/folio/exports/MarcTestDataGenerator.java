package org.folio.exports;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MarcField;
import org.folio.rest.jaxrs.model.MarcSubfield;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.folio.rest.jaxrs.model.StaticValueDetails;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.marc4j.MarcStreamWriter;
import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.Subfield;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.folio.Constants.OBJECT_MAPPER;


/**
 * Generates MARC records for testing job profiles.
 * Creates records that will match specific paths in a job profile graph.
 */
public class MarcTestDataGenerator {
  private static final Logger LOGGER = LogManager.getLogger(MarcTestDataGenerator.class);
  private static final MarcFactory factory = MarcFactory.newInstance();
  private static final Comparator<Integer> intComparator = Comparator.comparingInt(Integer::intValue);

  // Compare edges by source vertex order for consistent traversal
  public static final Comparator<RegularEdge> edgeComparator = (edge1, edge2) -> {
    JsonNode source1 = (JsonNode) edge1.getSource();
    JsonNode source2 = (JsonNode) edge2.getSource();
    return intComparator.compare(source1.path("order").asInt(), source2.path("order").asInt());
  };

  // Path entry that stores both node and incoming edge type
  private record PathEntry(JsonNode node, boolean isNonMatch) {}

  // MARC record provider - can be file-based or FOLIO-based
  private final MarcCircularStream marcStream;
  private final MatchExpressionHandler matchHandler;

  /**
   * Creates a generator that uses a local MARC file as a template.
   *
   * @param sampleMarcFilePath Path to the sample MARC file
   * @throws IOException If the file cannot be read
   */
  public MarcTestDataGenerator(String sampleMarcFilePath) throws IOException {
    if (!new java.io.File(sampleMarcFilePath).exists()) {
      throw new IllegalArgumentException("Sample MARC file does not exist: " + sampleMarcFilePath);
    }
    this.marcStream = new MarcCircularStream(sampleMarcFilePath);
    this.matchHandler = new MatchExpressionHandler(marcStream);
  }

  /**
   * Creates a generator that uses records from a FOLIO instance.
   *
   * @param baseUrl FOLIO instance base URL
   * @param token FOLIO authentication token
   */
  public MarcTestDataGenerator(String baseUrl, String token) {
    this(baseUrl, token, null);
  }

  /**
   * Creates a generator that uses records from a FOLIO instance with repository support.
   *
   * @param baseUrl FOLIO instance base URL
   * @param token FOLIO authentication token
   * @param repositoryPath Path to repository for storing currentId
   */
  public MarcTestDataGenerator(String baseUrl, String token, String repositoryPath) {
    this.marcStream = new MarcCircularStream(baseUrl, token, repositoryPath);
    this.matchHandler = new MatchExpressionHandler(marcStream);
  }

  /**
   * Generates test MARC records for all paths in a job profile snapshot.
   *
   * @param profileSnapshot The job profile snapshot JSON node
   * @param outputFilePath The output file path for the MARC records
   */
  public void generateTestData(JsonNode profileSnapshot, String outputFilePath) {
    Graph<JsonNode, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
    buildGraph(g, profileSnapshot);

    try {
      // Write the record to a file
      MarcStreamWriter writer = new MarcStreamWriter(new FileOutputStream(outputFilePath), "UTF-8");
      generateMarcRecords(g).forEach(writer::write);
      writer.close();
      LOGGER.info("Generated MARC records written to {}", outputFilePath);
    } catch (IOException e) {
      LOGGER.error("Error creating MARC record: {}", e.getMessage(), e);
    }
  }

  /**
   * Generates MARC records for all paths in the graph.
   */
  private Collection<Record> generateMarcRecords(Graph<JsonNode, RegularEdge> graph) throws IOException {
    // Get the job profile which should be the root of the graph
    Optional<JsonNode> jobProfile = graph.vertexSet().stream()
      .filter(key -> graph.incomingEdgesOf(key).isEmpty())
      .findFirst();

    if (jobProfile.isEmpty()) {
      LOGGER.warn("No job profile found in the graph");
      return Collections.emptyList();
    }

    // Get all possible paths through the graph
    Collection<List<PathEntry>> paths = listAllPaths(graph, jobProfile.get());
    LOGGER.info("Found {} distinct paths in the job profile", paths.size());

    // Group paths that can share the same record
    Collection<List<List<PathEntry>>> groupedPaths = groupPathsByMatchResult(paths);
    LOGGER.info("Grouped into {} records for testing", groupedPaths.size());

    ArrayList<Record> records = new ArrayList<>(groupedPaths.size());

    // Generate one record for each group of paths
    for (List<List<PathEntry>> pathGroup : groupedPaths) {
      try {
        Record templateRecord = marcStream.nextRecord();
        // Use the first path in the group as reference
        List<PathEntry> referencePath = pathGroup.get(0);

        // Generate initial record from reference path
        Record newRecord = generateRecordFromPath(referencePath, templateRecord);

        // If there are additional paths in this group, ensure the record satisfies them all
        for (int i = 1; i < pathGroup.size(); i++) {
          enhanceRecordForPath(newRecord, pathGroup.get(i), templateRecord);
        }

        records.add(newRecord);
      } catch (Exception e) {
        LOGGER.error("Error generating record for path group: {}", e.getMessage(), e);
      }
    }

    return records;
  }

  /**
   * Enhances an existing record to also satisfy an additional path.
   * This ensures that a single record can be used to test multiple paths.
   */
  private void enhanceRecordForPath(Record newRecord, List<PathEntry> path, Record templateRecord) {
    // Extract all match profiles from the path
    List<PathEntry> matchProfiles = path.stream()
      .filter(entry -> "MATCH_PROFILE".equals(entry.node().path("contentType").asText()))
      .toList();

    // Apply each match profile's conditions to the record
    for (PathEntry entry : matchProfiles) {
      JsonNode matchProfileNode = entry.node();
      boolean isNonMatch = entry.isNonMatch();

      MatchProfile matchProfile = OBJECT_MAPPER.convertValue(
        matchProfileNode.path("content"), MatchProfile.class);

      List<MatchDetail> matchDetails = matchProfile.getMatchDetails();
      if (matchDetails == null || matchDetails.isEmpty()) {
        LOGGER.warn("Match profile has no match details: {}", matchProfile.getName());
        continue;
      }

      // Apply each match detail to the record
      for (MatchDetail matchDetail : matchDetails) {
        applyMatchDetailToRecord(templateRecord, newRecord, matchDetail, isNonMatch);
      }
    }
  }

  /**
   * Checks if the path contains an action profile with action type "CREATE".
   *
   * @param profilePath The path to check
   * @return true if the path contains a create action profile, false otherwise
   */
  public boolean isCreateActionPath(List<PathEntry> profilePath) {
    return profilePath.stream()
      .filter(entry -> "ACTION_PROFILE".equals(entry.node().path("contentType").asText()))
      .anyMatch(entry -> "CREATE".equals(entry.node().path("content").path("action").asText()));
  }

  /**
   * Generates a MARC record that satisfies the match conditions in the given path.
   */
  private Record generateRecordFromPath(List<PathEntry> profilePath, Record templateRecord) {
    // Create a deep copy of the base record to modify
    Record newRecord = deepCopyRecord(templateRecord);

    // Extract all match profiles from the path
    List<PathEntry> matchProfiles = profilePath.stream()
      .filter(entry -> "MATCH_PROFILE".equals(entry.node().path("contentType").asText()))
      .toList();

    // Apply each match profile's conditions to the record
    for (PathEntry entry : matchProfiles) {
      JsonNode matchProfileNode = entry.node();
      boolean isNonMatch = entry.isNonMatch();

      MatchProfile matchProfile = OBJECT_MAPPER.convertValue(
        matchProfileNode.path("content"), MatchProfile.class);

      List<MatchDetail> matchDetails = matchProfile.getMatchDetails();
      if (matchDetails == null || matchDetails.isEmpty()) {
        LOGGER.warn("Match profile has no match details: {}", matchProfile.getName());
        continue;
      }

      // Apply each match detail to the record
      for (MatchDetail matchDetail : matchDetails) {
        applyMatchDetailToRecord(templateRecord, newRecord, matchDetail, isNonMatch);
      }
    }

    // If this path has a CREATE action profile, remove field 999ff$i if it exists
    if (isCreateActionPath(profilePath)) {
      newRecord.removeVariableField(newRecord.getVariableField("999"));
    }

    return newRecord;
  }

  /**
   * Applies a match detail to a MARC record, modifying fields as needed
   * to satisfy the match condition.
   *
   * @param templateRecord The source/template MARC record used for reference
   * @param newRecord The record being created/modified to satisfy match conditions
   * @param matchDetail The match detail to apply
   * @param isNonMatch If true, the condition is inverted (NON_MATCH)
   */
  private void applyMatchDetailToRecord(Record templateRecord, Record newRecord, MatchDetail matchDetail, boolean isNonMatch) {
    MatchExpression incomingExpression = matchDetail.getIncomingMatchExpression();
    MatchExpression existingExpression = matchDetail.getExistingMatchExpression();

    if (incomingExpression == null || existingExpression == null) {
      LOGGER.warn("Match detail has incomplete match expressions");
      return;
    }

    // Get MARC field from incoming expression
    MarcField marcField = deriveMarcField(incomingExpression);
    if (marcField == null || marcField.getField() == null) {
      LOGGER.warn("Could not derive MARC field from match expression");
      return;
    }

    // Get the match criterion
    MatchDetail.MatchCriterion criterion = matchDetail.getMatchCriterion();

    // For now, we only handle EXACTLY_MATCHES in detail
    String valueToSet;
    if (criterion != null && "EXACTLY_MATCHES".equals(criterion.value())) {
      valueToSet = determineValueForExactMatch(matchDetail, templateRecord);
    } else {
      // Fall back to current implementation for other criteria
      valueToSet = extractValueFromRecord(templateRecord, incomingExpression);
      if (valueToSet == null || valueToSet.isEmpty()) {
        valueToSet = generateAppropriateValue(incomingExpression);
      }
    }

    // For NON_MATCH conditions, modify the value so it won't match
    if (isNonMatch) {
      valueToSet = invertMatchValue(valueToSet, matchDetail);
    }

    // Apply the value to the new record
    boolean isControlField = isControlField(marcField.getField());
    if (isControlField) {
      applyControlField(newRecord, marcField, valueToSet);
    } else {
      applyDataField(newRecord, marcField, valueToSet);
    }
  }

  /**
   * Determines the appropriate value for exact matching
   */
  private String determineValueForExactMatch(MatchDetail matchDetail, Record templateRecord) {
    MatchExpression incomingExpression = matchDetail.getIncomingMatchExpression();
    MatchExpression existingExpression = matchDetail.getExistingMatchExpression();

    // First try to extract a value from the template record
    String valueFromTemplate = extractValueFromRecord(templateRecord, incomingExpression);

    // If no value found in the template, check if existing expression has a static value
    if (valueFromTemplate == null || valueFromTemplate.isEmpty()) {
      if (existingExpression.getDataValueType() == MatchExpression.DataValueType.STATIC_VALUE) {
        return extractValueFromExpression(existingExpression);
      } else {
        // If everything else fails, generate an appropriate value
        String fieldInfo = extractValueFromExpression(existingExpression);
        return generateAppropriateValueFromFieldInfo(fieldInfo);
      }
    }

    return valueFromTemplate;
  }

  /**
   * Extracts value information from an expression
   */
  private String extractValueFromExpression(MatchExpression expression) {
    // Check the data value type
    if (expression.getDataValueType() == MatchExpression.DataValueType.STATIC_VALUE) {
      // Handle static value case
      StaticValueDetails staticValueDetails = expression.getStaticValueDetails();
      if (staticValueDetails != null) {
        StaticValueDetails.StaticValueType type = staticValueDetails.getStaticValueType();
        if (type != null) {
          switch (type.value()) {
            case "TEXT":
              return staticValueDetails.getText();
            case "NUMBER":
              return staticValueDetails.getNumber();
            case "EXACT_DATE":
              if (staticValueDetails.getExactDate() != null) {
                return staticValueDetails.getExactDate().toString();
              }
              break;
            case "DATE_RANGE":
              // For date range, use fromDate as the representative value
              if (staticValueDetails.getFromDate() != null) {
                return staticValueDetails.getFromDate().toString();
              }
              break;
          }
        }
      }
    } else if (expression.getDataValueType() == MatchExpression.DataValueType.VALUE_FROM_RECORD) {
      // Extract field information that could be useful for generating an appropriate value
      List<Field> fields = expression.getFields();
      if (fields != null && !fields.isEmpty()) {
        // Get field name or identifier - could be used to understand expected data format
        for (Field field : fields) {
          if ("field".equals(field.getLabel())) {
            return field.getValue();
          }
        }
      }
    }
    return null;
  }

  /**
   * Generates an appropriate value based on field information
   */
  private String generateAppropriateValueFromFieldInfo(String fieldInfo) {
    if (fieldInfo == null) {
      return generateRandomValue();
    }

    // Generate appropriate test values based on common MARC fields or field types
    switch (fieldInfo) {
      case "001":
        return "oc" + randomNumeric(9); // Control number
      case "020":
        return "978" + randomNumeric(10); // ISBN
      case "022":
        return randomNumeric(4) + "-" + randomNumeric(4); // ISSN
      case "245":
        return "Test Title " + randomAlphanumeric(20); // Title
      case "100":
        return "Author, Test " + randomAlpha(10); // Author
      case "260":
      case "264":
        return "Test Publisher, " + (2000 + Integer.parseInt(randomNumeric(2))); // Publication info
      case "300":
        return randomNumeric(3) + " p. ; " + randomNumeric(2) + " cm."; // Physical description
      case "650":
        return "Test Subject -- Subdivision"; // Subject term
      default:
        return generateRandomValue();
    }
  }

  /**
   * Inverts a match value for NON_MATCH conditions.
   * Creates a value that won't match the original condition.
   */
  private String invertMatchValue(String originalValue, MatchDetail matchDetail) {
    if (originalValue == null || originalValue.isEmpty()) {
      return "NON_MATCH_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // Get the match criterion directly from the MatchDetail object
    MatchDetail.MatchCriterion matchCriterion = matchDetail.getMatchCriterion();
    String criterionValue = matchCriterion != null ? matchCriterion.value() : "EXACTLY_MATCHES";

    return switch(criterionValue) {
      case "EXACTLY_MATCHES" -> "NON_" + originalValue;
      case "EXISTING_VALUE_CONTAINS_INCOMING_VALUE" -> UUID.randomUUID().toString().substring(0, 8);
      case "EXISTING_VALUE_BEGINS_WITH_INCOMING_VALUE" -> "X" + originalValue;
      case "EXISTING_VALUE_ENDS_WITH_INCOMING_VALUE" -> originalValue + "X";
      default -> "NON_MATCH_" + originalValue;
    };
  }

  /**
   * Applies a value to a control field.
   */
  private void applyControlField(Record record, MarcField marcField, String valueToSet) {
    String fieldTag = marcField.getField();
    ControlField existingField = (ControlField) record.getVariableField(fieldTag);

    if (existingField != null) {
      // Update existing control field
      existingField.setData(valueToSet);
    } else {
      // Create new control field
      ControlField newField = factory.newControlField(fieldTag, valueToSet);
      record.addVariableField(newField);
    }
  }

  /**
   * Applies a value to a data field and its subfields.
   */
  private void applyDataField(Record record, MarcField marcField, String valueToSet) {
    String fieldTag = marcField.getField();
    String ind1 = marcField.getIndicator1();
    String ind2 = marcField.getIndicator2();

    // Get existing data field if it exists
    DataField existingField = (DataField) record.getVariableField(fieldTag);

    // Create data field if it doesn't exist
    if (existingField == null) {
      char ind1Char = ind1 != null && !ind1.isEmpty() ? ind1.charAt(0) : ' ';
      char ind2Char = ind2 != null && !ind2.isEmpty() ? ind2.charAt(0) : ' ';

      existingField = factory.newDataField(fieldTag, ind1Char, ind2Char);
      record.addVariableField(existingField);
    }

    // Apply subfields from match expression
    List<MarcSubfield> subfields = marcField.getSubfields();
    if (subfields != null && !subfields.isEmpty()) {
      for (MarcSubfield marcSubfield : subfields) {
        String subfieldCode = marcSubfield.getSubfield();
        if (subfieldCode == null || subfieldCode.isEmpty()) {
          continue;
        }

        // Find existing subfield
        Subfield existingSubfield = existingField.getSubfield(subfieldCode.charAt(0));

        if (existingSubfield != null) {
          // Update existing subfield
          existingSubfield.setData(valueToSet);
        } else {
          // Create new subfield
          Subfield newSubfield = factory.newSubfield(subfieldCode.charAt(0), valueToSet);
          existingField.addSubfield(newSubfield);
        }
      }
    } else {
      // If no specific subfield is defined, add a default subfield 'a'
      Subfield subfield = existingField.getSubfield('a');
      if (subfield != null) {
        subfield.setData(valueToSet);
      } else {
        existingField.addSubfield(factory.newSubfield('a', valueToSet));
      }
    }
  }

  /**
   * Extracts a value from a MARC record based on the match expression.
   *
   * @param record The MARC record
   * @param expression The match expression
   * @return The extracted value
   */
  private String extractValueFromRecord(Record record, MatchExpression expression) {
    List<Field> fields = expression.getFields();
    if (fields == null || fields.isEmpty()) {
      return generateRandomValue();
    }

    // Extract field tag, indicators, and subfield from the match expression
    String fieldTag = null;
    String indicator1 = null;
    String indicator2 = null;
    String subfieldCode = null;

    for (Field field : fields) {
      String label = field.getLabel();
      String value = field.getValue();

      if (label == null || value == null) {
        continue;
      }

      switch (label) {
        case "field":
          fieldTag = value;
          break;
        case "indicator1":
          indicator1 = value;
          break;
        case "indicator2":
          indicator2 = value;
          break;
        case "recordSubfield":
          subfieldCode = value;
          break;
      }
    }

    if (fieldTag == null) {
      return generateRandomValue();
    }

    // Extract the value from the record
    try {
      // Handle control fields (001-009)
      if (isControlField(fieldTag)) {
        ControlField controlField = (ControlField) record.getVariableField(fieldTag);
        if (controlField != null) {
          return controlField.getData();
        }
      }
      // Handle data fields with subfields and optional indicators
      else if (subfieldCode != null && !subfieldCode.isEmpty()) {
        // Create final copies of the variables for use in lambdas
        final String finalFieldTag = fieldTag;
        final String finalIndicator1 = indicator1;
        final String finalIndicator2 = indicator2;

        // Get all matching data fields
        List<DataField> matchingFields = record.getDataFields()
          .stream()
          .filter(df -> df.getTag().equals(finalFieldTag))
          .filter(df -> finalIndicator1 == null || String.valueOf(df.getIndicator1()).equals(finalIndicator1))
          .filter(df -> finalIndicator2 == null || String.valueOf(df.getIndicator2()).equals(finalIndicator2))
          .toList();

        // If we found matching fields, extract the subfield data
        if (!matchingFields.isEmpty()) {
          DataField dataField = matchingFields.get(0); // Use the first matching field
          Subfield subfield = dataField.getSubfield(subfieldCode.charAt(0));
          if (subfield != null) {
            return subfield.getData();
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Error extracting value from record: {}", e.getMessage());
    }

    return generateAppropriateValue(expression);
  }

  /**
   * Extracts MARC field info from a match expression.
   */
  private MarcField deriveMarcField(MatchExpression matchExpression) {
    List<Field> fields = matchExpression.getFields();
    if (fields == null || fields.isEmpty()) {
      return null;
    }

    MarcField marcField = new MarcField();
    Map<String, String> fieldValues = new HashMap<>();

    // Collect all field values
    for (Field field : fields) {
      if (field.getLabel() != null && field.getValue() != null) {
        fieldValues.put(field.getLabel(), field.getValue());
      }
    }

    // Set field tag
    marcField.setField(fieldValues.get("field"));

    // Set indicators
    marcField.setIndicator1(fieldValues.getOrDefault("indicator1", " "));
    marcField.setIndicator2(fieldValues.getOrDefault("indicator2", " "));

    // Set subfields
    String subfieldCode = fieldValues.get("recordSubfield");
    if (subfieldCode != null) {
      marcField.setSubfields(List.of(new MarcSubfield().withSubfield(subfieldCode)));
    }

    return marcField;
  }

  /**
   * Determines if a field is a control field (001-009).
   */
  private boolean isControlField(String field) {
    try {
      int fieldNum = Integer.parseInt(field);
      return fieldNum >= 1 && fieldNum <= 9;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Generates an appropriate value based on the match expression type.
   * Attempts to create a value that would make sense for the field.
   *
   * @param expression The match expression
   * @return A generated value
   */
  private String generateAppropriateValue(MatchExpression expression) {
    // Extract field tag from the expression
    String fieldTag = null;
    List<Field> fields = expression.getFields();

    if (fields != null) {
      for (Field field : fields) {
        if ("field".equals(field.getLabel())) {
          fieldTag = field.getValue();
          break;
        }
      }
    }

    return generateAppropriateValueFromFieldInfo(fieldTag);
  }

  /**
   * Generates a random value for use when no specific value can be determined.
   *
   * @return A random string
   */
  private String generateRandomValue() {
    return "TestValue-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);
  }

  /**
   * Generates a random numeric string of the specified length.
   *
   * @param length The length of the string
   * @return A random numeric string
   */
  private String randomNumeric(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      sb.append((int) (Math.random() * 10));
    }
    return sb.toString();
  }

  /**
   * Generates a random alphabetic string of the specified length.
   *
   * @param length The length of the string
   * @return A random alphabetic string
   */
  private String randomAlpha(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      char c = (char) ('a' + (int) (Math.random() * 26));
      sb.append(c);
    }
    return sb.toString();
  }

  /**
   * Generates a random alphanumeric string of the specified length.
   *
   * @param length The length of the string
   * @return A random alphanumeric string
   */
  private String randomAlphanumeric(int length) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < length; i++) {
      if (Math.random() < 0.7) {
        char c = (char) ('a' + (int) (Math.random() * 26));
        sb.append(c);
      } else {
        sb.append((int) (Math.random() * 10));
      }
    }
    return sb.toString();
  }

  /**
   * Creates a deep copy of a MARC record.
   */
  private Record deepCopyRecord(Record originalRecord) {
    Record newRecord = factory.newRecord();

    // Copy leader
    newRecord.setLeader(factory.newLeader(originalRecord.getLeader().toString()));

    // Copy control fields
    originalRecord.getControlFields().forEach(field ->
      newRecord.addVariableField(factory.newControlField(
        field.getTag(), field.getData())));

    // Copy data fields
    originalRecord.getDataFields().forEach(field -> {
      DataField newField = factory.newDataField(
        field.getTag(), field.getIndicator1(), field.getIndicator2());

      // Copy all subfields
      field.getSubfields().forEach(subfield ->
        newField.addSubfield(factory.newSubfield(
          subfield.getCode(), subfield.getData())));

      newRecord.addVariableField(newField);
    });

    return newRecord;
  }

  /**
   * Lists all possible paths through the graph from the root.
   * Tracks the edge relationship type (MATCH/NON_MATCH) for each node.
   */
  private Collection<List<PathEntry>> listAllPaths(Graph<JsonNode, RegularEdge> graph, JsonNode root) {
    // Root node has no incoming edge, so isNonMatch is always false
    return listAllPathsRecursive(graph, root, false);
  }

  /**
   * Recursive helper for listing paths.
   *
   * @param graph The job profile graph
   * @param node The current node
   * @param isNonMatch Whether the edge leading to this node is a NON_MATCH edge
   */
  private Collection<List<PathEntry>> listAllPathsRecursive(Graph<JsonNode, RegularEdge> graph, JsonNode node, boolean isNonMatch) {
    PathEntry entry = new PathEntry(node, isNonMatch);
    Set<RegularEdge> edges = graph.outgoingEdgesOf(node);

    if (edges.isEmpty()) {
      // Leaf node - return single path containing just this node
      return List.of(List.of(entry));
    }

    // Sort edges for consistent traversal
    List<RegularEdge> sortedEdges = edges.stream()
      .sorted(edgeComparator)
      .collect(Collectors.toList());

    // Process each outgoing edge
    List<List<PathEntry>> allPaths = new ArrayList<>();
    for (RegularEdge edge : sortedEdges) {
      JsonNode target = graph.getEdgeTarget(edge);
      boolean targetIsNonMatch = edge instanceof NonMatchRelationshipEdge;

      // Recursively get paths from the target
      Collection<List<PathEntry>> targetPaths = listAllPathsRecursive(graph, target, targetIsNonMatch);

      // Add the current node to the beginning of each target path
      for (List<PathEntry> targetPath : targetPaths) {
        List<PathEntry> completePath = new ArrayList<>();
        completePath.add(entry);
        completePath.addAll(targetPath);
        allPaths.add(completePath);
      }
    }

    return allPaths;
  }

  /**
   * Builds a graph representation of the profile snapshot.
   */
  private static Graph<JsonNode, RegularEdge> buildGraph(Graph<JsonNode, RegularEdge> graph, JsonNode profileSnapshot) {
    addProfileToGraph(graph, profileSnapshot);
    return graph;
  }

  /**
   * Adds a profile node to the graph.
   */
  private static void addProfileToGraph(Graph<JsonNode, RegularEdge> graph, JsonNode profileSnapshot) {
    graph.addVertex(profileSnapshot);
    Optional.of(profileSnapshot)
      .map(node -> node.path("childSnapshotWrappers"))
      .filter(JsonNode::isArray)
      .ifPresent(children -> addChildren(graph, profileSnapshot, children));
  }

  /**
   * Adds child profiles to the graph with appropriate edges.
   */
  private static void addChildren(Graph<JsonNode, RegularEdge> graph, JsonNode parent, JsonNode children) {
    boolean isMatchProfile = "MATCH_PROFILE".equals(parent.path("contentType").asText());

    StreamSupport.stream(children.spliterator(), false)
      .peek(child -> addProfileToGraph(graph, child))
      .forEach(child -> addEdge(graph, parent, child, isMatchProfile));
  }

  /**
   * Adds an edge to the graph, with special handling for match profiles.
   */
  private static void addEdge(Graph<JsonNode, RegularEdge> graph, JsonNode parent, JsonNode child, boolean isMatchProfile) {
    if (!isMatchProfile) {
      graph.addEdge(parent, child, new RegularEdge());
      return;
    }

    Optional.of(child.path("reactTo").asText())
      .map(reactTo -> switch (reactTo) {
        case "NON_MATCH" -> new NonMatchRelationshipEdge();
        case "MATCH" -> new MatchRelationshipEdge();
        default -> {
          LOGGER.warn("Invalid relationship for matching on profile: {}", parent);
          yield new RegularEdge();
        }
      })
      .ifPresent(edge -> graph.addEdge(parent, child, edge));
  }

  /**
   * Groups paths that can share the same record based on match result type.
   * Paths are grouped when:
   * 1. They share common ancestors AND
   * 2. Either:
   *    - Their most recent common ancestor is NOT a match profile, OR
   *    - Their most recent common ancestor IS a match profile AND they diverge with the same match result type
   *
   * @param allPaths Collection of all paths through the job profile graph
   * @return Collection of grouped paths, where each group will generate one record
   */
  private Collection<List<List<PathEntry>>> groupPathsByMatchResult(Collection<List<PathEntry>> allPaths) {
    // Result: each list in the collection represents a group of paths that can share one record
    List<List<List<PathEntry>>> groupedPaths = new ArrayList<>();

    // Create a copy of allPaths to work with
    List<List<PathEntry>> remainingPaths = new ArrayList<>(allPaths);

    while (!remainingPaths.isEmpty()) {
      // Take the first path as a reference
      List<PathEntry> referencePath = remainingPaths.remove(0);

      // Paths that will be grouped with the reference path
      List<List<PathEntry>> currentGroup = new ArrayList<>();
      currentGroup.add(referencePath);

      // Iterate through remaining paths to find those that can be grouped
      Iterator<List<PathEntry>> iterator = remainingPaths.iterator();
      while (iterator.hasNext()) {
        List<PathEntry> candidatePath = iterator.next();
        if (canGroupPaths(referencePath, candidatePath)) {
          currentGroup.add(candidatePath);
          iterator.remove();
        }
      }

      groupedPaths.add(currentGroup);
    }

    LOGGER.info("Grouped {} paths into {} groups for record generation",
      allPaths.size(), groupedPaths.size());

    return groupedPaths;
  }

  /**
   * Determines if two paths can be grouped (share the same record).
   *
   * @param path1 First path
   * @param path2 Second path
   * @return true if paths can be grouped, false otherwise
   */
  private boolean canGroupPaths(List<PathEntry> path1, List<PathEntry> path2) {
    // Find the index where the paths diverge
    int divergenceIndex = findDivergenceIndex(path1, path2);

    // If paths are identical, they can be grouped
    if (divergenceIndex == -1) {
      return true;
    }

    // If divergence happens at the very beginning, they can't be grouped
    if (divergenceIndex == 0) {
      return false;
    }

    // Get the node where paths diverge (common ancestor)
    PathEntry commonAncestor = path1.get(divergenceIndex - 1);

    // Check if the common ancestor is a match profile
    boolean isMatchProfile = "MATCH_PROFILE".equals(
      commonAncestor.node().path("contentType").asText());

    // If not a match profile, paths can be grouped
    if (!isMatchProfile) {
      return true;
    }

    // If a match profile, get match result type for both paths at divergence
    boolean isNonMatch1 = divergenceIndex < path1.size() && path1.get(divergenceIndex).isNonMatch();
    boolean isNonMatch2 = divergenceIndex < path2.size() && path2.get(divergenceIndex).isNonMatch();

    // If match result types differ, they can't be grouped
    if (isNonMatch1 != isNonMatch2) {
      return false;
    }

    // CRITICAL FIX: Check if any of the divergent nodes is a match profile
    // If one path goes to another match profile and one doesn't, they can't be grouped
    boolean isMatchProfile1 = divergenceIndex < path1.size() &&
      "MATCH_PROFILE".equals(path1.get(divergenceIndex).node().path("contentType").asText());
    boolean isMatchProfile2 = divergenceIndex < path2.size() &&
      "MATCH_PROFILE".equals(path2.get(divergenceIndex).node().path("contentType").asText());

    // If only one path contains a match profile at the divergence point, they can't be grouped
    if (isMatchProfile1 != isMatchProfile2) {
      return false;
    }

    // Both paths have the same match result type and neither or both contain match profiles
    return true;
  }

  /**
   * Finds the index where two paths diverge.
   *
   * @param path1 First path
   * @param path2 Second path
   * @return Index where paths diverge, or -1 if they are identical
   */
  private int findDivergenceIndex(List<PathEntry> path1, List<PathEntry> path2) {
    int minLength = Math.min(path1.size(), path2.size());

    for (int i = 0; i < minLength; i++) {
      // Compare nodes by contentType and ID to identify divergence
      JsonNode node1 = path1.get(i).node();
      JsonNode node2 = path2.get(i).node();

      // Using profileWrapperId for comparison
      if (!node1.path("profileWrapperId").equals(node2.path("profileWrapperId"))) {
        return i;
      }
    }

    // If one path is a prefix of the other, return the length of the shorter path
    return path1.size() != path2.size() ? minLength : -1;
  }

  /**
   * Closes resources used by the generator.
   */
  public void close() throws IOException {
    if (marcStream != null) {
      marcStream.close();
    }
  }
}
