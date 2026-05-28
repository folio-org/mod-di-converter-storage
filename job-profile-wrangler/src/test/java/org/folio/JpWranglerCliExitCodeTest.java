package org.folio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.hydration.ProfileHydration;
import org.folio.imports.ImportOutcome;
import org.folio.imports.ImportReport;
import org.junit.Test;
import picocli.CommandLine;

import java.util.HashMap;
import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
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
  public void foundationSeedProfilesToReplaceCollectsExistingSeedProfiles() throws Exception {
    FolioClient client = mock(FolioClient.class);
    JsonNode old900 = profile("old-900", "jp-900 old");
    JsonNode old901 = profile("old-901", "jp-901 old");
    JsonNode old902 = profile("old-902", "jp-902 old");

    when(client.getJobProfiles(Map.of("query", "name==\"jp-900 *\""))).thenReturn(Stream.of(old900));
    when(client.getJobProfiles(Map.of("query", "name==\"jp-901 *\""))).thenReturn(Stream.of(old901));
    when(client.getJobProfiles(Map.of("query", "name==\"jp-902 *\""))).thenReturn(Stream.of(old902));
    when(client.getJobProfileSnapshot("old-900")).thenReturn(Optional.of(snapshot("old-900", "m900", "a900", "p900")));
    when(client.getJobProfileSnapshot("old-901")).thenReturn(Optional.of(snapshot("old-901", "m901", "a901", "p901")));
    when(client.getJobProfileSnapshot("old-902")).thenReturn(Optional.of(snapshot("old-902", "m902", "a902", "p902")));

    List<JpWranglerCli.DeleteCommand.ProfileDeletionData> profiles =
      JpWranglerCli.ExportCommand.foundationSeedProfilesToReplace(client);

    assertEquals(List.of("old-900", "old-901", "old-902"),
      profiles.stream().map(JpWranglerCli.DeleteCommand.ProfileDeletionData::id).toList());
    assertEquals(Set.of("p900"), profiles.get(0).mappingIds());
  }

  @Test
  public void foundationSeedProfilesToReplaceKeepsPrefixAnchoredToSeedNames() {
    assertEquals("name==\"jp-900 *\"", JpWranglerCli.ExportCommand.seedProfileNameQuery(900));
  }

  @Test
  public void foundationSeedProfilesToReplaceDoesNotAbortOnSnapshotlessSeedJob() throws Exception {
    FolioClient client = mock(FolioClient.class);
    JsonNode old900 = profile("old-900", "jp-900 partial");

    when(client.getJobProfiles(Map.of("query", "name==\"jp-900 *\""))).thenReturn(Stream.of(old900));
    when(client.getJobProfiles(Map.of("query", "name==\"jp-901 *\""))).thenReturn(Stream.empty());
    when(client.getJobProfiles(Map.of("query", "name==\"jp-902 *\""))).thenReturn(Stream.empty());
    when(client.getJobProfileSnapshot("old-900")).thenReturn(Optional.empty());

    List<JpWranglerCli.DeleteCommand.ProfileDeletionData> profiles =
      JpWranglerCli.ExportCommand.foundationSeedProfilesToReplace(client);

    assertEquals(List.of("old-900"),
      profiles.stream().map(JpWranglerCli.DeleteCommand.ProfileDeletionData::id).toList());
    assertTrue(profiles.get(0).actionIds().isEmpty());
    assertTrue(profiles.get(0).mappingIds().isEmpty());
    assertTrue(profiles.get(0).matchIds().isEmpty());
  }

  @Test
  public void deleteSeedProfilesUsesCascadeDeletion() {
    FolioClient client = mock(FolioClient.class);
    JpWranglerCli.DeleteCommand.ProfileDeletionData profile =
      new JpWranglerCli.DeleteCommand.ProfileDeletionData("job", "jp-900 old",
        Set.of("match"), Set.of("action"), Set.of("mapping"));
    when(client.deleteJobProfile("job")).thenReturn(true);
    when(client.deleteMappingProfile("mapping")).thenReturn(true);
    when(client.deleteActionProfile("action")).thenReturn(true);
    when(client.deleteMatchProfile("match")).thenReturn(true);

    JpWranglerCli.DeleteCommand.DeletionResult result =
      JpWranglerCli.ExportCommand.deleteSeedProfiles(client, List.of(profile));

    assertEquals(0, result.totalFailed());
    var inOrder = inOrder(client);
    inOrder.verify(client).deleteJobProfile("job");
    inOrder.verify(client).deleteActionProfile("action");
    inOrder.verify(client).deleteMappingProfile("mapping");
    inOrder.verify(client).deleteMatchProfile("match");
  }

  @Test
  public void foundationSeedOrphanProfilesExcludeChildrenStillReferencedBySeedJobs() throws Exception {
    FolioClient client = mock(FolioClient.class);
    for (String prefix : List.of("jp-900", "jp-901", "jp-902")) {
      Map<String, String> query = Map.of("query", "name==\"" + prefix + " *\"");
      when(client.getActionProfiles(query)).thenReturn(Stream.empty());
      when(client.getMappingProfiles(query)).thenReturn(Stream.empty());
      when(client.getMatchProfiles(query)).thenReturn(Stream.empty());
    }

    Map<String, String> query900 = Map.of("query", "name==\"jp-900 *\"");
    when(client.getActionProfiles(query900)).thenReturn(Stream.of(
      profile("action-orphan", "jp-900 orphan action"),
      profile("action-linked", "jp-900 linked action")));
    when(client.getMappingProfiles(query900)).thenReturn(Stream.of(
      profile("mapping-orphan", "jp-900 orphan mapping"),
      profile("mapping-linked", "jp-900 linked mapping")));
    when(client.getMatchProfiles(query900)).thenReturn(Stream.of(
      profile("match-orphan", "jp-900 orphan match"),
      profile("match-linked", "jp-900 linked match")));

    JpWranglerCli.DeleteCommand.ProfileDeletionData linkedSeedJob =
      new JpWranglerCli.DeleteCommand.ProfileDeletionData("job", "jp-900 old",
        Set.of("match-linked"), Set.of("action-linked"), Set.of("mapping-linked"));

    JpWranglerCli.DeleteCommand.ProfileReferences orphans =
      JpWranglerCli.ExportCommand.foundationSeedOrphanProfilesToReplace(client, List.of(linkedSeedJob));

    assertEquals(Set.of("action-orphan"), orphans.actionIds());
    assertEquals(Set.of("mapping-orphan"), orphans.mappingIds());
    assertEquals(Set.of("match-orphan"), orphans.matchIds());
  }

  @Test
  public void seedFoundationDetectsInstanceHoldingsAndItemShapes() throws Exception {
    assertEquals(JpWranglerCli.SeedFoundationCommand.FoundationBucket.INSTANCE,
      JpWranglerCli.SeedFoundationCommand.bucketForRecord(marcRecord(null)));
    assertEquals(JpWranglerCli.SeedFoundationCommand.FoundationBucket.HOLDINGS,
      JpWranglerCli.SeedFoundationCommand.bucketForRecord(marcRecord("852")));
    assertEquals(JpWranglerCli.SeedFoundationCommand.FoundationBucket.ITEM,
      JpWranglerCli.SeedFoundationCommand.bucketForRecord(marcRecord("945")));
  }

  @Test
  public void blankRepositoryOptionFailsInsteadOfFallingBackToEmbeddedRepository() {
    JpWranglerCli.RepositoryOptions options = new JpWranglerCli.RepositoryOptions();
    options.repoPath = " ";

    IllegalArgumentException error = assertThrows(IllegalArgumentException.class, options::readableRepoPath);

    assertTrue(error.getMessage().contains("--repository must not be blank"));
  }

  @Test
  public void latestProfileUsesParsedCreationTimeAndTreatsMissingDatesAsOldest() throws Exception {
    FolioClient client = mock(FolioClient.class);
    JsonNode undated = profile("undated", "jp-900 undated");
    JsonNode older = profileWithCreatedDate("older", "jp-900 older", "2026-01-01T00:00:00.000+0000");
    JsonNode middle = profileWithCreatedDate("middle", "jp-900 middle", "2026-01-02T00:00:00.000+00:00");
    JsonNode newer = profileWithCreatedDate("newer", "jp-900 newer", "2026-01-03T00:00:00.000");
    when(client.getJobProfiles(Map.of("query", "name==\"jp-900 *\"")))
      .thenReturn(Stream.of(undated, middle, newer, older));

    Optional<JsonNode> latest = JpWranglerCli.SeedFoundationCommand.latestProfile(client, "jp-900 ");

    assertTrue(latest.isPresent());
    assertEquals("newer", latest.get().path("id").asText());
  }

  @Test
  public void jobExecutionFallbackMatchesOnlyAllowListedFilenameFields() throws Exception {
    JsonNode windowsPathExecution = OBJECT_MAPPER.readTree("""
      {"fileName":"","sourcePath":"C:\\\\imports\\\\seed.mrc"}
      """);
    JsonNode dataImportRenamedExecution = OBJECT_MAPPER.readTree("""
      {"fileName":"1779977091049-seed.mrc","sourcePath":"data-import/diku/1779977091049-seed_1.mrc"}
      """);
    JsonNode substringCollision = OBJECT_MAPPER.readTree("""
      {"fileName":"other-seed.mrc","sourcePath":"","notes":"seed.mrc"}
      """);

    assertTrue(JpWranglerCli.SeedFoundationCommand.matchesExecutionFile(windowsPathExecution, "seed.mrc"));
    assertTrue(JpWranglerCli.SeedFoundationCommand.matchesExecutionFile(dataImportRenamedExecution, "seed.mrc"));
    assertFalse(JpWranglerCli.SeedFoundationCommand.matchesExecutionFile(substringCollision, "seed.mrc"));
  }

  @Test
  public void latestJobExecutionIgnoresOlderExecutionsFromPreviousUploads() throws Exception {
    FolioClient client = mock(FolioClient.class);
    JsonNode older = jobExecution("older", "profile", "1779977091049-seed.mrc",
      "2026-05-28T14:04:52.767+00:00");
    JsonNode current = jobExecution("current", "profile", "1779977291049-seed.mrc",
      "2026-05-28T14:08:52.767+00:00");
    when(client.getJobExecutions(25)).thenReturn(Stream.of(older, current));

    Optional<String> id = JpWranglerCli.SeedFoundationCommand.latestJobExecutionId(client, "profile", "seed.mrc",
      Instant.parse("2026-05-28T14:08:00Z"));

    assertTrue(id.isPresent());
    assertEquals("current", id.get());
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
    return profile(id, id);
  }

  private JsonNode profile(String id, String name) {
    return OBJECT_MAPPER.createObjectNode().put("id", id).put("name", name);
  }

  private JsonNode profileWithCreatedDate(String id, String name, String createdDate) {
    var profile = OBJECT_MAPPER.createObjectNode().put("id", id).put("name", name);
    profile.putObject("metadata").put("createdDate", createdDate);
    return profile;
  }

  private JsonNode jobExecution(String id, String profileId, String fileName, String startedDate) {
    var execution = OBJECT_MAPPER.createObjectNode()
      .put("id", id)
      .put("fileName", fileName)
      .put("startedDate", startedDate);
    execution.putObject("jobProfileInfo").put("id", profileId);
    return execution;
  }

  private Record marcRecord(String dataFieldTag) {
    MarcFactory factory = MarcFactory.newInstance();
    Record record = factory.newRecord("00000nam a2200000 a 4500");
    record.addVariableField(factory.newControlField("001", "seed-001"));
    if (dataFieldTag != null) {
      record.addVariableField(factory.newDataField(dataFieldTag, ' ', ' '));
    }
    return record;
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
