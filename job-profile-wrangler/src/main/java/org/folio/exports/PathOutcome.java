package org.folio.exports;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Outcome details for one extracted job-profile execution path.
 */
public record PathOutcome(
  int pathIndex,
  String pathId,
  String reactTo,
  String matchProfileId,
  @JsonInclude(JsonInclude.Include.NON_NULL)
  Integer importRecordNumber,
  List<DestinationFile> destinationFiles,
  List<FieldWritten> fieldsWritten,
  GenerationOutcome outcome
) {
  public PathOutcome {
    destinationFiles = List.copyOf(destinationFiles);
    fieldsWritten = List.copyOf(fieldsWritten);
  }

  public PathOutcome(
      int pathIndex,
      String pathId,
      String reactTo,
      String matchProfileId,
      List<DestinationFile> destinationFiles,
      List<FieldWritten> fieldsWritten,
      GenerationOutcome outcome) {
    this(pathIndex, pathId, reactTo, matchProfileId, null, destinationFiles, fieldsWritten, outcome);
  }

  public record DestinationFile(
    String file,
    String role
  ) {}

  public record FieldWritten(
    String fieldTag,
    String value,
    String mapsTo
  ) {}
}
