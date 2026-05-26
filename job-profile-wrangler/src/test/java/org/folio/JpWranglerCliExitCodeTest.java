package org.folio;

import org.folio.imports.ImportOutcome;
import org.folio.imports.ImportReport;
import org.junit.Test;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

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

  @Test
  public void folioConnectionOptionsReadDotenvDefaults() throws Exception {
    Path dotenv = Files.createTempFile("jp-wrangler", ".env");
    Files.writeString(dotenv, """
      OKAPI=http://localhost:8000
      TENANT=diku
      USER=diku_admin
      PASS=admin
      TOKEN=token-from-dotenv
      X_OKAPI_URL=http://okapi:9130
      """);

    JpWranglerCli.FolioConnectionOptions options = new JpWranglerCli.FolioConnectionOptions();
    options.envFile = dotenv.toString();

    options.applyEnvironmentDefaults();

    assertEquals("http://localhost:8000", options.baseUrl);
    assertEquals("diku", options.tenant);
    assertEquals("diku_admin", options.username);
    assertEquals("admin", options.password);
    assertEquals("token-from-dotenv", options.token);
    assertEquals("http://okapi:9130", options.okapiUrl);
  }

  @Test
  public void explicitConnectionOptionsOverrideDotenvDefaults() throws Exception {
    Path dotenv = Files.createTempFile("jp-wrangler", ".env");
    Files.writeString(dotenv, """
      FOLIO_URL=http://dotenv.example
      FOLIO_TENANT=dotenv-tenant
      FOLIO_USERNAME=dotenv-user
      FOLIO_PASSWORD=dotenv-password
      """);

    JpWranglerCli.FolioConnectionOptions options = new JpWranglerCli.FolioConnectionOptions();
    options.envFile = dotenv.toString();
    options.baseUrl = "http://cli.example";
    options.tenant = "cli-tenant";
    options.username = "cli-user";
    options.password = "cli-password";

    options.applyEnvironmentDefaults();

    assertEquals("http://cli.example", options.baseUrl);
    assertEquals("cli-tenant", options.tenant);
    assertEquals("cli-user", options.username);
    assertEquals("cli-password", options.password);
  }

  @Test
  public void dotenvParserHandlesCommentsExportsAndQuotes() {
    Optional<JpWranglerCli.FolioConnectionOptions.DotenvEntry> comment =
      JpWranglerCli.FolioConnectionOptions.parseDotenvLine("# ignored");
    Optional<JpWranglerCli.FolioConnectionOptions.DotenvEntry> exported =
      JpWranglerCli.FolioConnectionOptions.parseDotenvLine("export FOLIO_PASSWORD=\"secret value\"");
    Map<String, String> loaded = JpWranglerCli.FolioConnectionOptions.loadDotenv("missing-dotenv-file");

    assertFalse(comment.isPresent());
    assertEquals("FOLIO_PASSWORD", exported.orElseThrow().name());
    assertEquals("secret value", exported.orElseThrow().value());
    assertEquals(Map.of(), loaded);
  }
}
