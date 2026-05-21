package org.folio.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.exports.GenerationOutcome.BlockedUnsupportedWorkflow;
import org.folio.validation.rules.CreateHoldingsWithoutInstanceContextRule;
import org.folio.validation.rules.EmptyMatchDetailsRule;
import org.folio.validation.rules.MatchInstanceCreateItemRule;
import org.folio.validation.rules.MatchInstanceUpdateMarcBibRule;
import org.folio.validation.rules.MissingMarcMappingOptionRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileShapeValidatorTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void matchInstanceCreateItemWithoutHoldingsFires() throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture("match-instance-create-item.json"));

    assertTrue(outcome.isPresent());
    assertEquals("blocked-unsupported-workflow", outcome.get().label());
    assertEquals(MatchInstanceCreateItemRule.RULE_NAME, outcome.get().rule());
  }

  @Test
  public void emptyMatchDetailsFires() throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture("empty-match-details.json"));

    assertTrue(outcome.isPresent());
    assertEquals(EmptyMatchDetailsRule.RULE_NAME, outcome.get().rule());
  }

  @Test
  public void matchInstanceUpdateMarcBibFires() throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture("match-instance-update-marc-bib.json"));

    assertTrue(outcome.isPresent());
    assertEquals(MatchInstanceUpdateMarcBibRule.RULE_NAME, outcome.get().rule());
  }

  @Test
  public void missingMarcMappingOptionFires() throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture("marc-mapping-option-missing.json"));

    assertTrue(outcome.isPresent());
    assertEquals(MissingMarcMappingOptionRule.RULE_NAME, outcome.get().rule());
  }

  @Test
  public void createHoldingsWithoutInstanceContextFires() throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture("create-holdings-without-instance-context.json"));

    assertTrue(outcome.isPresent());
    assertEquals(CreateHoldingsWithoutInstanceContextRule.RULE_NAME, outcome.get().rule());
  }

  @Test
  public void createInstanceHoldingsItemDoesNotMatch() throws Exception {
    assertNoRuleMatch("create-instance-holdings-item.json");
  }

  @Test
  public void inventoryMappingWithMarcInputDoesNotNeedMarcMappingOption() throws Exception {
    assertNoRuleMatch("inventory-mapping-with-marc-input.json");
  }

  @Test
  public void matchInstanceCreateHoldingsCreateItemDoesNotMatch() throws Exception {
    assertNoRuleMatch("match-instance-create-holdings-create-item.json");
  }

  @Test
  public void matchInstanceNonMatchBranchCreateInstanceBeforeHoldingsDoesNotMatch() throws Exception {
    assertNoRuleMatch("match-instance-nonmatch-create-instance-holdings.json");
  }

  @Test
  public void matchInstanceSiblingCreateHoldingsBeforeCreateItemDoesNotMatch() throws Exception {
    assertNoRuleMatch("match-instance-sibling-create-holdings-create-item.json");
  }

  @Test
  public void matchInstanceMatchHoldingsCreateItemDoesNotMatch() throws Exception {
    assertNoRuleMatch("match-instance-match-holdings-create-item.json");
  }

  @Test
  public void emptyRuleListReturnsEmpty() throws Exception {
    ProfileShapeValidator validator = new ProfileShapeValidator(List.of());

    assertFalse(validator.validate(fixture("match-instance-create-item.json")).isPresent());
  }

  @Test
  public void firstMatchingRuleWins() throws Exception {
    AtomicInteger secondRuleCalls = new AtomicInteger();
    ProfileShapeValidator validator = new ProfileShapeValidator(List.of(
      matchingRule("first-rule", new AtomicInteger()),
      matchingRule("second-rule", secondRuleCalls)
    ));

    Optional<BlockedUnsupportedWorkflow> outcome = validator.validate(fixture("match-instance-create-item.json"));

    assertTrue(outcome.isPresent());
    assertEquals("first-rule", outcome.get().rule());
    assertEquals(0, secondRuleCalls.get());
  }

  private void assertNoRuleMatch(String fixtureName) throws Exception {
    Optional<BlockedUnsupportedWorkflow> outcome = ProfileShapeValidator.defaultValidator()
      .validate(fixture(fixtureName));

    assertFalse(outcome.isPresent());
  }

  private JsonNode fixture(String fileName) throws IOException {
    String path = "profile-shape-validator/" + fileName;
    try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
      if (input == null) {
        throw new IOException("Missing fixture: " + path);
      }
      return OBJECT_MAPPER.readTree(input);
    }
  }

  private UnsupportedShapeRule matchingRule(String name, AtomicInteger calls) {
    return new UnsupportedShapeRule() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public String description() {
        return "Test rule";
      }

      @Override
      public String citation() {
        return "ProfileShapeValidatorTest";
      }

      @Override
      public Optional<BlockedUnsupportedWorkflow> evaluate(JsonNode snapshot) {
        calls.incrementAndGet();
        return Optional.of(new BlockedUnsupportedWorkflow(name, "matched"));
      }
    };
  }
}
