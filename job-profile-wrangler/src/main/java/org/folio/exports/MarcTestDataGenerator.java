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

    ArrayList<Record> records = new ArrayList<>(paths.size());

    // Generate a record for each path
    for (List<PathEntry> path : paths) {
      try {
        Record baseRecord = marcStream.nextRecord();
        records.add(generateRecordFromPath(path, baseRecord));
      } catch (Exception e) {
        LOGGER.error("Error generating record for path: {}", e.getMessage(), e);
      }
    }

    return records;
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
  private Record generateRecordFromPath(List<PathEntry> profilePath, Record baseRecord) {
    // Create a deep copy of the base record to modify
    Record record = deepCopyRecord(baseRecord);

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
        applyMatchDetailToRecord(baseRecord, record, matchDetail, isNonMatch);
      }
    }

    // If this path has a CREATE action profile, remove field 999ff$i if it exists
    if (isCreateActionPath(profilePath)) {
      record.removeVariableField(record.getVariableField("999"));
    }

    return record;
  }

  /**
   * Applies a match detail to a MARC record, modifying fields as needed
   * to satisfy the match condition.
   *
   * @param isNonMatch If true, the condition is inverted (NON_MATCH)
   */
  private void applyMatchDetailToRecord(Record existingRecord, Record record, MatchDetail matchDetail, boolean isNonMatch) {
    MatchExpression incomingMatchExpression = matchDetail.getIncomingMatchExpression();
    if (incomingMatchExpression == null) {
      LOGGER.warn("Match detail has no incoming match expression");
      return;
    }

    // Get the field information from the match expression
    MarcField marcField = deriveMarcField(incomingMatchExpression);
    if (marcField == null || marcField.getField() == null) {
      LOGGER.warn("Could not derive MARC field from match expression");
      return;
    }

    // Get the value to set in the field
    String valueToSet = matchHandler.getValueFromMatchDetailOrExistingRecord(matchDetail, existingRecord);

    // For NON_MATCH conditions, modify the value so it won't match
    if (isNonMatch) {
      valueToSet = invertMatchValue(valueToSet, matchDetail);
    }

    // Check if the field is a control field (001-009)
    boolean isControlField = isControlField(marcField.getField());

    // Handle control fields (001-009)
    if (isControlField) {
      applyControlField(record, marcField, valueToSet);
      return;
    }

    // Handle data fields (010+)
    applyDataField(record, marcField, valueToSet);
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
   * Closes resources used by the generator.
   */
  public void close() throws IOException {
    if (marcStream != null) {
      marcStream.close();
    }
  }
}
