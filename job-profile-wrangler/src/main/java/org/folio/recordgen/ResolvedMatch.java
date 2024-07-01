package org.folio.recordgen;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.folio.rest.jaxrs.model.MatchExpression;


@Builder
@Getter
@EqualsAndHashCode
public class ResolvedMatch {
  private Object incoming;
  private MatchExpression.DataValueType incomingDataValueType;
  private Object existing;
  private MatchExpression.DataValueType existingDataValueType;
  private boolean isNonMatch;
}
