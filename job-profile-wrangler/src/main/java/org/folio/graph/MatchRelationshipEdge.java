package org.folio.graph;

import org.jgrapht.graph.DefaultEdge;

public class MatchRelationshipEdge extends DefaultEdge {
  private static final String label = "MATCH";

  public static String getLabel() {
    return label;
  }

  @Override
  public String toString() {
    return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
  }
}
