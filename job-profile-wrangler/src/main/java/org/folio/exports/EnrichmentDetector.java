package org.folio.exports;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects paths whose match criteria need post-import enrichment.
 */
public final class EnrichmentDetector {
  public static final String INSTANCE_ID = "INSTANCE_ID";
  public static final String INSTANCE_HRID = "INSTANCE_HRID";

  private EnrichmentDetector() {
  }

  /**
   * Finds paths that match on non-MARC Instance fields assigned by FOLIO.
   *
   * @param paths paths in deterministic path-index order
   * @param outputBase base output path passed to the generate command
   * @return needs-enrichment outcomes keyed by zero-based path index
   */
  public static Map<Integer, GenerationOutcome.NeedsEnrichment> detect(
      List<CategorizedPath> paths,
      String outputBase) {

    Map<Integer, GenerationOutcome.NeedsEnrichment> outcomes = new LinkedHashMap<>();
    if (paths == null || paths.isEmpty()) {
      return outcomes;
    }

    for (int pathIndex = 0; pathIndex < paths.size(); pathIndex++) {
      CategorizedPath path = paths.get(pathIndex);
      MatchCriteria matchCriteria = path.matchCriteria();
      GenerationOutcome.NeedsEnrichment authorityDeleteEnrichment =
        authoritySourceRecordIdEnrichment(pathIndex, path, outputBase);
      if (authorityDeleteEnrichment != null) {
        outcomes.put(pathIndex, authorityDeleteEnrichment);
        continue;
      }
      if (matchCriteria == null || !matchCriteria.hasNonMarcMatches()) {
        continue;
      }

      MatchCriteria.NonMarcMatchSpec spec = matchCriteria.nonMarcMatches().get(0);
      String enrichType = enrichTypeFor(spec.existingField());
      if (enrichType == null) {
        continue;
      }

      outcomes.put(pathIndex, new GenerationOutcome.NeedsEnrichment(
        pathIndex,
        path.path().getPathId(),
        path.matchProfileId(),
        hintFor(outputBase, spec, enrichType)
      ));
    }

    return outcomes;
  }

  private static GenerationOutcome.NeedsEnrichment authoritySourceRecordIdEnrichment(
      int pathIndex,
      CategorizedPath path,
      String outputBase) {
    if (!changesMarcAuthority(path) || path.matchCriteria() == null || !path.matchCriteria().hasMarcMatches()) {
      return null;
    }
    boolean matchesSrsSourceRecordId = path.matchCriteria().matchFields().stream()
      .anyMatch(spec -> "999".equals(spec.fieldTag())
        && "f".equals(spec.indicator1())
        && "f".equals(spec.indicator2())
        && "s".equals(spec.subfieldCode()));
    if (!matchesSrsSourceRecordId) {
      return null;
    }
    return new GenerationOutcome.NeedsEnrichment(
      pathIndex,
      path.path().getPathId(),
      path.matchProfileId(),
      "Run: jp-wrangler enrich " + outputBase + "-import.mrc"
        + " --match-field 001 --enrich-field 999ff$s --enrich-type SOURCE_RECORD_ID"
        + " --record-type MARC_AUTHORITY --skip-missing");
  }

  private static boolean changesMarcAuthority(CategorizedPath path) {
    return path.path().getProfiles().stream()
      .filter(org.folio.graph.nodes.ActionProfileNode.class::isInstance)
      .map(org.folio.graph.nodes.ActionProfileNode.class::cast)
      .anyMatch(action -> ("UPDATE".equals(action.action()) || "DELETE".equals(action.action()))
        && "MARC_AUTHORITY".equals(action.folioRecord()));
  }

  private static String enrichTypeFor(String existingField) {
    if ("instance.id".equals(existingField)) {
      return INSTANCE_ID;
    }
    if ("instance.hrid".equals(existingField)) {
      return INSTANCE_HRID;
    }
    return null;
  }

  private static String hintFor(
      String outputBase,
      MatchCriteria.NonMarcMatchSpec spec,
      String enrichType) {

    String subfield = normalize(spec.targetSubfield()) == null ? "a" : normalize(spec.targetSubfield());
    return "Run: jp-wrangler enrich " + outputBase + "-import.mrc"
      + " --match-field 001"
      + " --enrich-field " + enrichFieldSpec(spec, subfield)
      + " --enrich-type " + enrichType;
  }

  private static String enrichFieldSpec(MatchCriteria.NonMarcMatchSpec spec, String subfield) {
    return spec.targetMarcField() + indicators(spec.targetIndicator1(), spec.targetIndicator2()) + "$" + subfield;
  }

  private static String indicators(String indicator1, String indicator2) {
    String first = normalize(indicator1);
    String second = normalize(indicator2);

    if (first == null && second == null) {
      return "";
    }
    if (second == null) {
      return first;
    }
    return (first == null ? " " : first) + second;
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
