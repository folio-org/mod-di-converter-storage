package org.folio.imports;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.ActionProfileNode;
import org.folio.graph.JobProfileNode;
import org.folio.graph.MappingProfileNode;
import org.folio.graph.MatchProfileNode;
import org.folio.graph.MatchRelationshipEdge;
import org.folio.graph.NonMatchRelationshipEdge;
import org.folio.graph.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.dot.DOTImporter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphReader {
  private final static Logger LOGGER = LogManager.getLogger();
  private final static DOTImporter<Profile, DefaultEdge> DOT_IMPORTER = new DOTImporter<>();

  static {
    DOT_IMPORTER.setVertexWithAttributesFactory((id, attrs) -> {
      Map<String, String> normalAttrs = normalizeAttributes(attrs);
      String name = normalAttrs.get("name");
      switch (name) {
        case "Job Profile" -> {
          return JobProfileNode.fromAttributes(normalAttrs);
        }
        case "Match Profile" -> {
          return MatchProfileNode.fromAttributes(normalAttrs);
        }
        case "Action Profile" -> {
          return ActionProfileNode.fromAttributes(normalAttrs);
        }
        case "Mapping Profile" -> {
          return MappingProfileNode.fromAttributes(normalAttrs);
        }
        default -> {
          LOGGER.error("Unrecognized profile: '{}'", normalAttrs);
          return null;
        }
      }
    });

    DOT_IMPORTER.setEdgeWithAttributesFactory((attrs -> attrs.entrySet()
      .stream()
      .filter(entry -> entry.getKey().equals("label"))
      .findFirst()
      .map(entry -> {
        Attribute attr = entry.getValue();
        if (MatchRelationshipEdge.getLabel().equals(attr.getValue())) {
          return new MatchRelationshipEdge();
        } else if (NonMatchRelationshipEdge.getLabel().equals(attr.getValue())) {
          return new NonMatchRelationshipEdge();
        }
        return null;
      })
      .orElse(new DefaultEdge())
    ));
  }

  public static Graph<Profile, DefaultEdge> read(String repoPath, int id) {
    Path filePath = Paths.get(repoPath, "jp-" + id + ".dot");
    Graph<Profile, DefaultEdge> g = new SimpleDirectedGraph<>(DefaultEdge.class);
    DOT_IMPORTER.importGraph(g, filePath.toFile());
    return g;
  }

  private static Map<String, String> normalizeAttributes(Map<String, Attribute> attrs) {
    return attrs
      .entrySet()
      .stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().toString()
      ));
  }
}
