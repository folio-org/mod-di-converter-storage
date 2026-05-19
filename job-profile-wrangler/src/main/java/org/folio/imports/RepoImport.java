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
import org.folio.validation.ProfileShapeValidator;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.folio.Constants.OBJECT_MAPPER;

public class RepoImport implements Runnable {
  private static final Logger LOGGER = LogManager.getLogger(RepoImport.class);
  private final FolioClient client;
  private final String repoPath;


  public RepoImport(FolioClient client, String repoPath) {
    this.client = client;
    this.repoPath = repoPath;
  }

  @Override
  public void run() {
    importProfiles();
  }

  public ImportReport importProfiles() {
    List<ImportReport.Entry> entries = new ArrayList<>();

    client.getJobProfiles()
      .forEach(profile -> {
        String profileId = profile.get("id").asText();
        String profileName = profile.path("name").asText(profileId);
        Optional<JsonNode> jobProfileSnapshotOptional = client.getJobProfileSnapshot(profileId);

        if (jobProfileSnapshotOptional.isEmpty()) {
          LOGGER.warn("Failed to fetch snapshot for profile: id={}, name={}", profileId, profileName);
          entries.add(new ImportReport.Entry(profileId, profileName,
            new ImportOutcome.ImportError("Failed to fetch job profile snapshot")));
          return;
        }

        ImportReport.Entry entry = importSnapshot(repoPath, profileId, profileName, jobProfileSnapshotOptional.get());
        entries.add(entry);
        LOGGER.info("Import profile outcome: id={}, name={}, outcome={}",
          profileId, profileName, entry.outcome().label());
      });

    ImportReport report = new ImportReport(Instant.now().toString(), entries);
    LOGGER.info("Import complete: Added: {}, Duplicate: {}, Blocked: {}, Errors: {}",
      report.addedCount(), report.duplicateCount(), report.blockedCount(), report.errorCount());
    return report;
  }

  public static Optional<RepoObject> fromString(String repoPath, String json) throws JsonProcessingException {
    JsonNode jsonNode = OBJECT_MAPPER.readTree(json);
    return fromString(repoPath, jsonNode);
  }

  public static Optional<RepoObject> fromString(String repoPath, JsonNode jsonNode) {
    ImportReport.Entry entry = importSnapshot(repoPath,
      jsonNode.path("content").path("id").asText(null),
      jsonNode.path("content").path("name").asText(null),
      jsonNode);
    if (entry.outcome() instanceof ImportOutcome.Added added) {
      return Optional.of(new RepoObject(added.repoId(), buildGraph(new SimpleDirectedGraph<>(RegularEdge.class), jsonNode)));
    }
    if (entry.outcome() instanceof ImportOutcome.Duplicate duplicate) {
      return Optional.of(new RepoObject(duplicate.existingRepoId(), buildGraph(new SimpleDirectedGraph<>(RegularEdge.class), jsonNode)));
    }
    return Optional.empty();
  }

  public static ImportReport.Entry importSnapshot(String repoPath, String profileId, String profileName, JsonNode jsonNode) {
    var blocked = ProfileShapeValidator.defaultValidator().validate(jsonNode);
    if (blocked.isPresent()) {
      return new ImportReport.Entry(profileId, profileName,
        new ImportOutcome.BlockedUnsupported(blocked.get().rule(), blocked.get().message()));
    }

    Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
    buildGraph(g, jsonNode);
    var searched = GraphReader.search(repoPath, g);
    if (searched.isEmpty()) {
      Optional<Integer> repoId = GraphWriter.writeGraph(repoPath, g);
      if (repoId.isPresent()) {
        return new ImportReport.Entry(profileId, profileName, new ImportOutcome.Added(repoId.get()));
      } else {
        LOGGER.error("Failed to write graph to repository: {}", g);
        return new ImportReport.Entry(profileId, profileName,
          new ImportOutcome.ImportError("Failed to write graph to repository"));
      }
    } else {
      LOGGER.info("Graph already exists. graph={}", g);
      return new ImportReport.Entry(profileId, profileName, new ImportOutcome.Duplicate(searched.get().repoId()));
    }
  }

  private static Graph<Profile, RegularEdge> buildGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot) {
    addProfileToGraph(graph, profileSnapshot);
    return graph;
  }

  private static Optional<Profile> addProfileToGraph(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot) {
    return createProfileNode(profileSnapshot)
      .map(node -> {
        graph.addVertex(node);
        addChildren(graph, profileSnapshot, node);
        return node;
      });
  }

  private static Optional<Profile> createProfileNode(JsonNode profileSnapshot) {
    String contentType = profileSnapshot.path("contentType").asText();
    String id = profileSnapshot.path("profileWrapperId").asText();
    int order = profileSnapshot.path("order").asInt();

    return switch (contentType) {
      case "JOB_PROFILE" -> Optional.of(new JobProfileNode(id,
        profileSnapshot.path("content").path("dataType").asText(), order));

      case "MATCH_PROFILE" -> Optional.of(new MatchProfileNode(id,
        profileSnapshot.path("content").path("incomingRecordType").asText(),
        profileSnapshot.path("content").path("existingRecordType").asText(),
        order));

      case "ACTION_PROFILE" -> Optional.of(new ActionProfileNode(id,
        profileSnapshot.path("content").path("action").asText(),
        profileSnapshot.path("content").path("folioRecord").asText(),
        order));

      case "MAPPING_PROFILE" -> Optional.of(new MappingProfileNode(id,
        profileSnapshot.path("content").path("incomingRecordType").asText(),
        profileSnapshot.path("content").path("existingRecordType").asText(),
        order));

      default -> Optional.empty();
    };
  }

  private static void addChildren(Graph<Profile, RegularEdge> graph, JsonNode profileSnapshot, Profile node) {
    Optional.ofNullable(profileSnapshot.get("childSnapshotWrappers"))
      .filter(JsonNode::isArray)
      .ifPresent(children -> {
        if (node instanceof MatchProfileNode matchNode) {
          addMatchChildren(graph, children, matchNode);
        } else {
          addRegularChildren(graph, children, node);
        }
      });
  }

  private static void addRegularChildren(Graph<Profile, RegularEdge> graph, JsonNode children, Profile parent) {
    StreamSupport.stream(children.spliterator(), false)
      .map(child -> addProfileToGraph(graph, child))
      .flatMap(Optional::stream)
      .forEach(child -> graph.addEdge(parent, child));
  }

  private static void addMatchChildren(Graph<Profile, RegularEdge> graph, JsonNode children, MatchProfileNode parent) {
    StreamSupport.stream(children.spliterator(), false)
      .forEach(child -> addProfileToGraph(graph, child)
        .ifPresent(profile -> addMatchEdge(graph, parent, profile, child.path("reactTo").asText())));
  }

  private static void addMatchEdge(Graph<Profile, RegularEdge> graph, Profile parent, Profile child, String reactTo) {
    switch (reactTo) {
      case "NON_MATCH" -> graph.addEdge(parent, child, new NonMatchRelationshipEdge());
      case "MATCH" -> graph.addEdge(parent, child, new MatchRelationshipEdge());
      default -> LOGGER.warn("Skipping child profile due to invalid reactTo value '{}' for parent: {}", reactTo, parent);
    }
  }
}
