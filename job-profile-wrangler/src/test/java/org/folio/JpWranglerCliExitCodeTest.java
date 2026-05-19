package org.folio;

import org.junit.Test;
import picocli.CommandLine;

import static org.junit.Assert.assertEquals;

public class JpWranglerCliExitCodeTest {
  @Test
  public void generateUsageErrorsReturnOneInsteadOfNeedsEnrichmentCode() {
    int exitCode = new CommandLine(new JpWranglerCli()).execute("generate", "profile-id");

    assertEquals(1, exitCode);
  }
}
