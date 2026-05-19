package org.folio.exports;

import java.util.List;

/**
 * Result of path extraction containing both CREATE and UPDATE paths.
 */
public record PathExtractionResult(
  List<CategorizedPath> createPaths,
  List<CategorizedPath> updatePaths
) {}
