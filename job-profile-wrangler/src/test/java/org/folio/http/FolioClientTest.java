package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.io.Resources;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
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
    folioClient = new FolioClient(() -> baseUrlBuilder, "token", null, httpClient);

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
  public void testAddsTenantAndOkapiUrlHeaders() throws IOException {
    folioClient = new FolioClient(
      httpClient,
      () -> baseUrlBuilder,
      "token",
      "diku",
      "http://wiremock:8080");

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addPathSegment(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("job_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    folioClient.getJobProfileSnapshot("123");

    ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
    verify(httpClient).newCall(requestCaptor.capture());
    Request request = requestCaptor.getValue();
    assertEquals("token", request.header("x-okapi-token"));
    assertEquals("diku", request.header("x-okapi-tenant"));
    assertEquals("http://wiremock:8080", request.header("x-okapi-url"));
  }

  @Test
  public void testGetMappingRulesUsesMappingRulesEndpoint() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addPathSegment(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    when(body.string()).thenReturn("{}");

    Optional<JsonNode> mappingRules = folioClient.getMappingRules("marc-bib");

    assertTrue(mappingRules.isPresent());
    verify(baseUrlBuilder).addPathSegments("mapping-rules");
    verify(baseUrlBuilder).addPathSegment("marc-bib");
  }

  @Test
  public void getUploadUrlUsesRequiredCamelCaseFileNameParameter() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("{}");

    folioClient.getUploadUrl("records.mrc");

    verify(baseUrlBuilder).addPathSegments("data-import/uploadUrl");
    verify(baseUrlBuilder).addQueryParameter("fileName", "records.mrc");
    verify(baseUrlBuilder, never()).addQueryParameter(eq("filename"), anyString());
  }


  @Test
  public void getJobProfilesPropagatesPageFailures() {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(response.isSuccessful()).thenReturn(false);

    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles();

    assertThrows(IllegalStateException.class, jobProfiles::count);
  }

  @Test
  public void getJobProfilesFailsClearlyWhenTotalRecordsIsMissing() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("{\"jobProfiles\":[]}");

    Stream<JsonNode> jobProfiles = folioClient.getJobProfiles();

    IllegalStateException error = assertThrows(IllegalStateException.class, jobProfiles::count);
    assertTrue(error.getCause().getMessage().contains("totalRecords"));
  }

  @Test
  public void findSourceRecordByMarcControlNumberPagesUntilMatch() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    Call firstCall = mock(Call.class);
    Call secondCall = mock(Call.class);
    Response firstResponse = mock(Response.class);
    Response secondResponse = mock(Response.class);
    ResponseBody firstBody = mock(ResponseBody.class);
    ResponseBody secondBody = mock(ResponseBody.class);

    when(httpClient.newCall(any())).thenReturn(firstCall, secondCall);
    when(firstCall.execute()).thenReturn(firstResponse);
    when(secondCall.execute()).thenReturn(secondResponse);
    when(firstResponse.isSuccessful()).thenReturn(true);
    when(secondResponse.isSuccessful()).thenReturn(true);
    when(firstResponse.body()).thenReturn(firstBody);
    when(secondResponse.body()).thenReturn(secondBody);
    when(firstBody.string()).thenReturn("""
      {
        "totalRecords": 1001,
        "sourceRecords": [{
          "recordId": "first-page",
          "parsedRecord": {"content": {"fields": [{"001": "not-it"}]}}
        }]
      }
      """);
    when(secondBody.string()).thenReturn("""
      {
        "totalRecords": 1001,
        "sourceRecords": [{
          "recordId": "target-record",
          "parsedRecord": {"content": "{\\"fields\\":[{\\"001\\":\\"auth-target\\"}]}"}
        }]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_AUTHORITY", "auth-target");

    assertTrue(result.isPresent());
    assertEquals("target-record", result.get().get("recordId").asText());
    verify(baseUrlBuilder).addQueryParameter("offset", "0");
    verify(baseUrlBuilder).addQueryParameter("offset", "1000");
  }

  @Test
  public void findSourceRecordByMarcControlNumberMatchesFormerControlNumberIn035() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("""
      {
        "totalRecords": 1,
        "sourceRecords": [{
          "recordId": "target-record",
          "parsedRecord": {
            "content": {
              "fields": [
                {"001": "in00000000004"},
                {"035": {"subfields": [{"a": "original-control-number"}]}}
              ]
            }
          }
        }]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_BIB", "original-control-number");

    assertTrue(result.isPresent());
    assertEquals("target-record", result.get().get("recordId").asText());
  }

  @Test
  public void findSourceRecordByMarcControlNumberRefusesConflicting001And035Matches() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("""
      {
        "totalRecords": 2,
        "sourceRecords": [
          {
            "recordId": "035-fallback",
            "parsedRecord": {"content": {"fields": [
              {"001": "in00000000004"},
              {"035": {"subfields": [{"a": "shared-control-number"}]}}
            ]}}
          },
          {
            "recordId": "exact-001",
            "parsedRecord": {"content": {"fields": [
              {"001": "shared-control-number"}
            ]}}
          }
        ]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_BIB", "shared-control-number");

    assertTrue(result.isEmpty());
  }

  @Test
  public void findSourceRecordByMarcControlNumberRefusesAmbiguous001Matches() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("""
      {
        "totalRecords": 2,
        "sourceRecords": [
          {
            "recordId": "first-001",
            "parsedRecord": {"content": {"fields": [
              {"001": "shared-control-number"}
            ]}}
          },
          {
            "recordId": "second-001",
            "parsedRecord": {"content": {"fields": [
              {"001": "shared-control-number"}
            ]}}
          }
        ]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_BIB", "shared-control-number");

    assertTrue(result.isEmpty());
  }

  @Test
  public void findSourceRecordByMarcControlNumberRefusesAmbiguous035Fallbacks() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("""
      {
        "totalRecords": 2,
        "sourceRecords": [
          {
            "recordId": "first-035",
            "parsedRecord": {"content": {"fields": [
              {"001": "in00000000004"},
              {"035": {"subfields": [{"a": "shared-control-number"}]}}
            ]}}
          },
          {
            "recordId": "second-035",
            "parsedRecord": {"content": {"fields": [
              {"001": "in00000000005"},
              {"035": {"subfields": [{"a": "shared-control-number"}]}}
            ]}}
          }
        ]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_BIB", "shared-control-number");

    assertTrue(result.isEmpty());
  }

  @Test
  public void findSourceRecordByMarcControlNumberSkipsMalformedParsedRecordAndKeepsFallback() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("""
      {
        "totalRecords": 2,
        "sourceRecords": [
          {
            "recordId": "035-fallback",
            "parsedRecord": {"content": {"fields": [
              {"001": "in00000000004"},
              {"035": {"subfields": [{"a": "original-control-number"}]}}
            ]}}
          },
          {
            "recordId": "malformed-record",
            "parsedRecord": {"content": "{not-json"}
          }
        ]
      }
      """);

    Optional<JsonNode> result = folioClient.findSourceRecordByMarcControlNumber("MARC_BIB", "original-control-number");

    assertTrue(result.isPresent());
    assertEquals("035-fallback", result.get().get("recordId").asText());
  }

  @Test
  public void findInstanceByIdentifierEscapesCqlStringValues() throws IOException {
    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));
    when(body.string()).thenReturn("{\"instances\":[]}");

    folioClient.findInstanceByIdentifier("abc\"\\def*", "type\"\\id?");

    ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
    verify(baseUrlBuilder).addQueryParameter(eq("query"), queryCaptor.capture());
    assertEquals(
      "(identifiers=\"*abc\\\"\\\\def\\**\" and identifiers=\"*type\\\"\\\\id\\?*\")",
      queryCaptor.getValue());
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

  @Test
  public void testCreateMappingProfile() throws IOException {
    String actionProfile = "{}";

    when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
    when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

    String content = Resources.toString(Resources.getResource("mapping_profile_response.json"), StandardCharsets.UTF_8);
    when(body.string()).thenReturn(content);

    Optional<JsonNode> createdActionProfile = folioClient.createMappingProfile(actionProfile);
    assertNotNull(createdActionProfile);
  }
}
