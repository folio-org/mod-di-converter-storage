package org.folio.exports;

/**
 * A path paired with its reaction type and match criteria.
 *
 * <p>The pathId inside {@link JobProfilePath} is human-readable but not unique across
 * MATCH and NON_MATCH branches, so consumers should use list position as pathIndex
 * when they need a unique per-run reference.
 */
public record CategorizedPath(
  JobProfilePath path,
  ReactTo reactTo,
  String matchProfileId,
  MatchCriteria matchCriteria
) {}
