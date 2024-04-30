package org.folio.graph;

import com.google.common.io.Resources;
import org.folio.RepoObject;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.folio.imports.RepoImport;
import org.jgrapht.Graph;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.folio.Constants.REPO_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class GraphReaderTest {

  private static Integer repoId;

  @BeforeClass
  public static void setup() throws IOException {
    String content = Resources.toString(Resources.getResource("job_profile_snapshot.json"), StandardCharsets.UTF_8);
    Optional<RepoObject> repoObject = RepoImport.fromString(REPO_PATH, content);
    if (repoObject.isEmpty()) throw new RuntimeException("Could not create object in repo");
    repoId = repoObject.get().repoId();
  }

  @Test
  public void readGraph() {
    Graph<Profile, RegularEdge> g1 = GraphReader.read(REPO_PATH, repoId);
    Graph<Profile, RegularEdge> g2 = GraphReader.read(REPO_PATH, repoId);
    assertEquals(g1, g2);
    assertNotNull(g1);
  }

  @Test
  public void readAllGraph() {
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(REPO_PATH);
    assertNotNull(graphs);
    assertFalse(graphs.isEmpty());
  }

  @Test
  public void search() {
    List<Graph<Profile, RegularEdge>> graphs = GraphReader.readAll(REPO_PATH);
    assertNotNull(graphs);
    assertNotEquals(graphs.size(), 0);
    assertTrue(GraphReader.search(REPO_PATH, graphs.get(0)).isPresent());
  }

}
