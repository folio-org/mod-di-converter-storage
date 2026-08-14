package org.folio.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FolioClientTest {

  @Mock
  private HttpUrl.Builder baseUrlBuilder;

  @Mock
  private OkHttpClient httpClient;

  @Mock
  private Call call;

  @Mock
  private Response response;

  @Mock
  private ResponseBody body;

  private FolioClient folioClient;

  @BeforeEach
  void setup() throws IOException {
    folioClient = new FolioClient(() -> baseUrlBuilder, "token");
    folioClient.setHttpClient(httpClient);

    when(httpClient.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(response);
    when(response.isSuccessful()).thenReturn(true);
    when(response.body()).thenReturn(body);
  }

  @DisplayName("should return non-null stream when getting job profiles")
  @Test
  void shouldReturnNonNullStream_whenGettingJobProfiles() {
    // act
    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles();

    // assert
    assertThat(jobProfiles).isNotNull();
  }

  @DisplayName("should return profiles matching query params when getting job profiles with query params")
  @Test
  void shouldReturnProfilesMatchingQueryParams_whenGettingJobProfilesWithQueryParams() throws IOException {
    // arrange
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("key1", "value1");
    queryParams.put("key2", "value2");

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profiles_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles(queryParams);

    // assert
    assertThat(jobProfiles).isNotNull().hasSize(3);
    verify(baseUrlBuilder, times(queryParams.size() + 3)).addQueryParameter(anyString(), anyString());
  }

  @DisplayName("should return snapshot when getting job profile snapshot by id")
  @Test
  void shouldReturnSnapshot_whenGettingJobProfileSnapshotById() throws IOException {
    // arrange
    String jobProfileId = "123";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addPathSegment(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Optional<JsonNode> jobProfileSnapshot = folioClient.getJobProfileSnapshot(jobProfileId);

    // assert
    assertThat(jobProfileSnapshot).isNotNull();
  }

  @DisplayName("should return created profile when creating a job profile")
  @Test
  void shouldReturnCreatedProfile_whenCreatingJobProfile() throws IOException {
    // arrange
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Optional<JsonNode> createdJobProfile = folioClient.createJobProfile("{}");

    // assert
    assertThat(createdJobProfile).isNotNull();
  }

  @DisplayName("should return created profile when creating a match profile")
  @Test
  void shouldReturnCreatedProfile_whenCreatingMatchProfile() throws IOException {
    // arrange
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Optional<JsonNode> createdMatchProfile = folioClient.createMatchProfile("{}");

    // assert
    assertThat(createdMatchProfile).isNotNull();
  }

  @DisplayName("should return created profile when creating an action profile")
  @Test
  void shouldReturnCreatedProfile_whenCreatingActionProfile() throws IOException {
    // arrange
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Optional<JsonNode> createdActionProfile = folioClient.createActionProfile("{}");

    // assert
    assertThat(createdActionProfile).isNotNull();
  }

  @DisplayName("should return created profile when creating a mapping profile")
  @Test
  void shouldReturnCreatedProfile_whenCreatingMappingProfile() throws IOException {
    // arrange
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    // act
    Optional<JsonNode> createdMappingProfile = folioClient.createMappingProfile("{}");

    // assert
    assertThat(createdMappingProfile).isNotNull();
  }
}
