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
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
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
      List.of(update),
      List.of()
    );

    List<CategorizedPath> ordered = new StrictRecordWriter().pathOrder(categorized);
    Map<Integer, GenerationOutcome.NeedsEnrichment> enrichment =
      EnrichmentDetector.detect(ordered, "records");

    assertEquals("update", ordered.get(0).path().getPathId());
    assertTrue(enrichment.containsKey(0));
    assertEquals("update", enrichment.get(0).pathId());
  }

  @Test
  public void extractionRecognizesAuthorityDeletePaths() throws Exception {
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
    assertTrue(result.unsupportedActionPaths().isEmpty());
    assertEquals(1, result.deletePaths().size());
    assertEquals(ReactTo.MATCH, result.deletePaths().get(0).reactTo());
    assertTrue(result.deletePaths().get(0).path().getPathId().contains("DELETE_MARC_AUTHORITY"));
  }

  @Test
  public void extractionRecognizesMatchMarcAuthorityUpdatePaths() throws Exception {
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
              "id": "update",
              "action": "UPDATE",
              "folioRecord": "MARC_AUTHORITY",
              "order": 0
            },
            "childSnapshotWrappers": [{
              "contentType": "MAPPING_PROFILE",
              "content": {
                "id": "mapping",
                "incomingRecordType": "MARC_AUTHORITY",
                "existingRecordType": "MARC_AUTHORITY",
                "order": 0,
                "mappingDetails": {"recordType": "MARC_AUTHORITY", "marcMappingOption": "UPDATE"}
              }
            }]
          }]
        }]
      }
      """);

    PathExtractionResult result = (PathExtractionResult) method.invoke(new JpWranglerCli.GenerateCommand(), snapshot);

    assertTrue(result.createPaths().isEmpty());
    assertEquals(1, result.updatePaths().size());
    assertTrue(result.deletePaths().isEmpty());
    assertTrue(result.unsupportedActionPaths().isEmpty());
    assertEquals(ReactTo.MATCH, result.updatePaths().get(0).reactTo());
    assertTrue(result.updatePaths().get(0).path().getPathId().contains("UPDATE_MARC_AUTHORITY"));
  }

  @Test
  public void extractionTreatsModifyMarcBibliographicAsUpdateLike() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("extractAllPaths", JsonNode.class);
    method.setAccessible(true);
    JsonNode snapshot = OBJECT_MAPPER.readTree("""
      {
        "contentType": "JOB_PROFILE",
        "content": {"id": "job", "dataType": "MARC", "order": 0},
        "childSnapshotWrappers": [
          {
            "contentType": "ACTION_PROFILE",
            "content": {"id": "create", "action": "CREATE", "folioRecord": "INSTANCE", "order": 0},
            "childSnapshotWrappers": [{
              "contentType": "MAPPING_PROFILE",
              "content": {"id": "map-create", "incomingRecordType": "MARC_BIBLIOGRAPHIC", "existingRecordType": "INSTANCE"}
            }]
          },
          {
            "contentType": "ACTION_PROFILE",
            "content": {"id": "modify", "action": "MODIFY", "folioRecord": "MARC_BIBLIOGRAPHIC", "order": 1},
            "childSnapshotWrappers": [{
              "contentType": "MAPPING_PROFILE",
              "content": {"id": "map-modify", "incomingRecordType": "MARC_BIBLIOGRAPHIC", "existingRecordType": "MARC_BIBLIOGRAPHIC"}
            }]
          }
        ]
      }
      """);

    PathExtractionResult result = (PathExtractionResult) method.invoke(new JpWranglerCli.GenerateCommand(), snapshot);

    assertEquals(1, result.createPaths().size());
    assertEquals(1, result.updatePaths().size());
    assertTrue(result.unsupportedActionPaths().isEmpty());
    assertTrue(result.updatePaths().get(0).path().getPathId().contains("MODIFY_MARC_BIBLIOGRAPHIC"));
  }

  @Test
  public void extractionKeepsAncestorMarcMatchCriteriaForChainedMatches() throws Exception {
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
            "id": "match-bib",
            "incomingRecordType": "MARC_BIBLIOGRAPHIC",
            "existingRecordType": "MARC_BIBLIOGRAPHIC",
            "order": 0,
            "matchDetails": [{
              "incomingMatchExpression": {
                "dataValueType": "VALUE_FROM_RECORD",
                "fields": [
                  {"label": "field", "value": "999"},
                  {"label": "indicator1", "value": "f"},
                  {"label": "indicator2", "value": "f"},
                  {"label": "recordSubfield", "value": "s"}
                ]
              },
              "existingMatchExpression": {
                "dataValueType": "VALUE_FROM_RECORD",
                "fields": [
                  {"label": "field", "value": "999"},
                  {"label": "indicator1", "value": "f"},
                  {"label": "indicator2", "value": "f"},
                  {"label": "recordSubfield", "value": "s"}
                ]
              }
            }]
          },
          "childSnapshotWrappers": [{
            "contentType": "MATCH_PROFILE",
            "reactTo": "MATCH",
            "content": {
              "id": "match-holdings",
              "incomingRecordType": "STATIC_VALUE",
              "existingRecordType": "HOLDINGS",
              "order": 1,
              "matchDetails": [{
                "incomingMatchExpression": {
                  "dataValueType": "STATIC_VALUE",
                  "fields": [],
                  "staticValueDetails": {"text": "false"}
                },
                "existingMatchExpression": {
                  "dataValueType": "VALUE_FROM_RECORD",
                  "fields": [{"label": "field", "value": "holdingsrecord.discoverySuppress"}]
                }
              }]
            },
            "childSnapshotWrappers": [{
              "contentType": "ACTION_PROFILE",
              "content": {"id": "update", "action": "UPDATE", "folioRecord": "HOLDINGS", "order": 0},
              "childSnapshotWrappers": [{
                "contentType": "MAPPING_PROFILE",
                "content": {"id": "mapping", "incomingRecordType": "MARC_BIBLIOGRAPHIC", "existingRecordType": "HOLDINGS"}
              }]
            }]
          }]
        }]
      }
      """);

    PathExtractionResult result = (PathExtractionResult) method.invoke(new JpWranglerCli.GenerateCommand(), snapshot);

    assertEquals(1, result.updatePaths().size());
    CategorizedPath path = result.updatePaths().get(0);
    assertEquals("match-holdings", path.matchProfileId());
    assertEquals("match-holdings", path.matchCriteria().matchProfileId());
    assertEquals(1, path.matchCriteria().matchFields().size());
    MatchCriteria.MatchFieldSpec spec = path.matchCriteria().matchFields().get(0);
    assertEquals("999", spec.fieldTag());
    assertEquals("f", spec.indicator1());
    assertEquals("f", spec.indicator2());
    assertEquals("s", spec.subfieldCode());

    Map<Integer, GenerationOutcome.NeedsEnrichment> enrichment =
      EnrichmentDetector.detect(List.of(path), "records");
    assertEquals(1, enrichment.size());
    assertTrue(enrichment.get(0).hint().contains("--enrich-field 999ff$s"));
  }

  @Test
  public void extractionKeepsUnsupportedModifyForOtherRecordTypes() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("extractAllPaths", JsonNode.class);
    method.setAccessible(true);
    JsonNode snapshot = OBJECT_MAPPER.readTree("""
      {
        "contentType": "JOB_PROFILE",
        "content": {"id": "job", "dataType": "MARC", "order": 0},
        "childSnapshotWrappers": [{
          "contentType": "ACTION_PROFILE",
          "content": {"id": "modify", "action": "MODIFY", "folioRecord": "INSTANCE", "order": 0},
          "childSnapshotWrappers": [{
            "contentType": "MAPPING_PROFILE",
            "content": {"id": "map-modify", "incomingRecordType": "MARC_BIBLIOGRAPHIC", "existingRecordType": "INSTANCE"}
          }]
        }]
      }
      """);

    PathExtractionResult result = (PathExtractionResult) method.invoke(new JpWranglerCli.GenerateCommand(), snapshot);

    assertTrue(result.createPaths().isEmpty());
    assertTrue(result.updatePaths().isEmpty());
    assertEquals(1, result.unsupportedActionPaths().size());
    assertTrue(result.unsupportedActionPaths().get(0).path().getPathId().contains("MODIFY_INSTANCE"));
  }

  @Test
  public void categorizationCoalescesSiblingMatchCreateActionsIntoOneImportRecord() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("categorizePaths", PathExtractionResult.class);
    method.setAccessible(true);

    CategorizedPath holdingsPath = matchCreatePath(
      "CREATE",
      "HOLDINGS",
      "MARC_BIBLIOGRAPHIC",
      "HOLDINGS",
      2
    );
    CategorizedPath itemPath = matchCreatePath(
      "CREATE",
      "ITEM",
      "MARC_BIBLIOGRAPHIC",
      "ITEM",
      4
    );
    PathExtractionResult extraction = new PathExtractionResult(
      List.of(holdingsPath, itemPath),
      List.of(),
      List.of(),
      List.of()
    );

    CategorizedPaths result = (CategorizedPaths) method.invoke(new JpWranglerCli.GenerateCommand(), extraction);

    assertEquals("Sibling CREATE actions under one MATCH outcome share one incoming record",
      1, result.unpairedCreatePaths().size());
    JobProfilePath path = result.unpairedCreatePaths().get(0).path();
    assertTrue(path.createsHoldings());
    assertTrue(path.createsItems());
    assertEquals(
      "JobProfile->MatchProfile->CREATE_HOLDINGS->Map_HOLDINGS->CREATE_ITEM->Map_ITEM",
      path.getPathId());
  }

  @Test
  public void categorizationDoesNotCoalesceCreateActionsWhenMatchProfileIdIsBlank() throws Exception {
    Method method = JpWranglerCli.GenerateCommand.class.getDeclaredMethod("categorizePaths", PathExtractionResult.class);
    method.setAccessible(true);

    CategorizedPath holdingsPath = new CategorizedPath(
      matchCreatePath("CREATE", "HOLDINGS", "MARC_BIBLIOGRAPHIC", "HOLDINGS", 2).path(),
      ReactTo.MATCH,
      "",
      MatchCriteria.empty());
    CategorizedPath itemPath = new CategorizedPath(
      matchCreatePath("CREATE", "ITEM", "MARC_BIBLIOGRAPHIC", "ITEM", 4).path(),
      ReactTo.MATCH,
      "",
      MatchCriteria.empty());
    PathExtractionResult extraction = new PathExtractionResult(
      List.of(holdingsPath, itemPath),
      List.of(),
      List.of(),
      List.of()
    );

    CategorizedPaths result = (CategorizedPaths) method.invoke(new JpWranglerCli.GenerateCommand(), extraction);

    assertEquals("Blank match profile ids are malformed and should not be grouped together",
      2, result.unpairedCreatePaths().size());
  }

  private CategorizedPath path(String pathId, ReactTo reactTo, MatchCriteria criteria) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job", "MARC", 0),
      new ActionProfileNode("action-" + pathId, "UPDATE", "INSTANCE", 0)
    );
    return new CategorizedPath(new JobProfilePath(profiles, pathId), reactTo, "match-1", criteria);
  }

  private CategorizedPath matchCreatePath(String action, String folioRecord, String incomingRecordType,
      String existingRecordType, int actionOrder) {
    List<Profile> profiles = List.of(
      new JobProfileNode("job", "MARC", 0),
      new MatchProfileNode("match-1", incomingRecordType, "INSTANCE", 1),
      new ActionProfileNode("action-" + folioRecord, action, folioRecord, actionOrder),
      new MappingProfileNode("mapping-" + folioRecord, incomingRecordType, existingRecordType, actionOrder + 1)
    );
    return new CategorizedPath(new JobProfilePath(profiles), ReactTo.MATCH, "match-1", MatchCriteria.empty());
  }

  private MatchCriteria nonMarcCriteria() {
    return new MatchCriteria(
      "match-1",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "a", null)),
      List.of(new MatchCriteria.NonMarcMatchSpec("instance.hrid", "999", "a", "f", "f"))
    );
  }
}
