package org.folio.imports;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.RepoObject;
import org.folio.graph.GraphReader;
import org.folio.graph.GraphWriter;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.Optional;

import static org.folio.Constants.OBJECT_MAPPER;

public class RepoImport implements Runnable {
  private static final Logger LOGGER = LogManager.getLogger();
  private final FolioClient client;
  private final String repoPath;


  public RepoImport(FolioClient client, String repoPath) {
    this.client = client;
    this.repoPath = repoPath;
  }

  @Override
  public void run() {
    client.getJobProfiles()
      .forEach(profile -> {
        String profileId = profile.get("id").asText();
        Optional<JsonNode> jobProfileSnapshotOptional = client.getJobProfileSnapshot(profileId);
        jobProfileSnapshotOptional.ifPresent(json -> fromString(repoPath, json));
      });

    LOGGER.info("DONE");
  }

  public static Optional<RepoObject> fromString(String repoPath, String json) throws JsonProcessingException {
    JsonNode jsonNode = OBJECT_MAPPER.readTree(json);
    return fromString(repoPath, jsonNode);
  }

  public static Optional<RepoObject> fromString(String repoPath, JsonNode jsonNode) {
    Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
    addProfileToGraph(g, jsonNode);
    var searched = GraphReader.search(repoPath, g);
    if (searched.isEmpty()) {
      Optional<Integer> repoId = GraphWriter.writeGraph(repoPath, g);
      if (repoId.isPresent()) {
        return Optional.of(new RepoObject(repoId.get(), g));
      }
    } else {
      LOGGER.info("Graph already exists. graph={}", g);
      return searched;
    }
    return Optional.empty();
  }

  private static Optional<Profile> addProfileToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot) {
    String contentType = profileSnapshot.path("contentType").asText();

    return switch (contentType) {
      case "JOB_PROFILE" -> {
        String dataType = profileSnapshot.path("content").path("dataType").asText();
        String id = profileSnapshot.path("profileWrapperId").asText();
        int order = profileSnapshot.path("order").asInt();
        JobProfileNode node = new JobProfileNode(id, dataType, order);
        graph.addVertex(node);
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
        JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray() && !childSnapshotWrappers.isEmpty()) {
          LOGGER.warn("Found {} childSnapshotWrappers for mapping profile: {}", childSnapshotWrappers.size(), node);
        }
        yield Optional.of(node);
      }
      default -> Optional.empty();
    };
  }

  private static void addChildProfilesToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot, Profile node) {
    JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
    if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
      for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
        Optional<Profile> profile = addProfileToGraph(graph, childSnapshotWrapper);
        profile.ifPresent(value -> graph.addEdge(node, value));
      }
    }
  }

  private static void addChildMatchProfilesToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot, MatchProfileNode node) {
    JsonNode childSnapshotWrappers = profileSnapshot.get("childSnapshotWrappers");
    if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
      for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
        Optional<Profile> profile = addProfileToGraph(graph, childSnapshotWrapper);
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
