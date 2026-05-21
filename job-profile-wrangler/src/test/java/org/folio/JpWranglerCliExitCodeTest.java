package org.folio;

import org.folio.imports.ImportOutcome;
import org.folio.imports.ImportReport;
import org.junit.Test;
import picocli.CommandLine;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class JpWranglerCliExitCodeTest {
  @Test
  public void generateUsageErrorsReturnOneInsteadOfNeedsEnrichmentCode() {
    int exitCode = new CommandLine(new JpWranglerCli()).execute("generate", "profile-id");

    assertEquals(1, exitCode);
  }

  @Test
  public void importReportWithBlockedOrErrorEntriesReturnsFailure() {
    ImportReport clean = new ImportReport("2026-05-20T00:00:00Z", List.of(
      new ImportReport.Entry("profile-1", "Profile 1", new ImportOutcome.Added(1)),
      new ImportReport.Entry("profile-2", "Profile 2", new ImportOutcome.Duplicate(1))
    ));
    ImportReport blocked = new ImportReport("2026-05-20T00:00:00Z", List.of(
      new ImportReport.Entry("profile-1", "Profile 1", new ImportOutcome.BlockedUnsupported("rule", "message"))
    ));
    ImportReport errored = new ImportReport("2026-05-20T00:00:00Z", List.of(
      new ImportReport.Entry("profile-1", "Profile 1", new ImportOutcome.ImportError("boom"))
    ));

    assertEquals(0, JpWranglerCli.ImportCommand.exitCodeForReport(clean));
    assertEquals(1, JpWranglerCli.ImportCommand.exitCodeForReport(blocked));
    assertEquals(1, JpWranglerCli.ImportCommand.exitCodeForReport(errored));
  }
}
