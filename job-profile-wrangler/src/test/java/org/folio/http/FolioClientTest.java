package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class FolioClientTest {

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

  @Before
  public void setup() throws IOException {
    folioClient = new FolioClient(() -> baseUrlBuilder, "token");
    folioClient.setHttpClient(httpClient);

    when(httpClient.newCall(any())).thenReturn(call);
    when(call.execute()).thenReturn(response);
    when(response.isSuccessful()).thenReturn(true);
    when(response.body()).thenReturn(body);
  }

  @Test
  public void testGetJobProfiles() {
    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles();
    assertNotNull(jobProfiles);
  }

  @Test
  public void testGetJobProfilesWithQueryParams() throws IOException {
    Map<String, String> queryParams = new HashMap<>();
    queryParams.put("key1", "value1");
    queryParams.put("key2", "value2");

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profiles_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles(queryParams);
    assertNotNull(jobProfiles);
    assertEquals(3, jobProfiles.count());
    verify(baseUrlBuilder, times(queryParams.size() + 3)).addQueryParameter(anyString(), anyString());
  }

  @Test
  public void testGetJobProfileSnapshot() throws IOException {
    String jobProfileId = "123";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addPathSegment(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Optional<JsonNode> jobProfileSnapshot = folioClient.getJobProfileSnapshot(jobProfileId);
    assertNotNull(jobProfileSnapshot);
  }

  @Test
  public void testCreateJobProfile() throws IOException {
    String jobProfile = "{}";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Optional<JsonNode> createdJobProfile = folioClient.createJobProfile(jobProfile);
    assertNotNull(createdJobProfile);
  }

  @Test
  public void testCreateMatchProfile() throws IOException {
    String matchProfile = "{}";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("match_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Optional<JsonNode> createdMatchProfile = folioClient.createMatchProfile(matchProfile);
    assertNotNull(createdMatchProfile);
  }

  @Test
  public void testCreateActionProfile() throws IOException {
    String actionProfile = "{}";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("action_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Optional<JsonNode> createdActionProfile = folioClient.createActionProfile(actionProfile);
    assertNotNull(createdActionProfile);
  }
}
