package org.folio.exports;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class GenerationOutcomeExitCodeTest {
  @Test
  public void variantsReturnStableExitCodes() {
    assertEquals(0, GenerationOutcome.Generated.INSTANCE.exitCode());
    assertEquals(2, new GenerationOutcome.NeedsEnrichment(0, "path", "match", "hint").exitCode());
    assertEquals(3, new GenerationOutcome.BlockedUnsupportedWorkflow("rule", "message").exitCode());
    assertEquals(4, new GenerationOutcome.GeneratorGap(0, "path", "REFERENCE_DATA_MISSING", "message").exitCode());
    assertEquals(5, new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "message").exitCode());
  }

  @Test
  public void outcomeCodesReserveOneForPreClassificationFailures() {
    List<Integer> outcomeCodes = List.of(
      GenerationOutcome.Generated.INSTANCE.exitCode(),
      new GenerationOutcome.NeedsEnrichment(0, "path", "match", "hint").exitCode(),
      new GenerationOutcome.BlockedUnsupportedWorkflow("rule", "message").exitCode(),
      new GenerationOutcome.GeneratorGap(0, "path", "REFERENCE_DATA_MISSING", "message").exitCode(),
      new GenerationOutcome.InvalidProfileShape("EMPTY_PATH", "message").exitCode()
    );

    assertEquals(List.of(0, 2, 3, 4, 5), outcomeCodes);
  }
}
