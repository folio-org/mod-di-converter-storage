package org.folio.validation;

import com.fasterxml.jackson.databind.JsonNode;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.rules.CreateHoldingsWithoutInstanceContextRule;
import org.folio.validation.rules.EmptyMatchDetailsRule;
import org.folio.validation.rules.MatchInstanceCreateItemRule;
import org.folio.validation.rules.MissingMarcMappingOptionRule;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Applies the canonical v1 unsupported-shape rule list to a live job-profile snapshot.
 */
public class ProfileShapeValidator {
  private static final List<UnsupportedShapeRule> DEFAULT_RULES = List.of(
    new MatchInstanceCreateItemRule(),
    new EmptyMatchDetailsRule(),
    new MissingMarcMappingOptionRule(),
    new CreateHoldingsWithoutInstanceContextRule()
  );

  private final List<UnsupportedShapeRule> rules;

  public ProfileShapeValidator(List<UnsupportedShapeRule> rules) {
    this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
  }

  public static ProfileShapeValidator defaultValidator() {
    return new ProfileShapeValidator(defaultRules());
  }

  public static List<UnsupportedShapeRule> defaultRules() {
    return DEFAULT_RULES;
  }

  public Optional<BlockedUnsupportedWorkflow> validate(JsonNode snapshot) {
    Objects.requireNonNull(snapshot, "snapshot");

    for (UnsupportedShapeRule rule : rules) {
      Optional<BlockedUnsupportedWorkflow> outcome = rule.evaluate(snapshot);
      if (outcome.isPresent()) {
        return outcome;
      }
    }

    return Optional.empty();
  }
}
