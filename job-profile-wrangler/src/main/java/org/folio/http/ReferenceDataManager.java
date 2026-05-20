package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TENANT_HEADER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;
import static org.folio.Constants.OKAPI_URL_HEADER;

/**
 * Manages reference data fetching and caching for FOLIO tenant reference data endpoints.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. The reference data cache
 * uses {@link ConcurrentHashMap} for safe concurrent access. Note that individual fetch
 * operations are not atomic - concurrent requests for uncached data may result in
 * duplicate fetches, but this is safe (last write wins, data is identical).
 */
public class ReferenceDataManager {
    private static final Logger LOGGER = LogManager.getLogger(ReferenceDataManager.class);

    private final String token;
    private final String tenantId;
    private final String okapiUrl;
    private final Supplier<HttpUrl.Builder> baseUrlBuilderSupplier;
    private final OkHttpClient httpClient;

    private final Map<String, List<JsonNode>> cache = new ConcurrentHashMap<>();
    private final Map<String, ReferenceDataEndpoint> endpointConfig;
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

    /**
     * Creates a ReferenceDataManager with a default OkHttpClient.
     *
     * @param baseUrlBuilderSupplier supplier for the base URL builder
     * @param token authentication token
     */
    public ReferenceDataManager(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token) {
        this(new OkHttpClient(), baseUrlBuilderSupplier, token, null, null);
    }

    /**
     * Creates a ReferenceDataManager with a default OkHttpClient and tenant ID.
     *
     * @param baseUrlBuilderSupplier supplier for the base URL builder
     * @param token authentication token
     * @param tenantId tenant identifier
     */
    public ReferenceDataManager(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token, String tenantId) {
        this(new OkHttpClient(), baseUrlBuilderSupplier, token, tenantId, null);
    }

    public ReferenceDataManager(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token, String tenantId, String okapiUrl) {
        this(new OkHttpClient(), baseUrlBuilderSupplier, token, tenantId, okapiUrl);
    }

    /**
     * Creates a ReferenceDataManager with an injected OkHttpClient.
     * This constructor is preferred for testability.
     *
     * @param httpClient the HTTP client to use for requests
     * @param baseUrlBuilderSupplier supplier for the base URL builder
     * @param token authentication token
     * @param tenantId tenant identifier (may be null)
     */
    public ReferenceDataManager(
        OkHttpClient httpClient,
        Supplier<HttpUrl.Builder> baseUrlBuilderSupplier,
        String token,
        String tenantId
    ) {
        this(httpClient, baseUrlBuilderSupplier, token, tenantId, null);
    }

    public ReferenceDataManager(
        OkHttpClient httpClient,
        Supplier<HttpUrl.Builder> baseUrlBuilderSupplier,
        String token,
        String tenantId,
        String okapiUrl
    ) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.baseUrlBuilderSupplier = Objects.requireNonNull(baseUrlBuilderSupplier, "baseUrlBuilderSupplier must not be null");
        this.token = token;
        this.tenantId = tenantId;
        this.okapiUrl = okapiUrl;
        this.endpointConfig = initializeEndpointConfiguration();
    }

    /**
     * Creates the endpoint configuration map.
     * Returns an immutable map for thread safety.
     */
    private static Map<String, ReferenceDataEndpoint> initializeEndpointConfiguration() {
        Map<String, ReferenceDataEndpoint> config = new HashMap<>();

        config.put("instance-types", new ReferenceDataEndpoint("instance-types", "instanceTypes"));
        config.put("identifier-types", new ReferenceDataEndpoint("identifier-types", "identifierTypes"));
        config.put("contributor-types", new ReferenceDataEndpoint("contributor-types", "contributorTypes"));
        config.put("instance-formats", new ReferenceDataEndpoint("instance-formats", "instanceFormats"));
        config.put("classification-types", new ReferenceDataEndpoint("classification-types", "classificationTypes"));

        config.put("holdings-types", new ReferenceDataEndpoint("holdings-types", "holdingsTypes"));
        config.put("material-types", new ReferenceDataEndpoint("material-types", "mtypes"));
        config.put("call-number-types", new ReferenceDataEndpoint("call-number-types", "callNumberTypes"));
        config.put("item-note-types", new ReferenceDataEndpoint("item-note-types", "itemNoteTypes"));
        config.put("loan-types", new ReferenceDataEndpoint("loan-types", "loantypes"));

        config.put("statistical-codes", new ReferenceDataEndpoint("statistical-codes", "statisticalCodes"));
        config.put("locations", new ReferenceDataEndpoint("locations", "locations"));
        config.put("service-points", new ReferenceDataEndpoint("service-points", "servicepoints"));
        config.put("statistical-code-types", new ReferenceDataEndpoint("statistical-code-types", "statisticalCodeTypes"));

        config.put("authority-note-types", new ReferenceDataEndpoint("authority-note-types", "authorityNoteTypes"));
        config.put("authority-source-files", new ReferenceDataEndpoint("authority-source-files", "authoritySourceFiles"));
        config.put("subject-sources", new ReferenceDataEndpoint("subject-sources", "subjectSources"));
        config.put("subject-types", new ReferenceDataEndpoint("subject-types", "subjectTypes"));

        return Collections.unmodifiableMap(config);
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

            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader(OKAPI_TOKEN_HEADER, token);
            if (tenantId != null) {
                requestBuilder.addHeader(OKAPI_TENANT_HEADER, tenantId);
            }
            if (okapiUrl != null) {
                requestBuilder.addHeader(OKAPI_URL_HEADER, okapiUrl);
            }
            Request request = requestBuilder.get().build();

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

    /**
     * Looks up a reference data ID by name.
     * This is useful for finding IDs for well-known reference data entries like
     * identifier types (e.g., "System control number").
     *
     * @param referenceType the type of reference data (e.g., "identifier-types")
     * @param name the name to search for (case-insensitive)
     * @return Optional containing the ID if found, empty otherwise
     */
    public Optional<String> getIdByName(String referenceType, String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }

        List<JsonNode> data = getReferenceData(referenceType);
        return data.stream()
            .filter(record -> record.has("name") && record.has("id"))
            .filter(record -> name.equalsIgnoreCase(record.get("name").asText()))
            .map(record -> record.get("id").asText())
            .findFirst();
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
