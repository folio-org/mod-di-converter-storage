package org.folio.hydration;

import com.google.common.io.Resources;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
