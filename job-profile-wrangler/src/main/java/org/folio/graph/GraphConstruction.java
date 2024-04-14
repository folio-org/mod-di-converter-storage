package org.folio.graph;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GraphConstruction {
  private static final Logger LOGGER = LogManager.getLogger();

  private Graph<Profile, RegularEdge> graph;
  private JsonNode profileSnapshot;
  private Map<Profile, JsonNode> profileContents = new HashMap<>();

  public GraphConstruction(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot) {
    this.graph = graph;
    this.profileSnapshot = profileSnapshot;
  }

  public Graph<Profile, RegularEdge> getGraph() {
    return graph;
  }

  public Map<Profile, JsonNode> getProfileContents() {
    return Collections.unmodifiableMap(profileContents);
  }

  public Optional<Profile> construct() {
    return constructInternal(graph, profileSnapshot);
  }

  public Optional<Profile> constructInternal(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot) {
    String contentType = profileSnapshot.path("contentType").asText();

    return switch (contentType) {
      case "JOB_PROFILE" -> {
        String dataType = profileSnapshot.path("content").path("dataType").asText();
        String id = profileSnapshot.path("profileWrapperId").asText();
        int order = profileSnapshot.path("order").asInt();
        JobProfileNode node = new JobProfileNode(id, dataType, order);
        graph.addVertex(node);
        profileContents.put(node, profileSnapshot.path("content"));
        addChildProfilesToGraph(graph, profileSnapshot, node);
        yield Optional.of(node);
      }
      case "MATCH_PROFILE" -> {
        String incomingRecordType = profileSnapshot.path("content").path("incomingRecordType").asText();
        String existingRecordType = profileSnapshot.path("content").path("existingRecordType").asText();
        String id = profileSnapshot.path("profileWrapperId").asText();
        int order = profileSnapshot.path("order").asInt();
        MatchProfileNode node = new MatchProfileNode(id, incomingRecordType, existingRecordType, order);
        graph.addVertex(node);
        profileContents.put(node, profileSnapshot.path("content"));
        addChildMatchProfilesToGraph(graph, profileSnapshot, node);
        yield Optional.of(node);
      }
      case "ACTION_PROFILE" -> {
        String folioRecord = profileSnapshot.path("content").path("folioRecord").asText();
        String actionType = profileSnapshot.path("content").path("action").asText();
        String id = profileSnapshot.path("profileWrapperId").asText();
        int order = profileSnapshot.path("order").asInt();
        ActionProfileNode node = new ActionProfileNode(id, actionType, folioRecord, order);
        graph.addVertex(node);
        profileContents.put(node, profileSnapshot.path("content"));
        addChildProfilesToGraph(graph, profileSnapshot, node);
        yield Optional.of(node);
      }
      case "MAPPING_PROFILE" -> {
        String incomingRecordType = profileSnapshot.path("content").path("incomingRecordType").asText();
        String existingRecordType = profileSnapshot.path("content").path("existingRecordType").asText();
        String id = profileSnapshot.path("profileWrapperId").asText();
        int order = profileSnapshot.path("order").asInt();
        MappingProfileNode node = new MappingProfileNode(id, incomingRecordType, existingRecordType, order);
        graph.addVertex(node);
        profileContents.put(node, profileSnapshot.path("content"));
        JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray() && !childSnapshotWrappers.isEmpty()) {
          LOGGER.warn("Found {} childSnapshotWrappers for mapping profile: {}", childSnapshotWrappers.size(), node);
        }
        yield Optional.of(node);
      }
      default -> Optional.empty();
    };
  }

  private void addChildProfilesToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot, Profile node) {
    JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
    if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
      for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
        Optional<Profile> profile = constructInternal(graph, childSnapshotWrapper);
        profile.ifPresent(value -> graph.addEdge(node, value));
      }
    }
  }

  private void addChildMatchProfilesToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot, MatchProfileNode node) {
    JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
    if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
      for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
        Optional<Profile> profile = constructInternal(graph, childSnapshotWrapper);
        profile.ifPresent(value -> {
          String reactTo = childSnapshotWrapper.path("reactTo").asText();
          if ("NON_MATCH".equals(reactTo)) {
            graph.addEdge(node, value, new NonMatchRelationshipEdge());
          } else if ("MATCH".equals(reactTo)) {
            graph.addEdge(node, value, new MatchRelationshipEdge());
          } else {
            LOGGER.warn("Found invalid relationship for matching on match profile: {}", node);
          }
        });
      }
    }
  }
}
