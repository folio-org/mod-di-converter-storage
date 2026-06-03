package org.folio.exports;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stable generation outcome contract for the job-profile-wrangler generate command.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = GenerationOutcome.Generated.class, name = GenerationOutcome.GENERATED),
  @JsonSubTypes.Type(value = GenerationOutcome.BlockedUnsupportedWorkflow.class, name = GenerationOutcome.BLOCKED_UNSUPPORTED_WORKFLOW),
  @JsonSubTypes.Type(value = GenerationOutcome.NeedsEnrichment.class, name = GenerationOutcome.NEEDS_ENRICHMENT),
  @JsonSubTypes.Type(value = GenerationOutcome.GeneratorGap.class, name = GenerationOutcome.GENERATOR_GAP),
  @JsonSubTypes.Type(value = GenerationOutcome.InvalidProfileShape.class, name = GenerationOutcome.INVALID_PROFILE_SHAPE)
})
public sealed interface GenerationOutcome permits
  GenerationOutcome.Generated,
  GenerationOutcome.BlockedUnsupportedWorkflow,
  GenerationOutcome.NeedsEnrichment,
  GenerationOutcome.GeneratorGap,
  GenerationOutcome.InvalidProfileShape {

  String GENERATED = "generated";
  String BLOCKED_UNSUPPORTED_WORKFLOW = "blocked-unsupported-workflow";
  String NEEDS_ENRICHMENT = "needs-enrichment";
  String GENERATOR_GAP = "generator-gap";
  String INVALID_PROFILE_SHAPE = "invalid-profile-shape";

  @JsonProperty("type")
  String type();

  default String label() {
    return type();
  }

  int exitCode();

  record Generated() implements GenerationOutcome {
    public static final Generated INSTANCE = new Generated();

    @Override
    @JsonProperty("type")
    public String type() {
      return GENERATED;
    }

    @Override
    public int exitCode() {
      return 0;
    }
  }

  record BlockedUnsupportedWorkflow(
    String rule,
    String message
  ) implements GenerationOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return BLOCKED_UNSUPPORTED_WORKFLOW;
    }

    @Override
    public int exitCode() {
      return 3;
    }
  }

  record NeedsEnrichment(
    int pathIndex,
    String pathId,
    String matchProfileId,
    String hint,
    Integer importRecordNumber
  ) implements GenerationOutcome {
    public NeedsEnrichment(int pathIndex, String pathId, String matchProfileId, String hint) {
      this(pathIndex, pathId, matchProfileId, hint, null);
    }

    @Override
    @JsonProperty("type")
    public String type() {
      return NEEDS_ENRICHMENT;
    }

    public NeedsEnrichment withImportRecordNumber(Integer importRecordNumber) {
      return new NeedsEnrichment(pathIndex, pathId, matchProfileId, hint, importRecordNumber);
    }

    public String hint() {
      if (importRecordNumber == null) {
        return hint;
      }
      // Keep the record number structured; render it only when presenting the command.
      String renderedRecordNumber = " --record-number " + importRecordNumber;
      int skipMissing = hint.indexOf(" --skip-missing");
      if (skipMissing >= 0) {
        return hint.substring(0, skipMissing) + renderedRecordNumber + hint.substring(skipMissing);
      }
      return hint + renderedRecordNumber;
    }

    @Override
    public int exitCode() {
      return 2;
    }
  }

  record GeneratorGap(
    int pathIndex,
    String pathId,
    String reason,
    String message
  ) implements GenerationOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return GENERATOR_GAP;
    }

    @Override
    public int exitCode() {
      return 4;
    }
  }

  record InvalidProfileShape(
    String reason,
    String message
  ) implements GenerationOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return INVALID_PROFILE_SHAPE;
    }

    @Override
    public int exitCode() {
      return 5;
    }
  }
}
