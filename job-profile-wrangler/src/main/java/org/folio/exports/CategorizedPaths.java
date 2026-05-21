package org.folio.exports;

import java.util.List;

/**
 * Paths grouped into paired and unpaired record-generation buckets.
 */
public record CategorizedPaths(
  List<MatchedPathPair> pairedPaths,
  List<CategorizedPath> unpairedCreatePaths,
  List<CategorizedPath> unpairedUpdatePaths,
  List<CategorizedPath> deletePaths
) {}
