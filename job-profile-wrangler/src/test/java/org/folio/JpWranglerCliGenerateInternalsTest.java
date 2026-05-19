package org.folio;

import org.folio.exports.CategorizedPath;
import org.folio.exports.CategorizedPaths;
import org.folio.exports.EnrichmentDetector;
import org.folio.exports.GenerationOutcome;
import org.folio.exports.JobProfilePath;
import org.folio.exports.MatchedPathPair;
import org.folio.exports.MatchCriteria;
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
