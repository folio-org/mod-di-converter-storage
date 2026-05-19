package org.folio.exports;

/**
 * Tracks when a path executes based on match-profile branch results.
 */
public enum ReactTo {
  /** Path executes when incoming record matches an existing FOLIO record. */
  MATCH,
  /** Path executes when incoming record does not match any existing FOLIO record. */
  NON_MATCH,
  /** Path has no match profile and executes unconditionally. */
  NONE
}
