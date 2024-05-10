package org.folio;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.MediaType;

public class Constants {
  public static final String OKAPI_TENANT_HEADER = "x-okapi-tenant";
  public static final String OKAPI_TOKEN_HEADER = "x-okapi-token";
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static final String ACCESS_TOKEN_COOKIE_NAME = "folioAccessToken";

  public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

  private Constants() {}
}
