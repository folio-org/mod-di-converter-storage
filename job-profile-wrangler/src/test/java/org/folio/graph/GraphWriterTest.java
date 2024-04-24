package org.folio.graph;

import com.google.common.io.Resources;
import org.folio.imports.RepoImport;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.folio.Constants.REPO_PATH;
import static org.junit.Assert.assertTrue;

public class GraphWriterTest {


  @Test
  public void renderGraph() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    var repoObject = RepoImport.fromString(REPO_PATH, content);
    assertTrue(repoObject.isPresent());
    GraphWriter.renderGraph("output", repoObject.get().graph());
  }
}
