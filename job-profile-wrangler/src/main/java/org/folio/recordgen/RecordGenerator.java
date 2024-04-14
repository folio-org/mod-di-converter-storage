package org.folio.recordgen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.github.javafaker.Faker;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonNodeJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.GraphConstruction;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.rest.jaxrs.model.Field;
import org.folio.rest.jaxrs.model.MarcField;
import org.folio.rest.jaxrs.model.MarcSubfield;
import org.folio.rest.jaxrs.model.MatchDetail;
import org.folio.rest.jaxrs.model.MatchExpression;
import org.folio.rest.jaxrs.model.MatchProfile;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.AllDirectedPaths;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.marc4j.MarcPermissiveStreamReader;
import org.marc4j.MarcReader;
import org.marc4j.MarcReaderFactory;
import org.marc4j.marc.DataField;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.marc4j.marc.VariableField;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.jayway.jsonpath.JsonPath.using;
import static org.folio.Constants.OBJECT_MAPPER;

/**
 * Generates records based on a job profile.
 */
public class RecordGenerator {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final String JSON_PATH_SEPARATOR = ".";
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();
  private static final Faker FAKER = new Faker();
  private static final Configuration JACKSON_JSON_NODE_CONFIGURATION = Configuration
    .builder()
    .mappingProvider(new JacksonMappingProvider())
    .jsonProvider(new JacksonJsonNodeJsonProvider())
    .build();
  private final FolioClient client;
  private final RoundRobinIdentifiers identifiers;

  public RecordGenerator(FolioClient client, RoundRobinIdentifiers identifiers) {
    this.client = client;
    this.identifiers = identifiers;
  }

  /**
   * Generates records based on the given job profile ID.
   *
   * @param jobProfileId The ID of the job profile.
   * @return An Optional containing the generated records as a byte array, or an empty Optional if generation fails.
   * @throws IOException If an I/O error occurs.
   */
  public Optional<Collection<Record>> generate(String jobProfileId) throws IOException {
    Optional<JsonNode> jobProfileSnapshot = client.getJobProfileSnapshot(jobProfileId);
    if (jobProfileSnapshot.isEmpty()) return Optional.empty();
    Graph<Profile, RegularEdge> profileGraph = new SimpleDirectedGraph<>(RegularEdge.class);
    GraphConstruction graphConstruction = new GraphConstruction(profileGraph, jobProfileSnapshot.get());
    Optional<Profile> jobProfileOptional = graphConstruction.construct();
    Map<Profile, JsonNode> profileContents = graphConstruction.getProfileContents();
    if (jobProfileOptional.isEmpty()) return Optional.empty();

    List<GraphPath<Profile, RegularEdge>> paths = findAllPathsToLeafNodes(profileGraph, jobProfileOptional.get());

    // resolve matches for paths
    Map<GraphPath<Profile, RegularEdge>, List<ResolvedMatch>> resolvedMatchesByPath = new HashMap<>();
    for (GraphPath<Profile, RegularEdge> path : paths) {
      resolvedMatchesByPath.put(path, resolveMatches(profileContents, path));
    }

    List<Record> records = new ArrayList<>();
    for (var resolvedMatches : resolvedMatchesByPath.entrySet()) {
      var optionalRecord = generateRecord(resolvedMatches.getKey(), resolvedMatches.getValue());
      optionalRecord.ifPresent(records::add);
      identifiers.next();
    }

    return Optional.of(records);
  }


  private Optional<Record> generateRecord(GraphPath<Profile, RegularEdge> path, List<ResolvedMatch> resolvedMatches) {
    Profile endVertex = path.getEndVertex();
    if (!(endVertex instanceof ActionProfileNode)) return Optional.empty();
    String recordType = ((ActionProfileNode) endVertex).folioRecord();
    Record record = MARC_FACTORY.newRecord();
    if (Arrays.stream(FolioClient.ExportRecordType.values()).anyMatch(type -> type.name().equals(recordType))) {
      FolioClient.ExportRecordType exportRecordType = FolioClient.ExportRecordType.valueOf(recordType);
      if (!FolioClient.ExportRecordType.ITEM.equals(exportRecordType)) { // no default data export profile exists for items
        Optional<byte[]> bytes = client.exportFolioObject(exportRecordType, identifiers.get().forRecordType(exportRecordType));
        if (bytes.isPresent()) {
          MarcReader reader = new MarcPermissiveStreamReader(new ByteArrayInputStream(bytes.get()), true, true);
          record = reader.next();
        }
      }
    }

    for (var resolvedMatch : resolvedMatches) {
      if (MatchExpression.DataValueType.VALUE_FROM_RECORD.equals(resolvedMatch.getIncomingDataValueType()) &&
        resolvedMatch.getIncoming() instanceof MarcField incomingMarcField) {
        if (incomingMarcField.getSubfields().size() > 1) {
          LOGGER.error("More than one subfield is present in record. field={}", incomingMarcField);
          return Optional.empty();
        }

        DataField df = MARC_FACTORY.newDataField(incomingMarcField.getField(),
          CharUtils.toChar(incomingMarcField.getIndicator1(), ' '),
          CharUtils.toChar(incomingMarcField.getIndicator2(), ' '));
        Object existing = resolvedMatch.getExisting();
        JsonNode loadedValueJsonNode = getExistingValue(existing);

        if (!loadedValueJsonNode.isArray()) {
          // convert single nodes to array so that it is suitable for upcoming logic
          loadedValueJsonNode = OBJECT_MAPPER.createArrayNode().add(loadedValueJsonNode);
        }

        for (var node : loadedValueJsonNode) {
          char subfieldTag = CharUtils.toChar(incomingMarcField
            .getSubfields()
            .get(0)
            .getSubfield(), ' ');
          if (!resolvedMatch.isNonMatch()) {
            df.addSubfield(MARC_FACTORY.newSubfield(subfieldTag, node.asText()));
          } else {
            Optional<Instant> date = isDate(node.asText());
            if (date.isEmpty()) {
              df.addSubfield(MARC_FACTORY.newSubfield(subfieldTag, "~" + node.asText()));
            } else {
              df.addSubfield(MARC_FACTORY.newSubfield(subfieldTag, date.get().plusSeconds(60).toString()));
            }
          }
        }

        record.addVariableField(df);
      } else if (MatchExpression.DataValueType.STATIC_VALUE.equals(resolvedMatch.getIncomingDataValueType())) {
        if (resolvedMatch.getExisting() instanceof MarcField existingMarcField) {
          Object existing = resolvedMatch.getExisting();
          JsonNode loadedValueJsonNode = getExistingValue(existing);
          DataField df = MARC_FACTORY.newDataField(existingMarcField.getField(),
            CharUtils.toChar(existingMarcField.getIndicator1(), ' '),
            CharUtils.toChar(existingMarcField.getIndicator2(), ' '));
          char subfieldTag = CharUtils.toChar(existingMarcField
            .getSubfields()
            .get(0)
            .getSubfield(), ' ');
          if (!resolvedMatch.isNonMatch()) {
            df.addSubfield(MARC_FACTORY.newSubfield(subfieldTag, loadedValueJsonNode.asText()));
          } else {
            df.addSubfield(MARC_FACTORY.newSubfield(subfieldTag, "~" + loadedValueJsonNode.asText()));
          }
          record.addVariableField(df);
        }
      }
    }

    addVariableFieldConditionally(record, "245",
      () -> MARC_FACTORY.newDataField("245", ' ', ' '),
      dataField -> {
        if (dataField.getSubfields('a') == null) {
          dataField.addSubfield(MARC_FACTORY.newSubfield('a', FAKER.book().title()));
        }
      });

    addVariableFieldConditionally(record, "008",
      () -> MARC_FACTORY.newControlField("008", MARC008Generator.generateRandom008()),
      field -> {
      });

    addVariableFieldConditionally(record, "856",
      () -> MARC_FACTORY.newDataField("856", '4', '0'),
      dataField -> {
        if (dataField.getSubfields('u') == null) {
          dataField.addSubfield(MARC_FACTORY.newSubfield('u', FAKER.internet().url()));
        }
      });

    return Optional.of(record);
  }

  /**
   * Resolves the matches for a given path in the profile graph.
   *
   * @param profileContents The map containing the profile contents, where the keys are the profiles and the values are the corresponding JSON nodes.
   * @param path            The graph path representing the sequence of profiles and edges to resolve matches for.
   * @return A list of resolved matches based on the profiles and edges in the given path.
   * @throws JsonProcessingException If an error occurs while processing the JSON content of a match profile.
   */
  private List<ResolvedMatch> resolveMatches(Map<Profile, JsonNode> profileContents,
                                             GraphPath<Profile, RegularEdge> path) throws JsonProcessingException {
    List<ResolvedMatch> resolvedMatches = new ArrayList<>();
    for (var edge : path.getEdgeList()) {
      Profile vertex = (Profile) edge.getSource();
      if (vertex instanceof MatchProfileNode matchp) {
        boolean isNonMatch = edge instanceof NonMatchRelationshipEdge;

        JsonNode jsonNode = profileContents.get(matchp);
        MatchProfile matchProfile = OBJECT_MAPPER.treeToValue(jsonNode, MatchProfile.class);
        MatchDetail matchDetail = matchProfile.getMatchDetails().get(0); // only one match detail is expected for now
        MatchDetailResolver matchDetailResolver = new MatchDetailResolver();
        resolvedMatches.add(matchDetailResolver.resolve(matchDetail, isNonMatch));
      }
    }
    return resolvedMatches;
  }

  /**
   * Finds all paths from the given job profile to the leaf nodes in the graph.
   *
   * <p>This method uses the {@link AllDirectedPaths} algorithm to find all paths from the job profile
   * to the leaf nodes in the graph. A leaf node is defined as a node with no outgoing edges.
   * The method considers different types of profiles (mapping profiles, action profiles, match profiles)
   * and their respective owning profiles (parent profiles) while determining the leaf nodes.
   *
   * <p>For match profiles, the method includes both the match and non-match profiles linked to the match
   * profile as leaf nodes.
   *
   * @param graph      the graph representing the profiles and their relationships
   * @param jobProfile the starting job profile for finding the paths
   * @return a list of {@link GraphPath} objects representing all paths from the job profile to the leaf nodes
   */
  private List<GraphPath<Profile, RegularEdge>> findAllPathsToLeafNodes(Graph<Profile, RegularEdge> graph, Profile jobProfile) {
    AllDirectedPaths<Profile, RegularEdge> pathFinder = new AllDirectedPaths<>(graph);

    // Find all leaf nodes
    Set<Profile> allLeafNodes = graph.vertexSet().stream()
      .filter(v -> graph.outDegreeOf(v) == 0)
      .map(node -> {
        // convert mapping profile to their owning action profiles
        if (node instanceof MappingProfileNode) {
          return getParent(graph, node);
        }
        return node;
      })
      .map(node -> {
        // convert action profiles to their owning job/match profiles
        if (node instanceof ActionProfileNode) {
          return getParent(graph, node);
        }
        return node;
      })
      .map(node -> {
        // get one profile linked by MATCH and another by NON_MATCH for match profiles
        if (node instanceof MatchProfileNode matchProfileNode) {
          Set<RegularEdge> regularEdges = graph.outgoingEdgesOf(matchProfileNode);
          boolean hasMatch = false;
          boolean hasNonMatch = false;
          List<Profile> result = new ArrayList<>();
          for (RegularEdge regularEdge : regularEdges) {
            if (!hasNonMatch && regularEdge instanceof NonMatchRelationshipEdge) {
              hasNonMatch = true;
              result.add(graph.getEdgeTarget(regularEdge));
            }
            if (!hasMatch && regularEdge instanceof MatchRelationshipEdge) {
              hasMatch = true;
              result.add(graph.getEdgeTarget(regularEdge));
            }
          }
          if (!hasMatch && !hasNonMatch) return List.of(node);
          return result;
        }
        // return other non-match profiles as is
        return List.of(node);
      })
      .flatMap(Collection::stream)
      .collect(Collectors.toSet());

    return pathFinder.getAllPaths(Set.of(jobProfile), allLeafNodes, true, null);
  }

  private static Profile getParent(Graph<Profile, RegularEdge> graph, Profile vertex) {
    return graph.incomingEdgesOf(vertex).stream()
      .map(graph::getEdgeSource)
      .findFirst()
      .orElse(null);
  }

  private JsonNode getExistingValue(Object existing) {
    JsonNode loadedValueJsonNode = null;
    if (existing instanceof Field existingField) {
      String objectType = StringUtils.substringBefore(existingField.getValue(), JSON_PATH_SEPARATOR);
      String path = StringUtils.substringAfter(existingField.getValue(), JSON_PATH_SEPARATOR);
      loadedValueJsonNode = loadValue(objectType, path);
    } else if (existing instanceof MarcField existingMarcField) {
      loadedValueJsonNode = loadMarcValue(existingMarcField);
    }
    return loadedValueJsonNode;
  }

  /**
   * Conditionally adds a variable field to a MARC record.
   *
   * <p>If the specified variable field with the given tag does not exist in the record,
   * a new variable field is created using the provided {@code createVariableField} supplier
   * and added to the record. The {@code configureVariableField} consumer is then applied to
   * the variable field, allowing for further configuration such as adding subfields.
   *
   * <p>If the specified variable field already exists in the record, the
   * {@code configureVariableField} consumer is applied directly to the existing variable field.
   *
   * @param <T>                    the type of the variable field, which must extend {@link VariableField}
   * @param record                 the MARC record to which the variable field will be added
   * @param tag                    the tag identifying the variable field
   * @param createVariableField    a supplier function that creates a new instance of the variable field
   * @param configureVariableField a consumer function that configures the variable field, such as adding subfields
   */
  private <T extends VariableField> void addVariableFieldConditionally(Record record, String tag,
                                                                       Supplier<T> createVariableField,
                                                                       Consumer<T> configureVariableField) {
    T variableField = (T) record.getVariableField(tag);
    if (variableField == null) {
      variableField = createVariableField.get();
      record.addVariableField(variableField);
    }
    configureVariableField.accept(variableField);
  }

  /**
   * Loads a value from the specified object type and JSON path.
   *
   * @param objectType The type of the object.
   * @param jsonPath   The JSON path to the value.
   * @return The loaded value as a JsonNode.
   * @throws RuntimeException If the value cannot be loaded.
   */
  private JsonNode loadValue(String objectType, String jsonPath) {
    Optional<JsonNode> result = Optional.empty();
    switch (objectType) {
      case "instance" -> {
        result = client.getInstance(identifiers.get().instanceId());
      }
      case "holdingsrecord" -> {
        result = client.getHoldings(identifiers.get().holdingsId());
      }
      case "item" -> {
        result = client.getItem(identifiers.get().itemId());
      }
      case "sourcerecord" -> {
        result = client.getSourceRecordBySRSId(identifiers.get().sourceRecordId());
      }
    }
    if (result.isEmpty()) {
      LOGGER.error("could not find record for {}", objectType);
      return MissingNode.getInstance();
    }

    // the normalization occurs because follow using CQL which is close to JsonPath but not exactly.
    String normalizedJsonPath = jsonPath.replace("[]", "[*]");
    return using(JACKSON_JSON_NODE_CONFIGURATION)
      .parse(result.get())
      .read(normalizedJsonPath);

  }

  private JsonNode loadMarcValue(MarcField marcField) {
    JsonNode fieldsArray = loadValue("sourcerecord", "parsedRecord.content.fields");
    if (!(fieldsArray instanceof ArrayNode arrayNode)) {
      LOGGER.error("Something was wrong when getting MARC fields. It wasn't an array. json={}", fieldsArray);
      return MissingNode.getInstance();
    }

    for (JsonNode fieldNode : arrayNode) {
      JsonNode foundFieldNode = fieldNode.get(marcField.getField());
      if (foundFieldNode == null) continue;

      JsonNode ind1Node = foundFieldNode.path("ind1");
      JsonNode ind2Node = foundFieldNode.path("ind2");

      if (!ind1Node.asText().equals(marcField.getIndicator1()) ||
        !ind2Node.asText().equals(marcField.getIndicator2())) {
        continue;
      }

      JsonNode subfieldsNode = foundFieldNode.path("subfields");
      if (subfieldsNode.isArray()) {
        Optional<MarcSubfield> first = marcField.getSubfields().stream().findFirst(); // there should be only one subfield
        if (first.isEmpty()) continue;
        for (JsonNode subfieldNode : subfieldsNode) {
          JsonNode valueNode = subfieldNode.path(first.get().getSubfield());
          if (valueNode != null) {
            return valueNode;
          }
        }
      }
      break;
    }
    return MissingNode.getInstance();
  }

  /**
   * Checks if the given string represents a date.
   *
   * @param dateString The string to check.
   * @return An Optional containing the parsed Instant if the string is a valid date, or an empty Optional otherwise.
   */
  private static Optional<Instant> isDate(String dateString) {
    try {
      return Optional.of(Instant.parse(dateString));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

}
