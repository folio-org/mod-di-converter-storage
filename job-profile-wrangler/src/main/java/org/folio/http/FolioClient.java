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

public class FolioClient {
  private static final Logger LOGGER = LogManager.getLogger();
  OkHttpClient httpClient = new OkHttpClient();

  private final String token;
  private final Supplier<HttpUrl.Builder> baseUrlBuilderSupplier;

  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String token) {
    this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;
    this.token = token;
  }

  public FolioClient(Supplier<HttpUrl.Builder> baseUrlBuilderSupplier, String tenantId, String username, String password) {
    this.baseUrlBuilderSupplier = baseUrlBuilderSupplier;

    Optional<String> okapiToken = getOkapiToken(baseUrlBuilderSupplier.get(), tenantId, username, password);
    if (okapiToken.isEmpty()) {
      throw new IllegalStateException("Could not get okapi token");
    }
    this.token = okapiToken.get();
  }

  protected void setHttpClient(OkHttpClient client) {
    httpClient = client;
  }

  public Stream<JsonNode> getJobProfiles() {
    return getJobProfiles(null);
  }

  public Stream<JsonNode> getJobProfiles(Map<String, String> queryParams) {
    final int queryParamLimit = 3000;
    final AtomicInteger queryParamOffset = new AtomicInteger(0);
    final AtomicInteger totalRecords = new AtomicInteger(0);

    return Stream.generate(() -> {

        if (queryParamOffset.get() > totalRecords.get()) {
          return null;
        }

        HttpUrl.Builder intermediateUrlBuilder = baseUrlBuilderSupplier.get()
          .addPathSegments("data-import-profiles/jobProfiles")
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
        Request request = new Request.Builder()
          .url(url)
          .addHeader(OKAPI_TOKEN_HEADER, token)
          .get()
          .build();

        try (Response response = httpClient.newCall(request).execute()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          assert response.body() != null;
          String result = response.body().string();
          JsonNode jsonNode = OBJECT_MAPPER.readTree(result);

          if (queryParamOffset.get() == 0) {
            totalRecords.set(jsonNode.get("totalRecords").asInt());
            if (totalRecords.get() == 0) {
              return null;
            }
          }

          queryParamOffset.getAndAdd(queryParamLimit);
          return StreamSupport.stream(jsonNode.get("jobProfiles").spliterator(), false);
        } catch (IOException e) {
          LOGGER.error(e);
          return null;
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

    Request request = new Request.Builder()
      .url(url)
      .addHeader(OKAPI_TOKEN_HEADER, token)
      .get()
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      assert response.body() != null;
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Something happened while querying for profile snapshot", e);
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

  private Optional<JsonNode> createObjInFolio(HttpUrl url, String obj) {
    RequestBody body = RequestBody.create(obj, MediaType.parse("application/json"));

    Request request = new Request.Builder()
      .url(url)
      .addHeader(OKAPI_TOKEN_HEADER, token)
      .post(body)
      .build();

    try (Response response = httpClient.newCall(request).execute()) {
      assert response.body() != null;
      String result = response.body().string();
      return Optional.of(OBJECT_MAPPER.readTree(result));
    } catch (IOException e) {
      LOGGER.error("Something happened while querying for profile snapshot", e);
    }
    return Optional.empty();
  }

  protected Optional<String> getOkapiToken(HttpUrl.Builder baseUrlBuilder, String tenantId, String username, String password) {
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
