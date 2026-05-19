package org.folio.exports;

import java.util.List;

/**
 * Outcome details for one extracted job-profile execution path.
 */
public record PathOutcome(
  int pathIndex,
  String pathId,
  String reactTo,
  String matchProfileId,
  List<DestinationFile> destinationFiles,
  List<FieldWritten> fieldsWritten,
  GenerationOutcome outcome
) {
  public PathOutcome {
    destinationFiles = List.copyOf(destinationFiles);
    fieldsWritten = List.copyOf(fieldsWritten);
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
