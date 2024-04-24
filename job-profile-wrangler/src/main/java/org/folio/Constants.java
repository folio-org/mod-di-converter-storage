package org.folio;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import okhttp3.MediaType;

import java.net.URL;

public class Constants {
  public static final String OKAPI_TENANT_HEADER = "x-okapi-tenant";
  public static final String OKAPI_TOKEN_HEADER = "x-okapi-token";
  public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  public static final String ACCESS_TOKEN_COOKIE_NAME = "folioAccessToken";

  public static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

  public static final String REPO_PATH;
  static {
    try {
      // Get the URL of the resources folder
      URL resourceUrl = Resources.getResource("repository");

      // Get the absolute path from the URL
      REPO_PATH = resourceUrl.getPath();
    } catch (IllegalArgumentException e) {
      // Handle the case when the resources folder is not found
      throw new RuntimeException("Resources folder not found.", e);
    }
  }
}
