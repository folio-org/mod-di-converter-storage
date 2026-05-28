package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.folio.Constants.ACCESS_TOKEN_COOKIE_NAME;
import static org.folio.Constants.JSON_MEDIA_TYPE;
import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TENANT_HEADER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;
import static org.folio.Constants.OKAPI_URL_HEADER;

public class FolioClient {
  private static final Logger LOGGER = LogManager.getLogger(FolioClient.class);
  private final OkHttpClient httpClient;
  private final String token;
  private final String tenantId;
  private final String okapiUrl;
  private final Supplier<HttpUrl.Builder> baseUrlBuilderSupplier;

  /**
   * Creates a FolioClient with an existing authentication token.
   * Use this constructor when you already have a valid FOLIO/Okapi token.
   *
   * @param baseUrlBuilderSupplier supplier for the base URL builder
   * @param token the authentication token (from prior login)
   */
  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token) {
    this(baseUrlBuilderSupplier, token, null, null, new OkHttpClient());
  }

  /**
   * Creates a FolioClient with an existing authentication token and tenant ID.
   * Use this constructor when you already have a valid FOLIO/Okapi token.
   *
   * @param baseUrlBuilderSupplier supplier for the base URL builder
   * @param token the authentication token (from prior login)
   * @param tenantId the FOLIO tenant identifier
   */
  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token, String tenantId) {
    this(baseUrlBuilderSupplier, token, tenantId, null, new OkHttpClient());
  }

  /**
   * Creates a FolioClient with an existing authentication token, tenant ID, and custom HTTP client.
   * Use this constructor when you already have a valid FOLIO/Okapi token.
   *
   * @param baseUrlBuilderSupplier supplier for the base URL builder
   * @param token the authentication token (from prior login)
   * @param tenantId the FOLIO tenant identifier
   * @param httpClient custom OkHttpClient instance
   */
  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token, String tenantId, OkHttpClient httpClient) {
    this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;
    this.token = token;
    this.tenantId = tenantId;
    this.okapiUrl = null;
    this.httpClient = httpClient;
  }

  public FolioClient(OkHttpClient httpClient, Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token,
                     String tenantId, String okapiUrl) {
    this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;
    this.token = token;
    this.tenantId = tenantId;
    this.okapiUrl = okapiUrl;
    this.httpClient = httpClient;
  }

  /**
   * Creates a FolioClient by authenticating with username and password.
   * Use this constructor when you need to perform a fresh login to FOLIO.
   *
   * @param baseUrlBuilderSupplier supplier for the base URL builder
   * @param tenantId the FOLIO tenant identifier
   * @param username the FOLIO username
   * @param password the FOLIO password
   * @throws IllegalStateException if authentication fails
   */
  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String tenantId, String username, String password) {
    this(baseUrlBuilderSupplier, tenantId, username, password, null, new OkHttpClient());
  }

  /**
   * Creates a FolioClient by authenticating with username and password using a custom HTTP client.
   * Use this constructor when you need to perform a fresh login to FOLIO with custom HTTP settings.
   *
   * @param baseUrlBuilderSupplier supplier for the base URL builder
   * @param tenantId the FOLIO tenant identifier
   * @param username the FOLIO username
   * @param password the FOLIO password
   * @param httpClient custom OkHttpClient instance
   * @throws IllegalStateException if authentication fails
   */
  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String tenantId, String username, String password, OkHttpClient httpClient) {
    this(baseUrlBuilderSupplier, tenantId, username, password, null, httpClient);
  }

  private FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String tenantId, String username,
                     String password, String okapiUrl, OkHttpClient httpClient) {
    this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;
    this.tenantId = tenantId;
    this.okapiUrl = okapiUrl;
    this.httpClient = httpClient;

    Optional<String> okapiToken = getOkapiToken(httpClient, baseUrlBuilderSupplier.get(), tenantId, username, password);
    if (okapiToken.isEmpty()) {
      throw new IllegalStateException("Could not get okapi token");
    }
    this.token = okapiToken.get();
  }

  /**
   * Adds common FOLIO headers to a request builder.
   * Includes x-okapi-token and x-okapi-tenant if available.
   */
  private Request.Builder addFolioHeaders(Request.Builder builder) {
    builder.addHeader(OKAPI_TOKEN_HEADER, token);
    if (tenantId != null) {
      builder.addHeader(OKAPI_TENANT_HEADER, tenantId);
    }
    if (okapiUrl != null) {
      builder.addHeader(OKAPI_URL_HEADER, okapiUrl);
    }
    return builder;
  }

  /**
   * Returns a lazy stream of all job profiles from the FOLIO tenant.
   * Handles pagination internally, fetching up to 3000 profiles per request.
   *
   * @return stream of job profile JSON nodes
   */
  public Stream<JsonNode> getJobProfiles() {
    return getJobProfiles(null);
  }

  /**
   * Returns a lazy stream of job profiles matching the specified query parameters.
   * Handles pagination internally, fetching up to 3000 profiles per request.
   * Results are sorted by ID for consistent ordering.
   *
   * @param queryParams optional query parameters (use "query" key for CQL filter)
   * @return stream of job profile JSON nodes
   */
  public Stream<JsonNode> getJobProfiles(Map<String, String> queryParams) {
    return getProfiles("data-import-profiles/jobProfiles", "jobProfiles", queryParams, "job profiles");
  }

  public Stream<JsonNode> getActionProfiles(Map<String, String> queryParams) {
    return getProfiles("data-import-profiles/actionProfiles", "actionProfiles", queryParams, "action profiles");
  }

  public Stream<JsonNode> getMappingProfiles(Map<String, String> queryParams) {
    return getProfiles("data-import-profiles/mappingProfiles", "mappingProfiles", queryParams, "mapping profiles");
  }

  public Stream<JsonNode> getMatchProfiles(Map<String, String> queryParams) {
    return getProfiles("data-import-profiles/matchProfiles", "matchProfiles", queryParams, "match profiles");
  }

  private Stream<JsonNode> getProfiles(
      String pathSegments,
      String collectionField,
      Map<String, String> queryParams,
      String profileType) {
    final int queryParamLimit = 3000;
    final AtomicInteger queryParamOffset = new AtomicInteger(0);
    final AtomicInteger totalRecords = new AtomicInteger(0);

    return Stream.generate(() -> {

        if (totalRecords.get() > 0 && queryParamOffset.get() >= totalRecords.get()) {
          return null;
        }

        HttpUrl.Builder intermediateUrlBuilder = baseUrlBuilderSupplier.get()
          .addPathSegments(pathSegments)
          .addQueryParameter("limit", Integer.toString(queryParamLimit))
          .addQueryParameter("offset", Integer.toString(queryParamOffset.get()));

        List<String> queryQueryParamList = new ArrayList<>();
        if (queryParams != null && !queryParams.isEmpty()) {
          for (var entry : queryParams.entrySet()) {
            if (!entry.getKey().equals("query")) {
              intermediateUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            } else {
              queryQueryParamList.add(entry.getValue());
            }
          }
        }
        queryQueryParamList.add("cql.allRecords=1 sortBy id");
        String query = String.join(" and ", queryQueryParamList);
        intermediateUrlBuilder.addQueryParameter("query", query);
        HttpUrl url = intermediateUrlBuilder.build();
        LOGGER.info("Query: {}", url);
        Request request = addFolioHeaders(new Request.Builder()
          .url(url))
          .get()
          .build();

        try (Response response = httpClient.newCall(request).execute()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          if (response.body() == null) {
            throw new IOException("Response body is null for " + profileType + " request");
          }
          String result = response.body().string();
          JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

          if (queryParamOffset.get() == 0) {
            JsonNode totalRecordsNode = jsonNode.get("totalRecords");
            if (totalRecordsNode == null || !totalRecordsNode.canConvertToInt()) {
              throw new IOException(profileType + " response is missing numeric totalRecords");
            }
            totalRecords.set(totalRecordsNode.asInt());
            if (totalRecords.get() == 0) {
              return null;
            }
          }

          JsonNode profilesNode = jsonNode.get(collectionField);
          if (profilesNode == null || !profilesNode.isArray()) {
            throw new IOException(profileType + " response is missing " + collectionField + " array");
          }

          queryParamOffset.getAndAdd(queryParamLimit);
          return StreamSupport.stream(profilesNode.spliterator(), false);
        } catch (IOException e) {
          throw new IllegalStateException("Failed to fetch " + profileType + " page at offset "
            + queryParamOffset.get(), e);
        }
      }).takeWhile(Objects::nonNull)
      .flatMap(Function.identity());
  }

  public Optional<JsonNode> getJobProfileSnapshot(String jobProfileId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/profileSnapshots")
      .addPathSegment(jobProfileId)
      .addQueryParameter("profileType", "JOB_PROFILE")
      .addQueryParameter("jobProfileId", jobProfileId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        LOGGER.error("Failed to fetch profile snapshot: {} - Status: {}", jobProfileId, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        LOGGER.error("Response body is null for profile snapshot: {}", jobProfileId);
        return Optional.empty();
      }
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Failed to fetch profile snapshot: {}", jobProfileId, e);
    }
    return Optional.empty();
  }

  public Optional<JsonNode> createJobProfile(String jobProfile) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/jobProfiles")
      .build();

    return createObjInFolio(url, jobProfile);
  }

  public Optional<JsonNode> createMatchProfile(String matchProfile) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/matchProfiles")
      .build();

    return createObjInFolio(url, matchProfile);
  }

  public Optional<JsonNode> createActionProfile(String actionProfile) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/actionProfiles")
      .build();

    return createObjInFolio(url, actionProfile);
  }

  public Optional<JsonNode> createMappingProfile(String mappingProfile) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/mappingProfiles")
      .build();

    return createObjInFolio(url, mappingProfile);
  }

  public boolean deleteJobProfile(String jobProfileId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/jobProfiles")
      .addPathSegment(jobProfileId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .delete()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to delete job profile: {} - Status: {} - Response: {}",
          jobProfileId, response.code(), errorBody);
        return false;
      }
      return true;
    } catch (IOException e) {
      LOGGER.error("Failed to delete job profile: {}", jobProfileId, e);
      return false;
    }
  }

  public boolean deleteMatchProfile(String matchProfileId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/matchProfiles")
      .addPathSegment(matchProfileId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .delete()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to delete match profile: {} - Status: {} - Response: {}",
          matchProfileId, response.code(), errorBody);
        return false;
      }
      return true;
    } catch (IOException e) {
      LOGGER.error("Failed to delete match profile: {}", matchProfileId, e);
      return false;
    }
  }

  public boolean deleteActionProfile(String actionProfileId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/actionProfiles")
      .addPathSegment(actionProfileId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .delete()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to delete action profile: {} - Status: {} - Response: {}",
          actionProfileId, response.code(), errorBody);
        return false;
      }
      return true;
    } catch (IOException e) {
      LOGGER.error("Failed to delete action profile: {}", actionProfileId, e);
      return false;
    }
  }

  public boolean deleteMappingProfile(String mappingProfileId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import-profiles/mappingProfiles")
      .addPathSegment(mappingProfileId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .delete()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to delete mapping profile: {} - Status: {} - Response: {}",
          mappingProfileId, response.code(), errorBody);
        return false;
      }
      return true;
    } catch (IOException e) {
      LOGGER.error("Failed to delete mapping profile: {}", mappingProfileId, e);
      return false;
    }
  }

  public Optional<JsonNode> createUploadDefinition(String fileName) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import/uploadDefinitions")
      .build();
    String body = """
      {"fileDefinitions":[{"name":%s}]}
      """.formatted(jsonString(fileName));
    return postJson(url, body);
  }

  public Optional<JsonNode> getUploadDefinition(String uploadDefinitionId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import/uploadDefinitions")
      .addPathSegment(uploadDefinitionId)
      .build();
    return getJson(url);
  }

  public Optional<JsonNode> getUploadUrl(String fileName) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import/uploadUrl")
      .addQueryParameter("fileName", fileName)
      .build();
    return getJson(url);
  }

  public Optional<String> uploadFileToStorage(String uploadUrl, Path filePath) {
    RequestBody body = RequestBody.create(filePath.toFile(), MediaType.parse("application/octet-stream"));
    Request request = new Request.Builder()
      .url(uploadUrl)
      .put(body)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to upload file to storage: {} - Status: {} - Response: {}",
          filePath, response.code(), errorBody);
        return Optional.empty();
      }
      String etag = response.header("ETag");
      if (etag == null || etag.isBlank()) {
        LOGGER.error("Storage upload response did not include an ETag for {}", filePath);
        return Optional.empty();
      }
      return Optional.of(etag.replace("\"", ""));
    } catch (IOException e) {
      LOGGER.error("Failed to upload file to storage: {}", filePath, e);
      return Optional.empty();
    }
  }

  public boolean assembleStorageFile(
      String uploadDefinitionId,
      String fileDefinitionId,
      String key,
      String etag,
      String uploadId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import/uploadDefinitions")
      .addPathSegment(uploadDefinitionId)
      .addPathSegment("files")
      .addPathSegment(fileDefinitionId)
      .addPathSegment("assembleStorageFile")
      .build();
    String body = """
      {"key":%s,"tags":[%s],"uploadId":%s}
      """.formatted(jsonString(key), jsonString(etag), jsonString(uploadId));
    return postJson(url, body).isPresent();
  }

  public Optional<JsonNode> processUploadedFiles(
      String uploadDefinitionId,
      JsonNode uploadDefinition,
      String jobProfileId,
      String jobProfileName) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("data-import/uploadDefinitions")
      .addPathSegment(uploadDefinitionId)
      .addPathSegment("processFiles")
      .build();
    String body = """
      {"uploadDefinition":%s,"jobProfileInfo":{"id":%s,"name":%s,"dataType":"MARC"}}
      """.formatted(uploadDefinition.toString(), jsonString(jobProfileId), jsonString(jobProfileName));
    return postJson(url, body);
  }

  public Optional<JsonNode> getJobExecution(String jobExecutionId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("change-manager/jobExecutions")
      .addPathSegment(jobExecutionId)
      .build();
    return getJson(url);
  }

  public Stream<JsonNode> getJobExecutions(int limit) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("metadata-provider/jobExecutions")
      .addQueryParameter("sortBy", "started_date,desc")
      .addQueryParameter("limit", Integer.toString(limit))
      .build();
    Optional<JsonNode> response = getJson(url);
    if (response.isEmpty()) {
      return Stream.empty();
    }
    JsonNode executions = response.get().path("jobExecutions");
    if (!executions.isArray()) {
      return Stream.empty();
    }
    return StreamSupport.stream(executions.spliterator(), false);
  }

  private Optional<JsonNode> getJson(HttpUrl url) {
    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("GET failed: {} - Status: {} - Response: {}", url, response.code(), errorBody);
        return Optional.empty();
      }
      if (response.body() == null) {
        LOGGER.error("Response body is null for GET request to: {}", url);
        return Optional.empty();
      }
      return Optional.of(OBJECT_MAPPER.readTree(response.body().string()));
    } catch (IOException e) {
      LOGGER.error("GET failed: {}", url, e);
      return Optional.empty();
    }
  }

  private Optional<JsonNode> postJson(HttpUrl url, String jsonBody) {
    RequestBody body = RequestBody.create(jsonBody, JSON_MEDIA_TYPE);
    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .post(body)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("POST failed: {} - Status: {} - Response: {}", url, response.code(), errorBody);
        return Optional.empty();
      }
      if (response.body() == null) {
        return Optional.of(OBJECT_MAPPER.createObjectNode());
      }
      String responseBody = response.body().string();
      if (responseBody.isBlank()) {
        return Optional.of(OBJECT_MAPPER.createObjectNode());
      }
      return Optional.of(OBJECT_MAPPER.readTree(responseBody));
    } catch (IOException e) {
      LOGGER.error("POST failed: {}", url, e);
      return Optional.empty();
    }
  }

  private static String jsonString(String value) {
    try {
      return OBJECT_MAPPER.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to serialize JSON string", e);
    }
  }

  /**
   * Generic helper method to create any FOLIO object via POST request.
   * Used internally by createJobProfile, createMatchProfile, createActionProfile, etc.
   *
   * @param url the FOLIO API endpoint URL
   * @param obj the JSON string representation of the object to create
   * @return optional containing the created object's JSON, or empty on failure
   */
  private Optional<JsonNode> createObjInFolio(HttpUrl url, String obj) {
    RequestBody body = RequestBody.create(obj, MediaType.parse("application/json"));

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .post(body)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errorBody = response.body() != null ? response.body().string() : "No response body";
        LOGGER.error("Failed to create object at: {} - Status: {} - Response: {}",
          url, response.code(), errorBody);
        return Optional.empty();
      }
      if (response.body() == null) {
        LOGGER.error("Response body is null for create request to: {}", url);
        return Optional.empty();
      }
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Failed to create object at: {}", url, e);
    }
    return Optional.empty();
  }

  /**
   * Fetches mapping metadata from mod-source-record-manager.
   *
   * @param recordType the record type (e.g., "marc-bib", "marc-holdings", "marc-authority")
   * @return optional JSON response containing mapping metadata
   */
  public Optional<JsonNode> getMappingMetadata(String recordType) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("mapping-metadata/type")
      .addPathSegment(recordType)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        LOGGER.warn("Failed to fetch mapping metadata for record type: {} - Status: {}",
          recordType, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        LOGGER.error("Response body is null for mapping metadata request: {}", recordType);
        return Optional.empty();
      }
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Failed to fetch mapping metadata for record type: {}", recordType, e);
    }
    return Optional.empty();
  }

  /**
   * Fetches raw mapping rules from mod-source-record-manager.
   *
   * @param recordType the record type (e.g., "marc-bib", "marc-holdings", "marc-authority")
   * @return optional JSON response containing mapping rules
   */
  public Optional<JsonNode> getMappingRules(String recordType) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("mapping-rules")
      .addPathSegment(recordType)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        LOGGER.warn("Failed to fetch mapping rules for record type: {} - Status: {}",
          recordType, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        LOGGER.error("Response body is null for mapping rules request: {}", recordType);
        return Optional.empty();
      }
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Failed to fetch mapping rules for record type: {}", recordType, e);
    }
    return Optional.empty();
  }

  /**
   * Finds an instance by its HRID (human-readable identifier).
   * The HRID typically corresponds to the 001 control field in MARC records.
   *
   * @param hrid the instance HRID to search for
   * @return optional JsonNode containing the instance data, or empty if not found
   */
  public Optional<JsonNode> findInstanceByHrid(String hrid) {
    String escapedHrid = escapeCqlString(hrid);
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("instance-storage/instances")
      .addQueryParameter("query", "hrid==\"" + escapedHrid + "\"")
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        LOGGER.warn("Failed to find instance by HRID: {} - Status: {}", hrid, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        return Optional.empty();
      }
      String result = response.body().string();
      JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      JsonNode instances = jsonNode.path("instances");
      if (instances.isArray() && !instances.isEmpty()) {
        return Optional.of(instances.get(0));
      }
      return Optional.empty();
    } catch (IOException e) {
      LOGGER.error("Failed to find instance by HRID: {}", hrid, e);
      return Optional.empty();
    }
  }

  /**
   * Finds an instance by a specific identifier value (e.g., OCLC number, ISBN).
   *
   * @param value the identifier value to search for
   * @param identifierTypeId the UUID of the identifier type (e.g., OCLC, ISBN type ID)
   * @return optional JsonNode containing the instance data, or empty if not found
   */
  public Optional<JsonNode> findInstanceByIdentifier(String value, String identifierTypeId) {
    // CQL query to match identifier value within the identifiers array
    String query = cqlWildcardMatch("identifiers", value);
    if (identifierTypeId != null && !identifierTypeId.isBlank()) {
      query = String.format("(%s and %s)", query, cqlWildcardMatch("identifiers", identifierTypeId));
    }

    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("instance-storage/instances")
      .addQueryParameter("query", query)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        LOGGER.warn("Failed to find instance by identifier: {} - Status: {}", value, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        return Optional.empty();
      }
      String result = response.body().string();
      JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
      JsonNode instances = jsonNode.path("instances");
      if (instances.isArray() && !instances.isEmpty()) {
        return Optional.of(instances.get(0));
      }
      return Optional.empty();
    } catch (IOException e) {
      LOGGER.error("Failed to find instance by identifier: {}", value, e);
      return Optional.empty();
    }
  }

  private static String escapeCqlString(String value) {
    return value
      .replace("\\", "\\\\")
      .replace("\"", "\\\"")
      .replace("*", "\\*")
      .replace("?", "\\?");
  }

  private static String cqlWildcardMatch(String field, String value) {
    return String.format("%s=\"*%s*\"", field, escapeCqlString(value));
  }

  public Optional<String> getReferenceDataIdByName(String referenceType, String name) {
    return new ReferenceDataManager(httpClient, baseUrlBuilderSupplier, token, tenantId, okapiUrl)
      .getIdByName(referenceType, name);
  }

  /**
   * Finds an instance by its UUID.
   *
   * @param instanceId the instance UUID
   * @return optional JsonNode containing the instance data, or empty if not found
   */
  public Optional<JsonNode> findInstanceById(String instanceId) {
    HttpUrl url = baseUrlBuilderSupplier.get()
      .addPathSegments("instance-storage/instances")
      .addPathSegment(instanceId)
      .build();

    Request request = addFolioHeaders(new Request.Builder()
      .url(url))
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        if (response.code() == 404) {
          return Optional.empty();
        }
        LOGGER.warn("Failed to find instance by ID: {} - Status: {}", instanceId, response.code());
        return Optional.empty();
      }
      if (response.body() == null) {
        return Optional.empty();
      }
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Failed to find instance by ID: {}", instanceId, e);
      return Optional.empty();
    }
  }

  /**
   * Finds a source-storage record by MARC 001 control number.
   *
   * <p>This intentionally scans a bounded page of source records because SRS does not expose
   * a simple portable "parsed MARC 001 equals" endpoint through Okapi. It is primarily used
   * by the wrangler enrichment step after importing freshly generated foundation records.
   */
  public Optional<JsonNode> findSourceRecordByMarcControlNumber(String recordType, String controlNumber) {
    final int limit = 1000;
    int offset = 0;
    int totalRecords = Integer.MAX_VALUE;
    JsonNode match001 = null;
    boolean multiple001Matches = false;
    JsonNode fallback035Match = null;
    boolean multiple035Matches = false;

    while (offset < totalRecords) {
      HttpUrl url = baseUrlBuilderSupplier.get()
        .addPathSegments("source-storage/source-records")
        .addQueryParameter("recordType", recordType)
        .addQueryParameter("limit", Integer.toString(limit))
        .addQueryParameter("offset", Integer.toString(offset))
        .build();

      Request request = addFolioHeaders(new Request.Builder()
        .url(url))
        .get()
        .build();

      try (Response response = httpClient.newCall(request).execute()) {
        if (!response.isSuccessful()) {
          LOGGER.warn("Failed to find source record by MARC 001: {} - Status: {}", controlNumber, response.code());
          return Optional.empty();
        }
        if (response.body() == null) {
          return Optional.empty();
        }
        String result = response.body().string();
        JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
        totalRecords = jsonNode.path("totalRecords").asInt(0);
        JsonNode sourceRecords = jsonNode.path("sourceRecords");
        if (!sourceRecords.isArray() || sourceRecords.isEmpty()) {
          return Optional.empty();
        }
        for (JsonNode sourceRecord : sourceRecords) {
          JsonNode parsedContent = sourceRecord.path("parsedRecord").path("content");
          JsonNode parsedRecord = parsedMarcContent(parsedContent);
          if (parsedRecord == null) {
            continue;
          }

          if (marcJsonHas001(parsedRecord, controlNumber)) {
            if (match001 == null) {
              match001 = sourceRecord;
            } else {
              multiple001Matches = true;
            }
          }
          if (marcJsonHas035a(parsedRecord, controlNumber)) {
            if (fallback035Match == null) {
              fallback035Match = sourceRecord;
            } else {
              multiple035Matches = true;
            }
          }
        }
        offset += limit;
      } catch (IOException e) {
        LOGGER.error("Failed to find source record by MARC 001: {}", controlNumber, e);
        return Optional.empty();
      }
    }

    if (multiple001Matches) {
      LOGGER.warn("Found multiple source records with MARC 001 '{}'; refusing ambiguous enrichment", controlNumber);
      return Optional.empty();
    }
    if (multiple035Matches) {
      LOGGER.warn("Found multiple source records with MARC 035$a '{}'; refusing ambiguous enrichment", controlNumber);
      return Optional.empty();
    }
    if (match001 != null && fallback035Match != null && !sameSourceRecord(match001, fallback035Match)) {
      LOGGER.warn("Found conflicting MARC 001 and 035$a source records for '{}'; refusing ambiguous enrichment", controlNumber);
      return Optional.empty();
    }
    if (fallback035Match != null) {
      return Optional.of(fallback035Match);
    }
    if (match001 != null) {
      return Optional.of(match001);
    }
    return Optional.empty();
  }

  private JsonNode parsedMarcContent(JsonNode parsedContent) {
    try {
      if (parsedContent.isObject()) {
        return parsedContent;
      }
      if (parsedContent.isTextual()) {
        return OBJECT_MAPPER.readTree(parsedContent.asText());
      }
    } catch (IOException e) {
      LOGGER.warn("Skipping malformed textual parsed MARC content during source-record lookup: {}", e.getMessage());
    }
    return null;
  }

  private boolean sameSourceRecord(JsonNode first, JsonNode second) {
    JsonNode firstRecordId = first.path("recordId");
    JsonNode secondRecordId = second.path("recordId");
    if (firstRecordId.isTextual() && secondRecordId.isTextual()) {
      return firstRecordId.asText().equals(secondRecordId.asText());
    }
    return first.equals(second);
  }

  private boolean marcJsonHas001(JsonNode content, String controlNumber) {
    JsonNode fields = content.path("fields");
    if (!fields.isArray()) {
      return false;
    }
    for (JsonNode field : fields) {
      JsonNode control001 = field.get("001");
      if (control001 != null && control001.isTextual() && controlNumber.equals(control001.asText())) {
        return true;
      }
    }
    return false;
  }

  private boolean marcJsonHas035a(JsonNode content, String controlNumber) {
    JsonNode fields = content.path("fields");
    if (!fields.isArray()) {
      return false;
    }
    for (JsonNode field : fields) {
      JsonNode field035 = field.get("035");
      JsonNode subfields = field035 == null ? null : field035.path("subfields");
      if (subfields != null && subfields.isArray()) {
        for (JsonNode subfield : subfields) {
          JsonNode subfieldA = subfield.get("a");
          // Exact-only fallback for wrangler-generated former 001 values moved into 035$a by FOLIO.
          // Do not normalize OCLC-style 035 values here; broad identifier matching risks wrong-record enrichment.
          if (subfieldA != null && subfieldA.isTextual() && controlNumber.equals(subfieldA.asText())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static Optional<String> getOkapiToken(OkHttpClient httpClient, HttpUrl.Builder baseUrlBuilder, String tenantId, String username, String password) {
    HttpUrl url = baseUrlBuilder
      .addPathSegments("authn/login-with-expiry")
      .build();
    Request request = new Request.Builder()
      .url(url)
      .addHeader(OKAPI_TENANT_HEADER, tenantId)
      .post(RequestBody.create(OBJECT_MAPPER.createObjectNode()
        .put("username", username)
        .put("password", password).toString(), JSON_MEDIA_TYPE))
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
      List<String> cookies = response.headers("Set-Cookie");
      return cookies
        .stream()
        .map(cookieString -> Cookie.parse(url, cookieString))
        .filter(Objects::nonNull)
        .filter(c -> c.name().equals(ACCESS_TOKEN_COOKIE_NAME))
        .map(Cookie::value)
        .findFirst();
    } catch (IOException e) {
      LOGGER.error("Something happened while getting token", e);
      return Optional.empty();
    }
  }
}
