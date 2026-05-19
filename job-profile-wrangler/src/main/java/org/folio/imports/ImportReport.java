package org.folio.imports;

import java.util.List;

public record ImportReport(
  String runTimestamp,
  List<Entry> profiles
) {
  public ImportReport {
    profiles = List.copyOf(profiles);
  }

  public record Entry(
    String profileId,
    String profileName,
    ImportOutcome outcome
  ) {}

  public long addedCount() {
    return profiles.stream().filter(entry -> entry.outcome() instanceof ImportOutcome.Added).count();
  }

  public long duplicateCount() {
    return profiles.stream().filter(entry -> entry.outcome() instanceof ImportOutcome.Duplicate).count();
  }

  public long blockedCount() {
    return profiles.stream().filter(entry -> entry.outcome() instanceof ImportOutcome.BlockedUnsupported).count();
  }

  public long errorCount() {
    return profiles.stream().filter(entry -> entry.outcome() instanceof ImportOutcome.ImportError).count();
  }
}
