package org.folio;

import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static org.folio.Constants.ACCESS_TOKEN_COOKIE_NAME;
import static org.folio.Constants.JSON_MEDIA_TYPE;
import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TENANT_HEADER;

public class Utilities {

  public static Optional<String> getOkapiToken(OkHttpClient httpClient,
                                               Supplier<HttpUrl.Builder> baseTemplate, String tenantId, String username, String password){
    HttpUrl url = baseTemplate.get()
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
      throw new RuntimeException(e);
    }
  }
}
