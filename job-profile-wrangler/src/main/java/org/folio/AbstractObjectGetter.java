package org.folio;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.Constants.OKAPI_TOKEN_HEADER;

public abstract class AbstractObjectGetter implements Runnable {
  private final Logger LOGGER = LogManager.getLogger();
  private final OkHttpClient httpClient;
  private final Supplier<HttpUrl.Builder> baseTemplateSupplier;
  private final String token;
  private final BlockingQueue<JsonNode> blockingQueue;

  public AbstractObjectGetter(OkHttpClient httpClient, Supplier<HttpUrl.Builder> baseTemplateSupplier, String token,
                              BlockingQueue<JsonNode> blockingQueue) {
    this.httpClient = httpClient;
    this.baseTemplateSupplier = baseTemplateSupplier;
    this.token = token;
    this.blockingQueue = blockingQueue;
  }

  public abstract String getUrlPath();

  public Map<String, String> getQueryParams() {
    return Collections.EMPTY_MAP;
  }

  public boolean shouldLogResults() {
    return false;
  }

  @Override
  public void run() {
    int queryParamlimit = 3000;
    int queryParamOffset = 0;
    int totalRecords = 0;

    Path path = Paths.get("/Users/okolawole/acq_results.log");

    try {
      // flag to allow first loop to get counts, exact count is returned when limit = 0 otherwise totalRecords is
      // an estimate
      boolean getCount = true;
      do {
        HttpUrl.Builder intermediateUrlBuilder = baseTemplateSupplier.get()
          .addPathSegments(getUrlPath())
          .addQueryParameter("limit", getCount ? "0" : Integer.toString(queryParamlimit))
          .addQueryParameter("offset", Integer.toString(queryParamOffset));
        // TODO: refactor this somehow, you are not building query param on every loop
        List<String> queryQueryParamList = new ArrayList<>();
        for (var entry : getQueryParams().entrySet()) {
          if (!entry.getKey().equals("query")) {
            intermediateUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
          } else {
            queryQueryParamList.add(entry.getValue());
          }
        }
        queryQueryParamList.add("cql.allRecords=1 sortBy id");
        String query = String.join(" and ", queryQueryParamList);
        intermediateUrlBuilder.addQueryParameter("query", query);
        HttpUrl url = intermediateUrlBuilder
          .build();
        LOGGER.info("Query: " + url);
        Request request = new Request.Builder()
          .url(url)
          .addHeader(OKAPI_TOKEN_HEADER, token)
          .get()
          .build();

        try (Response response = httpClient.newCall(request).execute()) {
          if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

          String result = response.body().string();
          if (shouldLogResults()) {
            Files.writeString(path, result, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(path, "\r\n", StandardOpenOption.APPEND);
          }
          JsonNode jsonNode = OBJECT_MAPPER.readTree(result);
          if (getCount) {
            totalRecords = jsonNode.get("totalRecords").asInt();
            getCount = false;
            if (totalRecords == 0) {
              // no records were returned
              return;
            }
          } else {
            queryParamOffset = queryParamOffset + queryParamlimit - 1;
            blockingQueue.put(jsonNode);
          }
        }
      } while (queryParamOffset < totalRecords);

    } catch (Exception e) {
      LOGGER.error(e);
    }
  }
}
