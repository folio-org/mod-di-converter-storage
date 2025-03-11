package org.folio.exports;

import com.google.common.io.Resources;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

import static org.folio.Constants.OBJECT_MAPPER;
import static org.folio.http.FolioClient.getOkapiToken;

public class MarcTestDataGeneratorTest {

  static final String TENANT_ID = "diku";
  static final String USERNAME = "diku_admin";
  static final String PASSWORD = "admin";
  OkHttpClient httpClient = new OkHttpClient();

  @Test
  public void test() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
//    MarcTestDataGenerator marcTestDataGenerator = new MarcTestDataGenerator("/Users/okolawole/Documents/marc_files/YBP Cat Firm.mrc");

    Supplier<HttpUrl.Builder> urlTemplate = () -> new HttpUrl.Builder()
      .scheme("https")
      .host("folio-snapshot-okapi.dev.folio.org");
    Optional<String> okapiToken = getOkapiToken(httpClient, urlTemplate.get(), TENANT_ID, USERNAME, PASSWORD);
    MarcTestDataGenerator marcTestDataGenerator = new MarcTestDataGenerator("https://folio-snapshot-okapi.dev.folio.org",
      okapiToken.orElseThrow(() -> new IllegalStateException("Could not get okapi token")));
    marcTestDataGenerator.generateTestData(OBJECT_MAPPER.readTree(content), "test.mrc");
  }

}
