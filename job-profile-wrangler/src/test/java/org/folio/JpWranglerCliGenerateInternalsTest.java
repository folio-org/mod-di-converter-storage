package org.folio;

import org.folio.exports.CategorizedPath;
import org.folio.exports.CategorizedPaths;
import org.folio.exports.EnrichmentDetector;
import org.folio.exports.GenerationOutcome;
import org.folio.exports.JobProfilePath;
import org.folio.exports.MatchedPathPair;
import org.folio.exports.MatchCriteria;
import org.folio.exports.PathExtractionResult;
import org.folio.exports.ReactTo;
import org.folio.exports.StrictRecordWriter;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.Profile;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JpWranglerCliGenerateInternalsTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void existingFieldPathUsesFieldValuesFromFolioMatchExpression() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("extractExistingFieldPath", JsonNode.class);
    method.setAccessible(true);
    JsonNode fields = OBJECT_MAPPER.readTree("""
      [
        {"label":"field","value":"instance"},
        {"label":"field","value":"hrid"}
      ]
      """);

    Object result = method.invoke(new JpWranglerCli.GenerateCommand(), fields);

    assertEquals("instance.hrid", result);
  }

  @Test
  public void enrichmentDetectionUsesStrictWriterPathOrderForPairedPaths() {
    CategorizedPath create = path("create", ReactTo.NON_MATCH, MatchCriteria.empty());
    CategorizedPath update = path("update", ReactTo.MATCH, nonMarcCriteria());
    CategorizedPaths categorized = new CategorizedPaths(
      List.of(new MatchedPathPair(create, update, "match-1")),
      List.of(create),
      List.of(update)
    );

    List<CategorizedPath> ordered = new StrictRecordWriter().pathOrder(categorized);
    Map<Integer, GenerationOutcome.NeedsEnrichment> enrichment =
      EnrichmentDetector.detect(ordered, "records");

    assertEquals("update", ordered.get(0).path().getPathId());
    assertTrue(enrichment.containsKey(0));
    assertEquals("update", enrichment.get(0).pathId());
  }

  @Test
  public void extractionKeepsUnsupportedActionPathsSeparateFromEmptyProfiles() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("extractAllPaths", JsonNode.class);
    method.setAccessible(true);
    JsonNode snapshot = OBJECT_MAPPER.readTree("""
      {
        "contentType": "JOB_PROFILE",
        "content": {"id": "job", "dataType": "MARC", "order": 0},
        "childSnapshotWrappers": [{
          "contentType": "MATCH_PROFILE",
          "reactTo": "MATCH",
          "content": {
            "id": "match",
            "incomingRecordType": "MARC_AUTHORITY",
            "existingRecordType": "MARC_AUTHORITY",
            "order": 0,
            "matchDetails": []
          },
          "childSnapshotWrappers": [{
            "contentType": "ACTION_PROFILE",
            "content": {
              "id": "delete",
              "action": "DELETE",
              "folioRecord": "MARC_AUTHORITY",
              "order": 0
            },
            "childSnapshotWrappers": [{
              "contentType": "MAPPING_PROFILE",
              "content": {
                "id": "mapping",
                "incomingRecordType": "MARC_AUTHORITY",
                "existingRecordType": "MARC_AUTHORITY",
                "order": 0
              }
            }]
          }]
        }]
      }
      """);

    PathExtractionResult result = (PathExtractionResult) method.invoke(new JpWranglerCli.GenerateCommand(), snapshot);

    assertTrue(result.createPaths().isEmpty());
    assertTrue(result.updatePaths().isEmpty());
    assertEquals(1, result.unsupportedActionPaths().size());
    assertEquals(ReactTo.MATCH, result.unsupportedActionPaths().get(0).reactTo());
    assertTrue(result.unsupportedActionPaths().get(0).path().getPathId().contains("DELETE_MARC_AUTHORITY"));
  }

  private CategorizedPath path(String pathId, ReactTo reactTo, MatchCriteria criteria) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job", "MARC", 0),
      new ActionProfileNode("action-" + pathId, "UPDATE", "INSTANCE", 0)
    );
    return new CategorizedPath(new JobProfilePath(profiles, pathId), reactTo, "match-1", criteria);
  }

  private MatchCriteria nonMarcCriteria() {
    return new MatchCriteria(
      "match-1",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "a", null)),
      List.of(new MatchCriteria.NonMarcMatchSpec("instance.hrid", "999", "a", "f", "f"))
    );
  }
}
