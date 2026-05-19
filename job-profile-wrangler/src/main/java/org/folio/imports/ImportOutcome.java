package org.folio.imports;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ImportOutcome.Added.class, name = ImportOutcome.ADDED),
  @JsonSubTypes.Type(value = ImportOutcome.Duplicate.class, name = ImportOutcome.DUPLICATE),
  @JsonSubTypes.Type(value = ImportOutcome.BlockedUnsupported.class, name = ImportOutcome.BLOCKED_UNSUPPORTED),
  @JsonSubTypes.Type(value = ImportOutcome.ImportError.class, name = ImportOutcome.IMPORT_ERROR)
})
public sealed interface ImportOutcome permits
  ImportOutcome.Added,
  ImportOutcome.Duplicate,
  ImportOutcome.BlockedUnsupported,
  ImportOutcome.ImportError {

  String ADDED = "added";
  String DUPLICATE = "duplicate";
  String BLOCKED_UNSUPPORTED = "blocked-unsupported";
  String IMPORT_ERROR = "import-error";

  @JsonProperty("type")
  String type();

  default String label() {
    return type();
  }

  record Added(int repoId) implements ImportOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return ADDED;
    }
  }

  record Duplicate(int existingRepoId) implements ImportOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return DUPLICATE;
    }
  }

  record BlockedUnsupported(String rule, String message) implements ImportOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return BLOCKED_UNSUPPORTED;
    }
  }

  record ImportError(String message) implements ImportOutcome {
    @Override
    @JsonProperty("type")
    public String type() {
      return IMPORT_ERROR;
    }
  }
}
