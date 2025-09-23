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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReferenceDataManagerTest {

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

    private ReferenceDataManager referenceDataManager;

    @Before
    public void setup() throws IOException {
        referenceDataManager = new ReferenceDataManager(() -> baseUrlBuilder, "token");
        referenceDataManager.setHttpClient(httpClient);

        when(httpClient.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);
        when(response.isSuccessful()).thenReturn(true);
        when(response.body()).thenReturn(body);
    }

    @Test
    public void testGetReferenceDataForInstanceTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> instanceTypes = referenceDataManager.getReferenceData("instance-types");
        assertNotNull(instanceTypes);
        assertFalse(instanceTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("instance-types");
    }

    @Test
    public void testGetReferenceDataForIdentifierTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/identifier_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> identifierTypes = referenceDataManager.getReferenceData("identifier-types");
        assertNotNull(identifierTypes);
        assertFalse(identifierTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("identifier-types");
    }

    @Test
    public void testGetReferenceDataForContributorTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/contributor_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> contributorTypes = referenceDataManager.getReferenceData("contributor-types");
        assertNotNull(contributorTypes);
        assertFalse(contributorTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("contributor-types");
    }

    @Test
    public void testGetReferenceDataForInstanceFormats() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_formats_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> instanceFormats = referenceDataManager.getReferenceData("instance-formats");
        assertNotNull(instanceFormats);
        assertFalse(instanceFormats.isEmpty());
        verify(baseUrlBuilder).addPathSegments("instance-formats");
    }

    @Test
    public void testGetReferenceDataForClassificationTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/classification_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> classificationTypes = referenceDataManager.getReferenceData("classification-types");
        assertNotNull(classificationTypes);
        assertFalse(classificationTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("classification-types");
    }

    @Test
    public void testGetReferenceDataForHoldingsTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/holdings_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> holdingsTypes = referenceDataManager.getReferenceData("holdings-types");
        assertNotNull(holdingsTypes);
        assertFalse(holdingsTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("holdings-types");
    }

    @Test
    public void testGetReferenceDataForMaterialTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/material_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> materialTypes = referenceDataManager.getReferenceData("material-types");
        assertNotNull(materialTypes);
        assertFalse(materialTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("material-types");
    }

    @Test
    public void testGetReferenceDataForCallNumberTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/call_number_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> callNumberTypes = referenceDataManager.getReferenceData("call-number-types");
        assertNotNull(callNumberTypes);
        assertFalse(callNumberTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("call-number-types");
    }

    @Test
    public void testGetReferenceDataForItemNoteTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/item_note_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> itemNoteTypes = referenceDataManager.getReferenceData("item-note-types");
        assertNotNull(itemNoteTypes);
        assertFalse(itemNoteTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("item-note-types");
    }

    @Test
    public void testGetReferenceDataForLoanTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/loan_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> loanTypes = referenceDataManager.getReferenceData("loan-types");
        assertNotNull(loanTypes);
        assertFalse(loanTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("loan-types");
    }

    @Test
    public void testGetReferenceDataForStatisticalCodes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/statistical_codes_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> statisticalCodes = referenceDataManager.getReferenceData("statistical-codes");
        assertNotNull(statisticalCodes);
        assertFalse(statisticalCodes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("statistical-codes");
    }

    @Test
    public void testGetReferenceDataForLocations() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/locations_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> locations = referenceDataManager.getReferenceData("locations");
        assertNotNull(locations);
        assertFalse(locations.isEmpty());
        verify(baseUrlBuilder).addPathSegments("locations");
    }

    @Test
    public void testGetReferenceDataForServicePoints() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/service_points_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> servicePoints = referenceDataManager.getReferenceData("service-points");
        assertNotNull(servicePoints);
        assertFalse(servicePoints.isEmpty());
        verify(baseUrlBuilder).addPathSegments("service-points");
    }

    @Test
    public void testGetReferenceDataForAuthorityNoteTypes() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/authority_note_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> authorityNoteTypes = referenceDataManager.getReferenceData("authority-note-types");
        assertNotNull(authorityNoteTypes);
        assertFalse(authorityNoteTypes.isEmpty());
        verify(baseUrlBuilder).addPathSegments("authority-note-types");
    }

    @Test
    public void testGetReferenceDataForAuthoritySourceFiles() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/authority_source_files_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> authoritySourceFiles = referenceDataManager.getReferenceData("authority-source-files");
        assertNotNull(authoritySourceFiles);
        assertFalse(authoritySourceFiles.isEmpty());
        verify(baseUrlBuilder).addPathSegments("authority-source-files");
    }

    @Test
    public void testGetRandomValidId() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        Optional<String> randomId = referenceDataManager.getRandomValidId("instance-types");
        assertTrue(randomId.isPresent());
        assertNotNull(randomId.get());
    }

    @Test
    public void testIsValidReference() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        boolean isValid = referenceDataManager.isValidReference("instance-types", "6312d172-f0cf-40f6-b27d-9fa8feaf332f");
        assertTrue(isValid);
    }

    @Test
    public void testGetAllReferenceDataTypes() {
        Set<String> allTypes = referenceDataManager.getAllReferenceDataTypes();
        assertNotNull(allTypes);
        assertFalse(allTypes.isEmpty());
        assertTrue(allTypes.contains("instance-types"));
        assertTrue(allTypes.contains("identifier-types"));
        assertTrue(allTypes.contains("contributor-types"));
        assertTrue(allTypes.contains("instance-formats"));
        assertTrue(allTypes.contains("classification-types"));
        assertTrue(allTypes.contains("holdings-types"));
        assertTrue(allTypes.contains("material-types"));
        assertTrue(allTypes.contains("call-number-types"));
        assertTrue(allTypes.contains("item-note-types"));
        assertTrue(allTypes.contains("loan-types"));
        assertTrue(allTypes.contains("statistical-codes"));
        assertTrue(allTypes.contains("locations"));
        assertTrue(allTypes.contains("service-points"));
        assertTrue(allTypes.contains("statistical-code-types"));
        assertTrue(allTypes.contains("authority-note-types"));
        assertTrue(allTypes.contains("authority-source-files"));
        assertTrue(allTypes.contains("subject-sources"));
        assertTrue(allTypes.contains("subject-types"));
    }

    @Test
    public void testGracefulDegradationWhenReferenceDataUnavailable() throws IOException {
        List<JsonNode> result = referenceDataManager.getReferenceData("non-existent-type");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testTenantContextHandling() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> instanceTypes = referenceDataManager.getReferenceData("instance-types");
        assertNotNull(instanceTypes);
        verify(httpClient).newCall(argThat(request ->
            request.header("x-okapi-token") != null
        ));
    }

    @Test
    public void testCachingMechanism() throws IOException {
        when(baseUrlBuilder.addPathSegments(anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.addQueryParameter(anyString(), anyString())).thenReturn(baseUrlBuilder);
        when(baseUrlBuilder.build()).thenReturn(HttpUrl.get("http://example.com"));

        String content = Resources.toString(Resources.getResource("reference_data/instance_types_response.json"), StandardCharsets.UTF_8);
        when(body.string()).thenReturn(content);

        List<JsonNode> firstCall = referenceDataManager.getReferenceData("instance-types");
        List<JsonNode> secondCall = referenceDataManager.getReferenceData("instance-types");

        assertNotNull(firstCall);
        assertNotNull(secondCall);
        verify(httpClient, times(1)).newCall(any());
    }
}