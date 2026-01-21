package org.folio.exports;

import java.util.*;

/**
 * Represents the result of job profile analysis.
 * Contains required inventory fields, match criteria, and path information for synthetic MARC generation.
 */
public record JobProfileAnalysisResult(
    Set<String> requiredInventoryFields,
    Set<MatchCriteria> matchCriteria,
    List<JobProfilePath> allPaths,
    Set<String> optimizedRequiredFields,
    Map<String, Set<String>> fieldOperations
) {
  public JobProfileAnalysisResult {
    Objects.requireNonNull(requiredInventoryFields);
    Objects.requireNonNull(matchCriteria);
    Objects.requireNonNull(allPaths);
    Objects.requireNonNull(optimizedRequiredFields);
    Objects.requireNonNull(fieldOperations);
    requiredInventoryFields = Set.copyOf(requiredInventoryFields);
    matchCriteria = Set.copyOf(matchCriteria);
    allPaths = List.copyOf(allPaths);
    optimizedRequiredFields = Set.copyOf(optimizedRequiredFields);
    fieldOperations = Map.copyOf(fieldOperations);
  }
}
