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
import java.util.PriorityQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GraphWriter {
  private final static Logger LOGGER = LogManager.getLogger();
  private final static DOTExporter<Profile, RegularEdge> DOT_EXPORTER = new DOTExporter<>();
  private final static PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());

  public final static Pattern DOT_FILE_PATTERN = Pattern.compile("jp-(\\d+)\\.dot");

  static {
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
    DOT_EXPORTER.setEdgeAttributeProvider((e) -> {
      Map<String, Attribute> map = new LinkedHashMap<>();
      map.put("label", DefaultAttribute.createAttribute(e.getLabel()));
      return map;
    });
  }

  public static synchronized void writeGraph(String repoPath, Graph<Profile, RegularEdge> graph) {
    try {

      if (maxHeap.isEmpty()) {
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
                LOGGER.error("Invalid format: " + fileName);
                return null;
              }
            }).filter(Objects::nonNull)
            .forEach(id -> maxHeap.add(Integer.parseInt(id)));
        } catch (IOException e) {
          LOGGER.error("An error occurred while reading the directory.", e);
        }
      }

      Integer newId = maxHeap.peek() != null ? maxHeap.peek() + 1 : 1;
      Path filePath = Paths.get(repoPath, "jp-" + newId + ".dot");
      try (FileWriter writer = new FileWriter(filePath.toFile())) {
        DOT_EXPORTER.exportGraph(graph, writer);
      }
      maxHeap.add(newId);
    } catch (IOException e) {
      LOGGER.error("An error occurred while writing to the file.", e);
    }
  }

  public static void renderGraph(String fileName, Graph<Profile, RegularEdge> graph) {
    Writer writer = new StringWriter();
    DOT_EXPORTER.exportGraph(graph, writer);
    try {
      Graphviz.fromString(writer.toString())
        .render(Format.SVG)
        .toFile(new File(fileName + ".svg"));
    } catch (IOException e) {
      LOGGER.error("Error during rendering of graph", e);
    }
  }

  private static String createTableLabel(Map<String, String> dataMap) {
    StringBuilder labelBuilder = new StringBuilder();
    labelBuilder.append("<table BORDER=\"0\" CELLBORDER=\"1\" CELLPADDING=\"6\">");
    dataMap.entrySet().stream()
      .filter(e -> e.getKey().equals("name"))
      .forEach(e -> labelBuilder.append("<tr><td COLSPAN=\"2\"><b>")
        .append(e.getValue().toUpperCase())
        .append("</b></td></tr>"));
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
