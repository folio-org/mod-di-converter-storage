package org.folio.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProfileTreeTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void normalizesSnapshotAliasesAndChildOrder() throws Exception {
    JsonNode snapshot = OBJECT_MAPPER.readTree("""
      {
        "profileWrapperId": "wrapper-job",
        "profileType": "JOB_PROFILE",
        "content": {"id": "content-job", "name": "Job", "dataType": "MARC"},
        "childrenWrappers": [
          {
            "profileWrapperId": "wrapper-action",
            "profileType": "ACTION_PROFILE",
            "reactionStatus": "NON_MATCH",
            "order": 2,
            "content": {"id": "content-action", "action": "CREATE", "folioRecord": "INSTANCE"}
          },
          {
            "profileWrapperId": "wrapper-match",
            "profileType": "MATCH_PROFILE",
            "reactionStatus": "MATCH",
            "order": 1,
            "content": {
              "id": "content-match",
              "incomingRecordType": "MARC_BIBLIOGRAPHIC",
              "existingRecordType": "INSTANCE"
            }
          }
        ]
      }
      """);

    ProfileTree tree = ProfileTree.fromSnapshot(snapshot);

    assertEquals("content-job", tree.profileId("fallback"));
    assertEquals("MATCH", tree.root().children().get(0).reactTo());
    assertEquals("NON_MATCH", tree.root().children().get(1).reactTo());

    Graph<Profile, RegularEdge> contentGraph = tree.toGraph(ProfileTree.ProfileIdSource.CONTENT);
    assertTrue(contentGraph.containsVertex(new JobProfileNode("content-job", "MARC", 0)));
    assertTrue(contentGraph.containsVertex(
      new MatchProfileNode("content-match", "MARC_BIBLIOGRAPHIC", "INSTANCE", 1)));

    Graph<Profile, RegularEdge> wrapperGraph = tree.toGraph(ProfileTree.ProfileIdSource.WRAPPER);
    assertTrue(wrapperGraph.containsVertex(new JobProfileNode("wrapper-job", "MARC", 0)));
    assertTrue(wrapperGraph.containsVertex(new ActionProfileNode("wrapper-action", "CREATE", "INSTANCE", 2)));

    List<String> edgeLabels = wrapperGraph.edgeSet().stream()
      .map(RegularEdge::getLabel)
      .toList();
    assertTrue(edgeLabels.contains("MATCH"));
    assertTrue(edgeLabels.contains("NON_MATCH"));
  }

  @Test
  public void orderedChildrenAcceptsWrapperNodeOrRawChildrenArray() throws Exception {
    JsonNode snapshot = OBJECT_MAPPER.readTree("""
      {
        "childSnapshotWrappers": [
          {"order": 2, "content": {"id": "second"}},
          {"order": 1, "content": {"id": "first"}}
        ]
      }
      """);

    List<JsonNode> fromWrapper = ProfileTree.orderedChildren(snapshot);
    List<JsonNode> fromArray = ProfileTree.orderedChildren(snapshot.path("childSnapshotWrappers"));

    assertEquals("first", fromWrapper.get(0).path("content").path("id").asText());
    assertEquals("first", fromArray.get(0).path("content").path("id").asText());
  }
}
