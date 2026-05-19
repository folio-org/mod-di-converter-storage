package org.folio.exports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

/**
 * Writes the machine-readable generation report and renders the human summary.
 */
public class GenerationReportWriter {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .enable(SerializationFeature.INDENT_OUTPUT);

  public Path write(Path outputBase, GenerationReport report, PrintStream out, boolean verbose) throws IOException {
    Path reportPath = reportPath(outputBase);
    OBJECT_MAPPER.writeValue(reportPath.toFile(), report);
    render(report, reportPath, out, verbose);
    return reportPath;
  }

  public Path reportPath(Path outputBase) {
    return outputBase.resolveSibling(outputBase.getFileName() + "-report.json");
  }

  public void render(GenerationReport report, Path reportPath, PrintStream out, boolean verbose) {
    out.println("Generation outcome: " + report.overallOutcome().label());
    out.println("Profile: " + fallback(report.profileName(), "(unnamed)") + " (" + fallback(report.profileId(), "unknown") + ")");
    out.println("Report: " + reportPath);

    if (report.paths().isEmpty()) {
      out.println("Paths: none");
    } else {
      out.println("Paths: " + report.paths().size());
    }

    if (hasDestinationFiles(report)) {
      out.println("Destination files:");
      report.paths().stream()
        .flatMap(path -> path.destinationFiles().stream())
        .distinct()
        .forEach(file -> out.println("  " + file.role() + ": " + file.file()));
    } else {
      out.println("No MARC files were written.");
    }

    if (report.overallOutcome() instanceof GenerationOutcome.NeedsEnrichment needsEnrichment) {
      out.println("Hint: " + needsEnrichment.hint());
    } else if (report.overallOutcome() instanceof GenerationOutcome.GeneratorGap gap) {
      out.println("Reason: " + gap.reason());
      out.println("Message: " + gap.message());
    } else if (report.overallOutcome() instanceof GenerationOutcome.BlockedUnsupportedWorkflow blocked) {
      out.println("Rule: " + blocked.rule());
      out.println("Message: " + blocked.message());
    } else if (report.overallOutcome() instanceof GenerationOutcome.InvalidProfileShape invalid) {
      out.println("Reason: " + invalid.reason());
      out.println("Message: " + invalid.message());
    }

    if (verbose && !report.paths().isEmpty()) {
      out.println();
      out.println("Path details:");
      for (PathOutcome path : report.paths()) {
        out.println("  [" + path.pathIndex() + "] " + path.outcome().label() + " " + path.pathId());
        out.println("      reactTo: " + path.reactTo());
        if (path.matchProfileId() != null && !path.matchProfileId().isBlank()) {
          out.println("      matchProfileId: " + path.matchProfileId());
        }
        for (PathOutcome.FieldWritten field : path.fieldsWritten()) {
          out.println("      " + field.fieldTag() + ": " + truncate(field.value(), 80));
          out.println("          mapsTo: " + field.mapsTo());
        }
        if (path.outcome() instanceof GenerationOutcome.NeedsEnrichment needsEnrichment) {
          out.println("      hint: " + needsEnrichment.hint());
        } else if (path.outcome() instanceof GenerationOutcome.GeneratorGap gap) {
          out.println("      reason: " + gap.reason());
          out.println("      message: " + gap.message());
        }
      }
    }
  }

  private boolean hasDestinationFiles(GenerationReport report) {
    return report.paths().stream().anyMatch(path -> !path.destinationFiles().isEmpty())
      && !(report.overallOutcome() instanceof GenerationOutcome.GeneratorGap)
      && !(report.overallOutcome() instanceof GenerationOutcome.BlockedUnsupportedWorkflow)
      && !(report.overallOutcome() instanceof GenerationOutcome.InvalidProfileShape);
  }

  private String fallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength - 3) + "...";
  }
}
