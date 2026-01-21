package org.folio.exports;

import java.util.Objects;

/**
 * Represents match criteria extracted from match profiles.
 * Contains the record types and matching logic used in job profile execution.
 */
public record MatchCriteria(
    String incomingRecordType,
    String existingRecordType,
    String matchField,
    String matchExpression
) {
  public MatchCriteria {
    Objects.requireNonNull(incomingRecordType);
    Objects.requireNonNull(existingRecordType);
  }

  public MatchCriteria(String incomingRecordType, String existingRecordType) {
    this(incomingRecordType, existingRecordType, null, null);
  }
}
