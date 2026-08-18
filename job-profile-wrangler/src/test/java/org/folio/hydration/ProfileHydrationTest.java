package org.folio.hydration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.Constants.OBJECT_MAPPER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfileHydrationTest {

  @Mock
  private FolioClient folioClient;

  private Graph<Profile, RegularEdge> graph;

  @BeforeEach
  void setUp() {
    graph = new DefaultDirectedGraph<>(RegularEdge.class);

    Profile jobProfile = new JobProfileNode("1", "MARC", 0);
    Profile matchProfile =
      new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);
    Profile actionProfile =
      new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(), ActionProfile.FolioRecord.INSTANCE.toString(),
        0);
    Profile mappingProfile =
      new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0);

    graph.addVertex(jobProfile);
    graph.addVertex(matchProfile);
    graph.addVertex(actionProfile);
    graph.addVertex(mappingProfile);
    graph.addEdge(jobProfile, matchProfile, new RegularEdge());
    graph.addEdge(matchProfile, actionProfile, new MatchRelationshipEdge());
    graph.addEdge(actionProfile, mappingProfile, new RegularEdge());
  }

  @DisplayName("should return hydrated job profile when all sub-profiles are successfully created")
  @Test
  void shouldReturnHydratedJobProfile_whenAllSubProfilesAreCreated() throws IOException {
    // arrange
    String mappingProfileResponse =
      Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    String actionProfileResponse =
      Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    String matchProfileResponse =
      Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    String jobProfileResponse =
      Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(folioClient.createMappingProfile(any())).thenReturn(
      Optional.of(OBJECT_MAPPER.readTree(mappingProfileResponse)));
    when(folioClient.createActionProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(actionProfileResponse)));
    when(folioClient.createMatchProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(matchProfileResponse)));
    when(folioClient.createJobProfile(any())).thenReturn(Optional.of(OBJECT_MAPPER.readTree(jobProfileResponse)));

    // act
    var jobProfile = new ProfileHydration(folioClient).hydrate(1, graph);

    // assert
    assertThat(jobProfile).isPresent();
    assertThat(jobProfile.get()).isInstanceOf(JobProfileUpdateDto.class);
  }

  @DisplayName("should return JOB_PROFILE type when profile is a JobProfileNode")
  @Test
  void shouldReturnJobProfileType_whenProfileIsJobProfileNode() {
    // act / assert
    assertThat(ProfileHydration.getProfileType(new JobProfileNode("1", "MARC", 0)))
      .isEqualTo(ProfileType.JOB_PROFILE);
  }

  @DisplayName("should return MATCH_PROFILE type when profile is a MatchProfileNode")
  @Test
  void shouldReturnMatchProfileType_whenProfileIsMatchProfileNode() {
    // act / assert
    assertThat(ProfileHydration.getProfileType(
      new MatchProfileNode("2", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0)))
      .isEqualTo(ProfileType.MATCH_PROFILE);
  }

  @DisplayName("should return ACTION_PROFILE type when profile is an ActionProfileNode")
  @Test
  void shouldReturnActionProfileType_whenProfileIsActionProfileNode() {
    // act / assert
    assertThat(ProfileHydration.getProfileType(
      new ActionProfileNode("3", ActionProfile.Action.CREATE.toString(),
        ActionProfile.FolioRecord.INSTANCE.toString(), 0)))
      .isEqualTo(ProfileType.ACTION_PROFILE);
  }

  @DisplayName("should return MAPPING_PROFILE type when profile is a MappingProfileNode")
  @Test
  void shouldReturnMappingProfileType_whenProfileIsMappingProfileNode() {
    // act / assert
    assertThat(ProfileHydration.getProfileType(
      new MappingProfileNode("4", EntityType.MARC_BIBLIOGRAPHIC.toString(), EntityType.INSTANCE.toString(), 0)))
      .isEqualTo(ProfileType.MAPPING_PROFILE);
  }

  @DisplayName("should return null when profile type is unknown")
  @Test
  void shouldReturnNull_whenProfileTypeIsUnknown() {
    // act / assert
    assertThat(ProfileHydration.getProfileType(mock(Profile.class))).isNull();
  }
}
