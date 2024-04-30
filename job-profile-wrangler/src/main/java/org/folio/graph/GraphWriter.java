package org.folio.graph;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.AttributeType;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The GraphWriter class provides methods for writing and rendering graphs using the DOT format.
 * It uses the JGraphT library for graph operations and the Graphviz library for rendering graphs.
 */
public class GraphWriter {
  private final static Logger LOGGER = LogManager.getLogger();
  private final static DOTExporter<Profile, RegularEdge> DOT_EXPORTER = new DOTExporter<>();
  private final static PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

  public final static Pattern DOT_FILE_PATTERN = Pattern.compile("jp-(\\d+)\\.dot");

  static {
    // Set the vertex attribute provider for the DOT exporter
    DOT_EXPORTER.setVertexAttributeProvider((v) -> {
      Map<String, Attribute> graphvizMap = v.getAttributes()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(
          Map.Entry::getKey,
          entry -> DefaultAttribute.createAttribute(entry.getValue())
        ));
      Map<String, Attribute> map = new LinkedHashMap<>(graphvizMap);
      map.put("label", new DefaultAttribute<>(createTableLabel(v.getAttributes()), AttributeType.HTML));
      map.put("shape", DefaultAttribute.createAttribute("plain"));
      return map;
    });

    // Set the edge attribute provider for the DOT exporter
    DOT_EXPORTER.setEdgeAttributeProvider((e) -> {
      Map<String, Attribute> map = new LinkedHashMap<>();
      map.put("label", DefaultAttribute.createAttribute(e.getLabel()));
      return map;
    });
  }

  private GraphWriter() {
  }

  /**
   * Writes the graph to a DOT file in the specified repository path.
   * The file name is generated automatically based on the existing files in the repository.
   *
   * @param repoPath the repository path where the DOT file will be saved
   * @param graph    the graph to be written
   * @return an Optional containing the ID of the generated file, or an empty Optional if an error occurred
   */
  public static synchronized Optional<Integer> writeGraph(String repoPath, Graph<Profile, RegularEdge> graph) {
    try {
      if (maxHeap.isEmpty()) {
        // If the maxHeap is empty, read the existing DOT files in the repository and populate the maxHeap
        try (Stream<Path> stream = Files.list(Paths.get(repoPath))) {
          List<String> fileNames = stream
            .filter(Files::isRegularFile)
            .map(Path::getFileName)
            .map(Path::toString)
            .toList();

          fileNames.stream()
            .map(fileName -> {
              Matcher matcher = DOT_FILE_PATTERN.matcher(fileName);
              if (matcher.matches()) {
                return matcher.group(1);
              } else {
                LOGGER.error("Invalid format: {}", fileName);
                return null;
              }
            }).filter(Objects::nonNull)
            .forEach(id -> maxHeap.add(Integer.parseInt(id)));
        } catch (IOException e) {
          LOGGER.error("An error occurred while reading the directory.", e);
        }
      }

      // Generate a new ID for the DOT file
      Integer newId = maxHeap.peek() != null ? maxHeap.peek() + 1 : 1;
      Path filePath = Paths.get(repoPath, genGraphFileName(newId));

      // Write the graph to the DOT file
      try (FileWriter writer = new FileWriter(filePath.toFile())) {
        DOT_EXPORTER.exportGraph(graph, writer);
      }

      maxHeap.add(newId);
      return Optional.of(newId);
    } catch (IOException e) {
      LOGGER.error("An error occurred while writing to the file.", e);
      return Optional.empty();
    }
  }

  /**
   * Generate a file name for a repo identifier
   */
  public static String genGraphFileName(Integer repoId) {
    return String.format("jp-%03d", repoId) + ".dot";
  }

  /**
   * Renders the graph to an SVG file with the specified file name.
   *
   * @param fileName the name of the output SVG file (without the extension)
   * @param graph    the graph to be rendered
   */
  public static Optional<File> renderGraph(String fileName, Graph<Profile, RegularEdge> graph) {
    Writer writer = new StringWriter();

    // Export the graph to the DOT format
    DOT_EXPORTER.exportGraph(graph, writer);

    try {
      // Render the graph to an SVG file using Graphviz
      return Optional.of(Graphviz.fromString(writer.toString())
        .render(Format.SVG)
        .toFile(new File(fileName + ".svg")));
    } catch (IOException e) {
      LOGGER.error("Error during rendering of graph", e);
    }
    return Optional.empty();
  }

  /**
   * Creates an HTML table label for a vertex based on its attributes.
   *
   * @param dataMap the map of attribute key-value pairs
   * @return the HTML table label string
   */
  private static String createTableLabel(Map<String, String> dataMap) {
    StringBuilder labelBuilder = new StringBuilder();
    labelBuilder.append("<table BORDER=\"0\" CELLBORDER=\"1\" CELLPADDING=\"6\">");

    // Add the "name" attribute as a header row
    dataMap.entrySet().stream()
      .filter(e -> e.getKey().equals("name"))
      .forEach(e -> labelBuilder.append("<tr><td COLSPAN=\"2\"><b>")
        .append(e.getValue().toUpperCase())
        .append("</b></td></tr>"));

    // Add the remaining attributes as table rows
    for (Map.Entry<String, String> entry : dataMap.entrySet()) {
      if (entry.getKey().equals("name")) {
        continue;
      }
      labelBuilder.append("<tr><td>")
        .append(entry.getKey())
        .append("</td><td>")
        .append(entry.getValue())
        .append("</td></tr>");
    }

    labelBuilder.append("</table>");
    return labelBuilder.toString();
  }
}
