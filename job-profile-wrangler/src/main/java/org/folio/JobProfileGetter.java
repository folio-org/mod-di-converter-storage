package org.folio;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;

public class JobProfileGetter extends AbstractObjectGetter {
  public JobProfileGetter(OkHttpClient httpClient, Supplier<HttpUrl.Builder> baseTemplateSupplier, String token,
                           BlockingQueue<JsonNode> blockingQueue) {
    super(httpClient, baseTemplateSupplier, token, blockingQueue);
  }

  @Override
  public String getUrlPath() {
    return "data-import-profiles/jobProfiles";
  }

}
