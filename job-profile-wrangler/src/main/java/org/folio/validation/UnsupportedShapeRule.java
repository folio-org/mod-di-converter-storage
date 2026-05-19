package org.folio.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;

import java.util.Optional;

/**
 * Evaluates a live job-profile snapshot for one known unsupported workflow shape.
 */
public interface UnsupportedShapeRule {
  String name();

  String description();

  String citation();

  Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot);
}
