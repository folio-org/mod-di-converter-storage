package org.folio.graph;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.RepoObject;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.folio.graph.GraphWriter.DOT_FILE_PATTERN;

/**
 * The GraphReader class provides methods to read and search graphs from DOT files.
 * It uses the JGraphT library to represent and manipulate the graphs.
 */
public class GraphReader {
  private static final Logger LOGGER = LogManager.getLogger();
  private static final DOTImporter<Profile, RegularEdge> DOT_IMPORTER = new DOTImporter<>();

  static {
    // Configure the DOTImporter to create vertices based on the "name" attribute
    DOT_IMPORTER.setVertexWithAttributesFactory((id, attrs) -> {
      Map<String, String> normalAttrs = normalizeAttributes(attrs);
      String name = normalAttrs.get("name");
      switch (name) {
        case "Job Profile" -> {
          return JobProfileNode.fromAttributes(id, normalAttrs);
        }
        case "Match Profile" -> {
          return MatchProfileNode.fromAttributes(id, normalAttrs);
        }
        case "Action Profile" -> {
          return ActionProfileNode.fromAttributes(id, normalAttrs);
        }
        case "Mapping Profile" -> {
          return MappingProfileNode.fromAttributes(id, normalAttrs);
        }
        default -> {
          LOGGER.error("Unrecognized profile: '{}'", normalAttrs);
          return null;
        }
      }
    });

    // Configure the DOTImporter to create edges based on the "label" attribute
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

  private GraphReader() {}

  /**
   * Reads a graph from a DOT file specified by the repository path and ID.
   *
   * @param repoPath The path to the repository containing the DOT files.
   * @param id       The ID of the graph to read.
   * @return The read graph.
   */
  public static Graph<Profile, RegularEdge> read(String repoPath, int id) {
    Path filePath = Paths.get(repoPath, GraphWriter.genGraphFileName(id));
    Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
    DOT_IMPORTER.importGraph(g, filePath.toFile());
    return g;
  }

  /**
   * Reads all graphs from DOT files in the specified repository path.
   *
   * @param repoPath The path to the repository containing the DOT files.
   * @return A list of all read graphs.
   */
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

  /**
   * Searches for a graph in the specified repository path that matches the given graph.
   *
   * @param repoPath The path to the repository containing the DOT files.
   * @param graph    The graph to search for.
   * @return An Optional containing the found RepoObject, or an empty Optional if not found.
   */
  public static Optional<RepoObject> search(String repoPath,
                                            Graph<Profile, RegularEdge> graph) {
    try (Stream<Path> stream = Files.list(Paths.get(repoPath))) {
      return stream
        .filter(Files::isRegularFile)
        .map(filePath -> {
          Graph<Profile, RegularEdge> g = new SimpleDirectedGraph<>(RegularEdge.class);
          DOT_IMPORTER.importGraph(g, filePath.toFile());
          return Pair.of(filePath, g);
        })
        .filter(pair -> areGraphsEqual(graph, pair.getRight()))
        .findFirst()
        .map(pair -> {
          String fileName = pair.getLeft().getFileName().toString();
          Matcher matcher = DOT_FILE_PATTERN.matcher(fileName);
          if (matcher.matches()) {
            String repoId = matcher.group(1);
            return new RepoObject(Integer.parseInt(repoId), pair.getRight());
          } else {
            LOGGER.error("Invalid format: {}", fileName);
            return null;
          }
        });
    } catch (IOException e) {
      LOGGER.error("An error occurred while reading the directory.", e);
    }
    return Optional.empty();
  }

  /**
   * Normalizes the attributes by converting them to a Map<String, String>.
   *
   * @param attrs The attributes to normalize.
   * @return The normalized attributes as a Map<String, String>.
   */
  private static Map<String, String> normalizeAttributes(Map<String, Attribute> attrs) {
    return attrs
      .entrySet()
      .stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        entry -> entry.getValue().toString()
      ));
  }

  /**
   * Compares two graphs for equality using a custom comparator.
   *
   * @param graph1 The first graph to compare.
   * @param graph2 The second graph to compare.
   * @return true if the graphs are equal, false otherwise.
   */
  public static boolean areGraphsEqual(Graph<Profile, RegularEdge> graph1, Graph<Profile, RegularEdge> graph2) {
    // Check if the graphs have the same number of vertices and edges
    if (graph1.vertexSet().size() != graph2.vertexSet().size() ||
      graph1.edgeSet().size() != graph2.edgeSet().size()) {
      return false;
    }

    // Compare each node in the graphs using the custom comparator
    boolean areNodesEqual = graph1.vertexSet().stream()
      .allMatch(node1 -> graph2.vertexSet().stream()
        .anyMatch(node2 -> node1.getClass().equals(node2.getClass())
          && node1.getComparator().compare(node1, node2) == 0));

    if (!areNodesEqual) {
      return false;
    }

    // Compare the edges in the graphs

    return graph1.edgeSet().stream()
      .allMatch(edge1 -> {
        Profile source1 = graph1.getEdgeSource(edge1);
        Profile target1 = graph1.getEdgeTarget(edge1);

        return graph2.edgeSet().stream()
          .anyMatch(edge2 -> {
            Profile source2 = graph2.getEdgeSource(edge2);
            Profile target2 = graph2.getEdgeTarget(edge2);

            return source1.getClass().equals(source2.getClass())
              && target1.getClass().equals(target2.getClass())
              && source1.getComparator().compare(source1, source2) == 0
              && target1.getComparator().compare(target1, target2) == 0;
          });
      });
  }
}
