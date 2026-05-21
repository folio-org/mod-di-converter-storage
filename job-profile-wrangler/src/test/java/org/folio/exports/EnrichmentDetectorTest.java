package org.folio.exports;

import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EnrichmentDetectorTest {
  private static final String OUTPUT_BASE = "target/generated/job-profile";

  @Test
  public void marcOnlyMatchCriteriaDoesNotNeedEnrichment() {
    CategorizedPath path = path("path-0", marcOnlyCriteria("match-1"));

    Map<Integer, GenerationOutcome.NeedsEnrichment> result =
      EnrichmentDetector.detect(List.of(path), OUTPUT_BASE);

    assertTrue(result.isEmpty());
  }

  @Test
  public void instanceHridNeedsEnrichmentWithRunnableHint() {
    CategorizedPath path = path("path-0", matchCriteria(nonMarc("instance.hrid", "999", "a", "f", "f")));

    Map<Integer, GenerationOutcome.NeedsEnrichment> result =
      EnrichmentDetector.detect(List.of(path), OUTPUT_BASE);

    assertEquals(1, result.size());
    GenerationOutcome.NeedsEnrichment outcome = result.get(0);
    assertEquals(0, outcome.pathIndex());
    assertEquals("path-0", outcome.pathId());
    assertEquals("match-1", outcome.matchProfileId());
    assertEquals("Run: jp-wrangler enrich target/generated/job-profile-import.mrc --match-field 001 "
      + "--enrich-field 999ff$a --enrich-type INSTANCE_HRID", outcome.hint());
  }

  @Test
  public void instanceIdNeedsEnrichmentWithInstanceIdType() {
    CategorizedPath path = path("path-0", matchCriteria(nonMarc("instance.id", "999", "a", "f", "f")));

    GenerationOutcome.NeedsEnrichment outcome =
      EnrichmentDetector.detect(List.of(path), OUTPUT_BASE).get(0);

    assertTrue(outcome.hint().contains("--enrich-type INSTANCE_ID"));
  }

  @Test
  public void authorityDeleteBySrsIdNeedsSourceRecordEnrichment() {
    CategorizedPath path = authorityDeletePath("delete-auth", new MatchCriteria(
      "match-auth",
      List.of(new MatchCriteria.MatchFieldSpec("999", "f", "f", "s", null)),
      List.of()
    ));

    GenerationOutcome.NeedsEnrichment outcome =
      EnrichmentDetector.detect(List.of(path), OUTPUT_BASE).get(0);

    assertEquals("delete-auth", outcome.pathId());
    assertEquals("match-auth", outcome.matchProfileId());
    assertEquals("Run: jp-wrangler enrich target/generated/job-profile-import.mrc --match-field 001 "
      + "--enrich-field 999ff$s --enrich-type SOURCE_RECORD_ID --record-type MARC_AUTHORITY", outcome.hint());
  }


  @Test
  public void multiplePathsClassifiesOnlyMatchingPathByListIndex() {
    List<CategorizedPath> paths = List.of(
      path("path-0", marcOnlyCriteria("match-0")),
      path("path-1", matchCriteria(nonMarc("instance.hrid", "999", "a", "f", "f"))),
      path("path-2", marcOnlyCriteria("match-2"))
    );

    Map<Integer, GenerationOutcome.NeedsEnrichment> result =
      EnrichmentDetector.detect(paths, OUTPUT_BASE);

    assertEquals(1, result.size());
    assertTrue(result.containsKey(1));
    assertEquals(1, result.get(1).pathIndex());
    assertEquals("path-1", result.get(1).pathId());
  }

  @Test
  public void allMatchingPathsAreClassified() {
    List<CategorizedPath> paths = List.of(
      path("path-0", matchCriteria(nonMarc("instance.hrid", "999", "a", "f", "f"))),
      path("path-1", matchCriteria(nonMarc("instance.id", "035", null, null, null)))
    );

    Map<Integer, GenerationOutcome.NeedsEnrichment> result =
      EnrichmentDetector.detect(paths, OUTPUT_BASE);

    assertEquals(2, result.size());
    assertEquals("path-0", result.get(0).pathId());
    assertEquals("path-1", result.get(1).pathId());
  }

  @Test
  public void nonASubfieldStillUsesStableLookupField() {
    CategorizedPath path = path("path-0", matchCriteria(nonMarc("instance.hrid", "035", "z", "f", "f")));

    GenerationOutcome.NeedsEnrichment outcome =
      EnrichmentDetector.detect(List.of(path), OUTPUT_BASE).get(0);

    assertEquals("Run: jp-wrangler enrich target/generated/job-profile-import.mrc --match-field 001 "
      + "--enrich-field 035ff$z --enrich-type INSTANCE_HRID", outcome.hint());
  }

  @Test
  public void hintIsDeterministicForSameInputs() {
    CategorizedPath path = path("path-0", matchCriteria(nonMarc("instance.hrid", "999", "a", "f", "f")));

    String firstHint = EnrichmentDetector.detect(List.of(path), OUTPUT_BASE).get(0).hint();
    String secondHint = EnrichmentDetector.detect(List.of(path), OUTPUT_BASE).get(0).hint();

    assertEquals(firstHint, secondHint);
    assertEquals("Run: jp-wrangler enrich target/generated/job-profile-import.mrc --match-field 001 "
      + "--enrich-field 999ff$a --enrich-type INSTANCE_HRID", firstHint);
  }

  private CategorizedPath path(String pathId, MatchCriteria matchCriteria) {
    JobProfilePath path = new JobProfilePath(
      List.of(
        new JobProfileNode("job-1", "MARC", 0),
        new ActionProfileNode("action-1", "UPDATE", "INSTANCE", 1)
      ),
      pathId
    );
    return new CategorizedPath(path, ReactTo.MATCH, "match-1", matchCriteria);
  }

  private CategorizedPath authorityDeletePath(String pathId, MatchCriteria matchCriteria) {
    JobProfilePath path = new JobProfilePath(
      List.of(
        new JobProfileNode("job-1", "MARC", 0),
        new ActionProfileNode("action-1", "DELETE", "MARC_AUTHORITY", 1)
      ),
      pathId
    );
    return new CategorizedPath(path, ReactTo.MATCH, matchCriteria.matchProfileId(), matchCriteria);
  }

  private MatchCriteria matchCriteria(MatchCriteria.NonMarcMatchSpec nonMarcMatch) {
    return new MatchCriteria(
      "match-1",
      List.of(new MatchCriteria.MatchFieldSpec(
        nonMarcMatch.targetMarcField(),
        nonMarcMatch.targetIndicator1(),
        nonMarcMatch.targetIndicator2(),
        nonMarcMatch.targetSubfield(),
        null)),
      List.of(nonMarcMatch)
    );
  }

  private MatchCriteria marcOnlyCriteria(String matchProfileId) {
    return new MatchCriteria(
      matchProfileId,
      List.of(new MatchCriteria.MatchFieldSpec("035", "f", "f", "a", null)),
      List.of()
    );
  }

  private MatchCriteria.NonMarcMatchSpec nonMarc(
      String existingField,
      String targetMarcField,
      String targetSubfield,
      String targetIndicator1,
      String targetIndicator2) {
    return new MatchCriteria.NonMarcMatchSpec(
      existingField,
      targetMarcField,
      targetSubfield,
      targetIndicator1,
      targetIndicator2
    );
  }
}
