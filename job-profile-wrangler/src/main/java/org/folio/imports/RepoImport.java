package org.folio.imports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.JobProfileGetter;
import org.folio.graph.ActionProfileNode;
import org.folio.graph.JobProfileNode;
import org.folio.graph.MappingProfileNode;
import org.folio.graph.MatchProfileNode;
import org.folio.graph.MatchRelationshipEdge;
import org.folio.graph.NonMatchRelationshipEdge;
import org.folio.graph.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Supplier;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;

public class RepoImport implements Runnable {
  private final static Logger LOGGER = LogManager.getLogger();
  private final OkHttpClient client;
  private final Supplier<HttpUrl.Builder> urlBuilder;
  private final String token;
  private final String repoPath;
  final private ExecutorService executor;
  private final BlockingQueue<JsonNode> blockingQueue = new LinkedBlockingDeque<>(5);

  public RepoImport(OkHttpClient client, Supplier<HttpUrl.Builder> urlBuilder,
                    String token, ExecutorService executor, String repoPath) {
    this.client = client;
    this.urlBuilder = urlBuilder;
    if (token == null) {
      throw new RuntimeException("Couldn't get okapi token");
    }
    this.token = token;
    this.executor = executor;
    this.repoPath = repoPath;
  }

  @Override
  public void run() {

    CompletableFuture
      .runAsync(new JobProfileGetter(client, urlBuilder, token, blockingQueue), executor);

    try {
      Supplier<HttpUrl.Builder> profileSnapshotsUrlBuilder = () -> urlBuilder.get()
        .addPathSegments("data-import-profiles/profileSnapshots")
        .addQueryParameter("profileType", "JOB_PROFILE");

      JsonNode content = blockingQueue.take();
      JsonNode jobProfiles = content.get("jobProfiles");
      if (jobProfiles instanceof ArrayNode jobProfilesArr) {
        jobProfilesArr.forEach(profile -> {
          String profileId = profile.get("id").asText();
          HttpUrl url = profileSnapshotsUrlBuilder.get()
            .addPathSegment(profileId)
            .addQueryParameter("jobProfileId", profileId)
            .build();
          Request request = new Request.Builder()
            .url(url)
            .addHeader(OKAPI_TOKEN_HEADER, token)
            .get()
            .build();
          try (Response response = client.newCall(request).execute()) {
            String result = response.body().string();
            JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
            Graph<Profile, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);

            addProfile(g, jsonNode);

            GraphWriter.writeGraph(repoPath, g);

          } catch (IOException e) {
            LOGGER.error("Something happened while querying for profile snapshot", e);
          }

        });
      }
    } catch (InterruptedException e) {
      LOGGER.warn("VertexBuilder interrupted");
    }

    LOGGER.info("DONE");
  }

  private Optional<Profile> addProfile(Graph<Profile, DefaultEdge> graph, JsonNode jobProfileSnapshot) {
    String id = jobProfileSnapshot.path("profileId").asText();
    String contentType = jobProfileSnapshot.path("contentType").asText();

    switch (contentType) {
      case "JOB_PROFILE" -> {
        String dataType = jobProfileSnapshot.path("content").path("dataType").asText();
        JobProfileNode node = new JobProfileNode(id, dataType);
        graph.addVertex(node);
        JsonNode childSnapshotWrappers = jobProfileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
          for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
            Optional<Profile> profile = addProfile(graph, childSnapshotWrapper);
            profile.ifPresent(value -> graph.addEdge(node, value));
          }
        }
        return Optional.of(node);
      }
      case "MATCH_PROFILE" -> {
        String incomingRecordType = jobProfileSnapshot.path("content").path("incomingRecordType").asText();
        String existingRecordType = jobProfileSnapshot.path("content").path("existingRecordType").asText();
        MatchProfileNode node = new MatchProfileNode(id, incomingRecordType, existingRecordType);
        graph.addVertex(node);
        JsonNode childSnapshotWrappers = jobProfileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
          for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
            Optional<Profile> profile = addProfile(graph, childSnapshotWrapper);
            profile.ifPresent(value -> {
              String reactTo = childSnapshotWrapper.path("reactTo").asText();
              if ("NON_MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new NonMatchRelationshipEdge());
              } else if ("MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new MatchRelationshipEdge());
              } else {
                graph.addEdge(node, value);
              }
            });
          }
        }
        return Optional.of(node);
      }
      case "ACTION_PROFILE" -> {
        String folioRecord = jobProfileSnapshot.path("content").path("folioRecord").asText();
        String actionType = jobProfileSnapshot.path("content").path("action").asText();
        ActionProfileNode node = new ActionProfileNode(id, actionType, folioRecord);
        graph.addVertex(node);
        JsonNode childSnapshotWrappers = jobProfileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
          for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
            Optional<Profile> profile = addProfile(graph, childSnapshotWrapper);
            profile.ifPresent(value -> {
              String reactTo = childSnapshotWrapper.path("reactTo").asText();
              if ("NON_MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new NonMatchRelationshipEdge());
              } else if ("MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new MatchRelationshipEdge());
              } else {
                graph.addEdge(node, value);
              }
            });
          }
        }
        return Optional.of(node);
      }
      case "MAPPING_PROFILE" -> {
        String incomingRecordType = jobProfileSnapshot.path("content").path("incomingRecordType").asText();
        String existingRecordType = jobProfileSnapshot.path("content").path("existingRecordType").asText();
        MappingProfileNode node = new MappingProfileNode(id, incomingRecordType, existingRecordType);
        graph.addVertex(node);
        JsonNode childSnapshotWrappers = jobProfileSnapshot.get("childSnapshotWrappers");
        if (childSnapshotWrappers != null && childSnapshotWrappers.isArray()) {
          for (JsonNode childSnapshotWrapper : childSnapshotWrappers) {
            Optional<Profile> profile = addProfile(graph, childSnapshotWrapper);
            profile.ifPresent(value -> {
              String reactTo = childSnapshotWrapper.path("reactTo").asText();
              if ("NON_MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new NonMatchRelationshipEdge());
              } else if ("MATCH".equals(reactTo)) {
                graph.addEdge(node, value, new MatchRelationshipEdge());
              } else {
                graph.addEdge(node, value);
              }
            });
          }
        }
        return Optional.of(node);
      }
      default -> {
        return Optional.empty();
      }
    }
  }
}
