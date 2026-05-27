package org.folio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.ImportOutcome;
import org.folio.imports.ImportReport;
import org.junit.Test;
import picocli.CommandLine;

import java.util.HashMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

public class JpWranglerCliExitCodeTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  public void generateUsageErrorsReturnOneInsteadOfNeedsEnrichmentCode() {
    int exitCode = new CommandLine(new JpWranglerCli()).execute("generate", "profile-id");

    assertEquals(1, exitCode);
  }

  @Test
  public void exportModesAreMutuallyExclusive() {
    assertEquals(0, JpWranglerCli.ExportCommand.selectedExportModeCount(null, false, false));
    assertEquals(1, JpWranglerCli.ExportCommand.selectedExportModeCount(5, false, false));
    assertEquals(1, JpWranglerCli.ExportCommand.selectedExportModeCount(null, true, false));
    assertEquals(1, JpWranglerCli.ExportCommand.selectedExportModeCount(null, false, true));
    assertEquals(2, JpWranglerCli.ExportCommand.selectedExportModeCount(5, false, true));
    assertEquals(2, JpWranglerCli.ExportCommand.selectedExportModeCount(null, true, true));
    assertEquals(3, JpWranglerCli.ExportCommand.selectedExportModeCount(5, true, true));
  }

  @Test
  public void foundationSeedRollbackRunsInReverseCreationOrder() {
    ProfileHydration hydration = mock(ProfileHydration.class);
    ProfileHydration.HydrationResult first =
      new ProfileHydration.HydrationResult(new Object(), new HashMap<>(), new ArrayList<Profile>());
    ProfileHydration.HydrationResult second =
      new ProfileHydration.HydrationResult(new Object(), new HashMap<>(), new ArrayList<Profile>());

    JpWranglerCli.ExportCommand.rollbackSeedProfiles(hydration, List.of(first, second));

    var inOrder = inOrder(hydration);
    inOrder.verify(hydration).rollback(second);
    inOrder.verify(hydration).rollback(first);
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

  @Test
  public void explicitDotenvFileMustExist() {
    IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
      () -> JpWranglerCli.FolioConnectionOptions.loadDotenv("missing-dotenv-file", true));

    assertTrue(exception.getMessage().contains("Dotenv file does not exist"));
  }

  @Test
  public void collectProfileIdsFindsNestedSubProfiles() throws Exception {
    JsonNode snapshot = snapshot("job-1", "match-1", "action-1", "mapping-1");

    JpWranglerCli.DeleteCommand.ProfileReferences references =
      JpWranglerCli.DeleteCommand.collectProfileIds(snapshot);

    assertEquals(Set.of("match-1"), references.matchIds());
    assertEquals(Set.of("action-1"), references.actionIds());
    assertEquals(Set.of("mapping-1"), references.mappingIds());
  }

  @Test
  public void sharedReferencesFindsChildrenReferencedByNonSelectedProfiles() throws Exception {
    FolioClient client = mock(FolioClient.class);
    JsonNode selectedProfile = profile("selected");
    JsonNode otherProfile = profile("other");
    JpWranglerCli.DeleteCommand.ProfileReferences selectedReferences =
      new JpWranglerCli.DeleteCommand.ProfileReferences(
        new java.util.HashSet<>(Set.of("match-selected")),
        new java.util.HashSet<>(Set.of("action-selected")),
        new java.util.HashSet<>(Set.of("mapping-selected", "mapping-shared")));

    when(client.getJobProfileSnapshot("other"))
      .thenReturn(Optional.of(snapshot("other", "match-other", "action-other", "mapping-shared")));

    JpWranglerCli.DeleteCommand.ProfileReferences shared =
      JpWranglerCli.DeleteCommand.sharedReferencesFromNonSelectedProfiles(client,
        List.of(selectedProfile, otherProfile), Set.of("selected"), selectedReferences);

    assertEquals(Set.of("mapping-shared"), shared.mappingIds());
    assertTrue(shared.matchIds().isEmpty());
    assertTrue(shared.actionIds().isEmpty());
  }

  @Test
  public void sharedReferencesAbortWhenNonSelectedSnapshotCannotBeChecked() {
    FolioClient client = mock(FolioClient.class);
    JpWranglerCli.DeleteCommand.ProfileReferences selectedReferences =
      new JpWranglerCli.DeleteCommand.ProfileReferences(
        new java.util.HashSet<>(), new java.util.HashSet<>(), new java.util.HashSet<>(Set.of("mapping-selected")));
    when(client.getJobProfileSnapshot("other")).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class,
      () -> JpWranglerCli.DeleteCommand.sharedReferencesFromNonSelectedProfiles(client,
        List.of(profile("other")), Set.of(), selectedReferences));
  }

  private JsonNode profile(String id) {
    return OBJECT_MAPPER.createObjectNode().put("id", id).put("name", id);
  }

  private JsonNode snapshot(String jobId, String matchId, String actionId, String mappingId) throws Exception {
    return OBJECT_MAPPER.readTree("""
      {
        "contentType": "JOB_PROFILE",
        "content": {"id": "%s"},
        "childSnapshotWrappers": [{
          "contentType": "MATCH_PROFILE",
          "content": {"id": "%s"},
          "childSnapshotWrappers": [{
            "contentType": "ACTION_PROFILE",
            "content": {"id": "%s"},
            "childSnapshotWrappers": [{
              "contentType": "MAPPING_PROFILE",
              "content": {"id": "%s"}
            }]
          }]
        }]
      }
      """.formatted(jobId, matchId, actionId, mappingId));
  }
}
