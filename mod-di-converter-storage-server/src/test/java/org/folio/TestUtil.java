package org.folio;

import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

public class TestUtil {
  public static String readFileFromPath(String path) throws IOException {
    return new String(FileUtils.readFileToByteArray(new File(path)));
  }

  public static JsonObject readJson(String filePath) throws IOException {
    return new JsonObject(TestUtil.readFileFromPath(filePath));
  }
}
