package org.folio.hydration;

import com.google.common.io.Resources;
import org.folio.foundation.FoundationSeedProfile;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.folio.http.FolioClient;
import org.folio.rest.jaxrs.model.ActionProfile;
import org.folio.rest.jaxrs.model.EntityType;
import org.folio.rest.jaxrs.model.JobProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingProfileUpdateDto;
import org.folio.rest.jaxrs.model.MappingRule;
import org.folio.rest.jaxrs.model.MatchProfileUpdateDto;
import org.folio.rest.jaxrs.model.ProfileAssociation;
import org.folio.rest.jaxrs.model.ProfileType;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@RunWith(MockitoJUnitRunner.class)
public class ProfileHydrationTest {

  @Mock
  private FolioClient folioClient;

  private Graph<Profile, RegularEdge> graph;

  @Before
  public void setUp() {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(), ActionProfile.FolioRecord.INSTANCE.toString(), 0);
    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());
  }


  @Test
  public void hydrate() throws IOException {
    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    ProfileHydration profileHydration = new ProfileHydration(folioClient);
    var jobProfile = profileHydration.hydrate(1, graph);
    assertTrue(jobProfile.isPresent());
    assertTrue(jobProfile.get() instanceof JobProfileUpdateDto);
  }

  @Test
  public void hydrateUsesTenantSystemControlNumberIdentifierType() throws IOException {
    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.getReferenceDataIdByName("identifier-types", MatchDetailsFactory.SYSTEM_CONTROL_NUMBER_TYPE_NAME))
      .thenReturn(Optional.of("tenant-system-control-number-id"));
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    new ProfileHydration(folioClient).hydrate(1, graph);

    ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
    verify(folioClient).createMatchProfile(requestCaptor.capture());
    MatchProfileUpdateDto request = OBJECT_MAPPER.readValue(requestCaptor.getValue(), MatchProfileUpdateDto.class);
    String matchDetailsJson = OBJECT_MAPPER.writeValueAsString(request.getProfile().getMatchDetails());
    assertTrue(matchDetailsJson.contains("tenant-system-control-number-id"));
  }

  @Test
  public void hydrateDoesNotTreatUserRepoId900AsFoundationSeedProfile() throws IOException {
    MappingDetail mappingDetails = hydrateAndCaptureInstanceMapping(900, false);

    MappingRule discoverySuppressField = mappingDetails.getMappingFields().stream()
      .filter(field -> "discoverySuppress".equals(field.getName()))
      .findFirst()
      .orElseThrow();

    assertNull(discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void hydrateFoundationSeedProfileUsesFoundationMappingDetails() throws IOException {
    MappingDetail mappingDetails = hydrateAndCaptureInstanceMapping(900, true);

    MappingRule discoverySuppressField = mappingDetails.getMappingFields().stream()
      .filter(field -> "discoverySuppress".equals(field.getName()))
      .findFirst()
      .orElseThrow();

    assertEquals(MappingRule.BooleanFieldAction.ALL_FALSE, discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void hydratePreservesNestedAssociationOrder() throws IOException {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.UPDATE.toString(),
      ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC.toString(), 2);
    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(),
      EntityType.MARC_BIBLIOGRAPHIC.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());

    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    new ProfileHydration(folioClient).hydrate(57, graph);

    ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
    verify(folioClient).createJobProfile(requestCaptor.capture());
    JobProfileUpdateDto request = OBJECT_MAPPER.readValue(requestCaptor.getValue(), JobProfileUpdateDto.class);

    ProfileAssociation matchToActionAssociation = request.getAddedRelations().stream()
      .filter(association -> association.getMasterProfileType() == ProfileType.MATCH_PROFILE)
      .filter(association -> association.getDetailProfileType() == ProfileType.ACTION_PROFILE)
      .findFirst()
      .orElseThrow();
    assertEquals(Integer.valueOf(2), matchToActionAssociation.getOrder());
  }

  @Test
  public void hydrateAddsMarcMappingOptionForMarcBibliographicUpdateMappings() throws IOException {
    MappingDetail mappingDetails = hydrateMarcMapping(ActionProfile.Action.UPDATE,
      ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC,
      EntityType.MARC_BIBLIOGRAPHIC);

    assertEquals(MappingDetail.MarcMappingOption.UPDATE, mappingDetails.getMarcMappingOption());
    assertTrue(mappingDetails.getMarcMappingDetails().isEmpty());
  }

  @Test
  public void hydrateAddsMarcMappingOptionForMarcBibliographicModifyMappings() throws IOException {
    MappingDetail mappingDetails = hydrateMarcMapping(ActionProfile.Action.MODIFY,
      ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC,
      EntityType.MARC_BIBLIOGRAPHIC);

    assertEquals(MappingDetail.MarcMappingOption.MODIFY, mappingDetails.getMarcMappingOption());
    assertTrue(mappingDetails.getMarcMappingDetails().isEmpty());
  }

  @Test
  public void hydrateAddsMarcMappingOptionForMarcHoldingsUpdateMappings() throws IOException {
    MappingDetail mappingDetails = hydrateMarcMapping(ActionProfile.Action.UPDATE,
      ActionProfile.FolioRecord.MARC_HOLDINGS,
      EntityType.MARC_HOLDINGS);

    assertEquals(MappingDetail.MarcMappingOption.UPDATE, mappingDetails.getMarcMappingOption());
    assertTrue(mappingDetails.getMarcMappingDetails().isEmpty());
  }

  private MappingDetail hydrateMarcMapping(ActionProfile.Action action, ActionProfile.FolioRecord folioRecord,
                                           EntityType recordType) throws IOException {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", recordType.toString(), recordType.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", action.toString(), folioRecord.toString(), 0);
    Profile mappingProfile = new MappingProfileNode("4", recordType.toString(), recordType.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    if (action == ActionProfile.Action.MODIFY) {
      graph.addEdge(jobProfile, actionProfile, new RegularEdge());
    } else {
      graph.addEdge(jobProfile, matchProfile, new RegularEdge());
      graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    }
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());

    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    new ProfileHydration(folioClient).hydrate(19, graph);

    ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
    verify(folioClient).createMappingProfile(requestCaptor.capture());
    MappingProfileUpdateDto request = OBJECT_MAPPER.readValue(requestCaptor.getValue(), MappingProfileUpdateDto.class);
    return request.getProfile().getMappingDetails();
  }

  private MappingDetail hydrateAndCaptureInstanceMapping(int repoId, boolean foundationSeed) throws IOException {
    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    ProfileHydration hydration = new ProfileHydration(folioClient);
    if (foundationSeed) {
      hydration.hydrateFoundationSeedProfile(FoundationSeedProfile.INSTANCE, graph);
    } else {
      hydration.hydrate(repoId, graph);
    }

    ArgumentCaptor<String> requestCaptor = ArgumentCaptor.forClass(String.class);
    verify(folioClient).createMappingProfile(requestCaptor.capture());
    MappingProfileUpdateDto request = OBJECT_MAPPER.readValue(requestCaptor.getValue(), MappingProfileUpdateDto.class);
    return request.getProfile().getMappingDetails();
  }

  @Test
  public void hydrateReturnsEmptyWhenJobProfileCreationFails() throws IOException {
    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.empty());
    when(folioClient.deleteMappingProfile("82de8419-688a-4594-97a9-a881aa27e8de")).thenReturn(true);
    when(folioClient.deleteActionProfile("29f0b8a9-422d-4e6d-9963-2357d7c3e28d")).thenReturn(true);
    when(folioClient.deleteMatchProfile("92e8aa6b-bdb8-4f19-b3e2-29de0d55d995")).thenReturn(true);

    var jobProfile = new ProfileHydration(folioClient).hydrate(26, graph);

    assertTrue(jobProfile.isEmpty());
    verify(folioClient).deleteMatchProfile("92e8aa6b-bdb8-4f19-b3e2-29de0d55d995");
    verify(folioClient).deleteActionProfile("29f0b8a9-422d-4e6d-9963-2357d7c3e28d");
    verify(folioClient).deleteMappingProfile("82de8419-688a-4594-97a9-a881aa27e8de");
  }

  @Test
  public void hydrateReturnsEmptyWhenMappingProfileCreationFailsBeforeCreatingDependents() {
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.empty());

    var jobProfile = new ProfileHydration(folioClient).hydrate(26, graph);

    assertTrue(jobProfile.isEmpty());
    verify(folioClient, never()).createActionProfile(any());
    verify(folioClient, never()).createMatchProfile(any());
    verify(folioClient, never()).createJobProfile(any());
  }

  @Test
  public void hydrateRejectsUnsupportedMatchDetailsBeforeCreatingMatchProfile() throws IOException {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.INSTANCE.toString(), EntityType.INSTANCE.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(),
      ActionProfile.FolioRecord.INSTANCE.toString(), 0);
    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(),
      EntityType.INSTANCE.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());

    String mappingProfileResponse = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.deleteMappingProfile("82de8419-688a-4594-97a9-a881aa27e8de")).thenReturn(true);
    when(folioClient.deleteActionProfile("29f0b8a9-422d-4e6d-9963-2357d7c3e28d")).thenReturn(true);

    var result = new ProfileHydration(folioClient).hydrate(26, graph);

    assertTrue(result.isEmpty());
    verify(folioClient, never()).createMatchProfile(any());
    verify(folioClient).deleteActionProfile("29f0b8a9-422d-4e6d-9963-2357d7c3e28d");
    verify(folioClient).deleteMappingProfile("82de8419-688a-4594-97a9-a881aa27e8de");
  }

  @Test
  public void hydrateRejectsActionProfileWithoutMappingBeforeCreatingObjects() {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(),
      EntityType.HOLDINGS.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(),
      ActionProfile.FolioRecord.HOLDINGS.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());

    var result = new ProfileHydration(folioClient).hydrate(17, graph);

    assertTrue(result.isEmpty());
    verifyNoInteractions(folioClient);
  }

  @Test
  public void hydrateRejectsMatchModifyMarcBibBeforeCreatingObjects() {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(),
      EntityType.MARC_BIBLIOGRAPHIC.toString(), 0);
    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.MODIFY.toString(),
      ActionProfile.FolioRecord.MARC_BIBLIOGRAPHIC.toString(), 0);
    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(),
      EntityType.MARC_BIBLIOGRAPHIC.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());

    var result = new ProfileHydration(folioClient).hydrate(30, graph);

    assertTrue(result.isEmpty());
    verifyNoInteractions(folioClient);
  }

  @Test
  public void testGetProfileType() {
    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    ProfileType profileType = ProfileHydration.getProfileType(jobProfile);
    assertEquals(ProfileType.JOB_PROFILE, profileType);

    Profile matchProfile = new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);
    profileType = ProfileHydration.getProfileType(matchProfile);
    assertEquals(ProfileType.MATCH_PROFILE, profileType);

    Profile actionProfile = new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(), ActionProfile.FolioRecord.INSTANCE.toString(), 0);
    profileType = ProfileHydration.getProfileType(actionProfile);
    assertEquals(ProfileType.ACTION_PROFILE, profileType);

    Profile mappingProfile = new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);
    profileType = ProfileHydration.getProfileType(mappingProfile);
    assertEquals(ProfileType.MAPPING_PROFILE, profileType);

    Profile mock = mock(Profile.class);
    profileType = ProfileHydration.getProfileType(mock);
    assertNull(profileType);

  }
}
