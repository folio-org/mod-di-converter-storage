package org.folio.exports;

/**
 * A CREATE path paired with an UPDATE path that share the same match profile.
 */
public record MatchedPathPair(
  CategorizedPath createPath,
  CategorizedPath updatePath,
  String matchProfileId
) {}
