package org.folio.graph;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.edges.MatchRelationshipEdge;
import org.folio.graph.edges.NonMatchRelationshipEdge;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.ActionProfileNode;
import org.folio.graph.nodes.JobProfileNode;
import org.folio.graph.nodes.MappingProfileNode;
import org.folio.graph.nodes.MatchProfileNode;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.dot.DOTImporter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphReader {
  private final static Logger LOGGER = LogManager.getLogger();
  private final static DOTImporter<Profile, RegularEdge> DOT_IMPORTER = new DOTImporter<>();

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
        if (MatchRelationshipEdge.getLabelValue().equals(attr.getValue())) {
          return new MatchRelationshipEdge();
        } else if (NonMatchRelationshipEdge.getLabelValue().equals(attr.getValue())) {
          return new NonMatchRelationshipEdge();
        }
        return null;
      })
      .orElse(new RegularEdge())
    ));
  }

  public static Graph<Profile, RegularEdge> read(String repoPath, int id) {
    Path filePath = Paths.get(repoPath, "jp-" + id + ".dot");
    Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
    DOT_IMPORTER.importGraph(g, filePath.toFile());
    return g;
  }

  public static List<Graph<Profile, RegularEdge>> readAll(String repoPath) {
    List<Graph<Profile, RegularEdge>> graphs = new ArrayList<>();
    try (Stream<Path> stream = Files.list(Paths.get(repoPath))) {
      stream
        .filter(Files::isRegularFile)
        .forEach(filePath -> {
          Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
          DOT_IMPORTER.importGraph(g, filePath.toFile());
          graphs.add(g);
        });
    } catch (IOException e) {
      LOGGER.error("An error occurred while reading the directory.", e);
    }

    return graphs;
  }

  public static boolean isGraphPresent(String repoPath,
                                       Graph<Profile, RegularEdge> graph) {
    try (Stream<Path> stream = Files.list(Paths.get(repoPath))) {
      return stream
        .filter(Files::isRegularFile)
        .anyMatch(filePath -> {
          Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
          DOT_IMPORTER.importGraph(g, filePath.toFile());
          return graph.equals(g);
        });
    } catch (IOException e) {
      LOGGER.error("An error occurred while reading the directory.", e);
    }
    return false;
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
