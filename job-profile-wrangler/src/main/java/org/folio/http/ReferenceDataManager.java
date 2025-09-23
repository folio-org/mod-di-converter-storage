package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;

public class ReferenceDataManager {
    private static final Logger LOGGER = LogManager.getLogger(ReferenceDataManager.class);

    private final String token;
    private final Supplier<HttpUrl.Builder> baseUrlBuilderSupplier;
    private OkHttpClient httpClient = new OkHttpClient();

    private final Map<String, List<JsonNode>> cache = new ConcurrentHashMap<>();
    private final Map<String, ReferenceDataEndpoint> endpointConfig = new HashMap<>();
    private final Random random = new Random();

    private static final Set<String> HIGH_PRIORITY_ENDPOINTS = Set.of(
        "instance-types", "material-types", "loan-types", "locations"
    );

    private static final Set<String> MEDIUM_PRIORITY_ENDPOINTS = Set.of(
        "identifier-types", "contributor-types", "instance-formats",
        "holdings-types", "call-number-types", "statistical-codes"
    );

    private static final Set<String> LOW_PRIORITY_ENDPOINTS = Set.of(
        "classification-types", "item-note-types", "service-points",
        "statistical-code-types", "authority-note-types",
        "authority-source-files", "subject-sources", "subject-types"
    );

    public ReferenceDataManager(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token) {
        this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;
        this.token = token;
        initializeEndpointConfiguration();
    }

    protected void setHttpClient(OkHttpClient client) {
        this.httpClient = client;
    }

    private void initializeEndpointConfiguration() {
        endpointConfig.put("instance-types", new ReferenceDataEndpoint("instance-types", "instanceTypes"));
        endpointConfig.put("identifier-types", new ReferenceDataEndpoint("identifier-types", "identifierTypes"));
        endpointConfig.put("contributor-types", new ReferenceDataEndpoint("contributor-types", "contributorTypes"));
        endpointConfig.put("instance-formats", new ReferenceDataEndpoint("instance-formats", "instanceFormats"));
        endpointConfig.put("classification-types", new ReferenceDataEndpoint("classification-types", "classificationTypes"));

        endpointConfig.put("holdings-types", new ReferenceDataEndpoint("holdings-types", "holdingsTypes"));
        endpointConfig.put("material-types", new ReferenceDataEndpoint("material-types", "mtypes"));
        endpointConfig.put("call-number-types", new ReferenceDataEndpoint("call-number-types", "callNumberTypes"));
        endpointConfig.put("item-note-types", new ReferenceDataEndpoint("item-note-types", "itemNoteTypes"));
        endpointConfig.put("loan-types", new ReferenceDataEndpoint("loan-types", "loantypes"));

        endpointConfig.put("statistical-codes", new ReferenceDataEndpoint("statistical-codes", "statisticalCodes"));
        endpointConfig.put("locations", new ReferenceDataEndpoint("locations", "locations"));
        endpointConfig.put("service-points", new ReferenceDataEndpoint("service-points", "servicepoints"));
        endpointConfig.put("statistical-code-types", new ReferenceDataEndpoint("statistical-code-types", "statisticalCodeTypes"));

        endpointConfig.put("authority-note-types", new ReferenceDataEndpoint("authority-note-types", "authorityNoteTypes"));
        endpointConfig.put("authority-source-files", new ReferenceDataEndpoint("authority-source-files", "authoritySourceFiles"));
        endpointConfig.put("subject-sources", new ReferenceDataEndpoint("subject-sources", "subjectSources"));
        endpointConfig.put("subject-types", new ReferenceDataEndpoint("subject-types", "subjectTypes"));
    }

    public List<JsonNode> getReferenceData(String referenceType) {
        if (cache.containsKey(referenceType)) {
            LOGGER.debug("Returning cached data for reference type: {}", referenceType);
            return cache.get(referenceType);
        }

        ReferenceDataEndpoint endpoint = endpointConfig.get(referenceType);
        if (endpoint == null) {
            LOGGER.warn("Unknown reference data type: {}", referenceType);
            return Collections.emptyList();
        }

        try {
            List<JsonNode> data = fetchReferenceData(endpoint);
            cache.put(referenceType, data);
            return data;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch reference data for type: {}", referenceType, e);
            return Collections.emptyList();
        }
    }

    private List<JsonNode> fetchReferenceData(ReferenceDataEndpoint endpoint) throws IOException {
        final int queryParamLimit = 2000;
        final AtomicInteger queryParamOffset = new AtomicInteger(0);
        final AtomicInteger totalRecords = new AtomicInteger(0);
        List<JsonNode> allRecords = new ArrayList<>();

        do {
            HttpUrl.Builder urlBuilder = baseUrlBuilderSupplier.get()
                .addPathSegments(endpoint.getPath())
                .addQueryParameter("limit", Integer.toString(queryParamLimit))
                .addQueryParameter("offset", Integer.toString(queryParamOffset.get()));

            if (endpoint.requiresQuery()) {
                urlBuilder.addQueryParameter("query", "cql.allRecords=1 sortBy id");
            }

            HttpUrl url = urlBuilder.build();
            LOGGER.debug("Fetching reference data from: {}", url);

            Request request = new Request.Builder()
                .url(url)
                .addHeader(OKAPI_TOKEN_HEADER, token)
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    if (response.code() == 404) {
                        LOGGER.warn("Reference data endpoint not found: {}", url);
                        return Collections.emptyList();
                    }
                    throw new IOException("Unexpected response code " + response.code() + " for " + url);
                }

                assert response.body() != null;
                String result = response.body().string();
                JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

                if (queryParamOffset.get() == 0) {
                    totalRecords.set(jsonNode.has("totalRecords") ? jsonNode.get("totalRecords").asInt() : 0);
                    if (totalRecords.get() == 0) {
                        return Collections.emptyList();
                    }
                }

                JsonNode recordsNode = jsonNode.get(endpoint.getRecordsField());
                if (recordsNode != null && recordsNode.isArray()) {
                    StreamSupport.stream(recordsNode.spliterator(), false)
                        .forEach(allRecords::add);
                }

                queryParamOffset.getAndAdd(queryParamLimit);
            }
        } while (queryParamOffset.get() < totalRecords.get());

        return allRecords;
    }

    public Optional<String> getRandomValidId(String referenceType) {
        List<JsonNode> data = getReferenceData(referenceType);
        if (data.isEmpty()) {
            return Optional.empty();
        }

        JsonNode randomRecord = data.get(random.nextInt(data.size()));
        if (randomRecord.has("id")) {
            return Optional.of(randomRecord.get("id").asText());
        }
        return Optional.empty();
    }

    public boolean isValidReference(String referenceType, String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }

        List<JsonNode> data = getReferenceData(referenceType);
        return data.stream()
            .filter(record -> record.has("id"))
            .anyMatch(record -> id.equals(record.get("id").asText()));
    }

    public Set<String> getAllReferenceDataTypes() {
        return new HashSet<>(endpointConfig.keySet());
    }

    public void clearCache() {
        cache.clear();
    }

    public void clearCache(String referenceType) {
        cache.remove(referenceType);
    }

    public CacheStats getCacheStats() {
        return new CacheStats(cache.size(), cache.entrySet().stream()
            .mapToInt(entry -> entry.getValue().size()).sum());
    }

    public enum Priority {
        HIGH, MEDIUM, LOW
    }

    public Priority getPriority(String referenceType) {
        if (HIGH_PRIORITY_ENDPOINTS.contains(referenceType)) {
            return Priority.HIGH;
        } else if (MEDIUM_PRIORITY_ENDPOINTS.contains(referenceType)) {
            return Priority.MEDIUM;
        } else {
            return Priority.LOW;
        }
    }

    private static class ReferenceDataEndpoint {
        private final String path;
        private final String recordsField;

        public ReferenceDataEndpoint(String path, String recordsField) {
            this.path = path;
            this.recordsField = recordsField;
        }

        public String getPath() {
            return path;
        }

        public String getRecordsField() {
            return recordsField;
        }

        public boolean requiresQuery() {
            return true;
        }
    }

    public static class CacheStats {
        private final int cachedTypes;
        private final int totalRecords;

        public CacheStats(int cachedTypes, int totalRecords) {
            this.cachedTypes = cachedTypes;
            this.totalRecords = totalRecords;
        }

        public int getCachedTypes() {
            return cachedTypes;
        }

        public int getTotalRecords() {
            return totalRecords;
        }

        @Override
        public String toString() {
            return String.format("CacheStats{cachedTypes=%d, totalRecords=%d}", cachedTypes, totalRecords);
        }
    }
}