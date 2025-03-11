package org.folio.graph;

import guru.nidi.graphviz.engine.Format;
import guru.nidi.graphviz.engine.Graphviz;
import guru.nidi.graphviz.engine.GraphvizCmdLineEngine;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Optional;

/**
 * Enhanced rendering method for the GraphWriter class that uses the command-line
 * GraphViz engine directly to avoid JavaScript engine issues.
 */
public class GraphWriterEnhanced {
  private static final Logger LOGGER = LogManager.getLogger(GraphWriterEnhanced.class);

  private GraphWriterEnhanced() {}

  /**
   * Renders a graph to SVG using the GraphViz command-line tool.
   *
   * @param fileName The base name for the output file (without extension)
   * @param graph The graph to render
   * @return Optional containing the rendered file, or empty if rendering failed
   */
  public static Optional<File> renderGraphQuietly(String fileName, Graph<Profile, RegularEdge> graph) {
    // Disable GraalVM warnings
    System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
    System.setProperty("guru.nidi.graphviz.engine.GraphvizEngine", "guru.nidi.graphviz.engine.GraphvizCmdLineEngine");

    // First check if the dot command is available
    if (!isGraphvizInstalled()) {
      LOGGER.warn("GraphViz dot command not found. Please install GraphViz to render graphs.");
      // Fall back to writing DOT file only
      return writeDotFile(fileName, graph);
    }

    try {
      // Export graph to DOT format
      Writer writer = new StringWriter();
      GraphWriter.DOT_EXPORTER.exportGraph(graph, writer);
      String dotSource = writer.toString();

      // Create the output file path
      File outputFile = new File(fileName + ".svg");

      // Force use of command line engine
      Graphviz.useEngine(new GraphvizCmdLineEngine());

      // Render the graph
      Graphviz.fromString(dotSource)
        .render(Format.SVG)
        .toFile(outputFile);

      LOGGER.info("Graph rendered to {}", outputFile.getAbsolutePath());
      return Optional.of(outputFile);
    } catch (Exception e) {
      LOGGER.error("Error during rendering of graph: {}", e.getMessage());
      // Fall back to writing DOT file
      return writeDotFile(fileName, graph);
    }
  }

  /**
   * Checks if the GraphViz dot command is available on the system.
   *
   * @return true if GraphViz is installed and the dot command is available
   */
  private static boolean isGraphvizInstalled() {
    try {
      Process process = new ProcessBuilder("dot", "-V").start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      LOGGER.debug("GraphViz dot command check failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Fallback method to write the graph as a DOT file when rendering fails.
   *
   * @param fileName The base name for the output file
   * @param graph The graph to write
   * @return Optional containing the DOT file, or empty if writing failed
   */
  private static Optional<File> writeDotFile(String fileName, Graph<Profile, RegularEdge> graph) {
    try {
      File dotFile = new File(fileName + ".dot");
      try (FileWriter writer = new FileWriter(dotFile)) {
        GraphWriter.DOT_EXPORTER.exportGraph(graph, writer);
      }
      LOGGER.info("Graph written as DOT file to {}", dotFile.getAbsolutePath());
      return Optional.of(dotFile);
    } catch (IOException e) {
      LOGGER.error("Error writing DOT file: {}", e.getMessage());
      return Optional.empty();
    }
  }
}
