package org.folio.http;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.HttpUrl;
import org.junit.Test;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class FolioClientTest {

  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";

  @Test
  public void getJobProfiles() {
    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");
    FolioClient client = new FolioClient(urlTemplate, TENANT_ID, USERNAME, PASSWORD);

    Stream<JsonNode> jobProfiles = client
      .getJobProfiles(null);
    assertTrue(jobProfiles.findAny().isPresent());
  }
}
