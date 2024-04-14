package org.folio.graph;

import org.jgrapht.graph.DefaultEdge;

public class NonMatchRelationshipEdge extends DefaultEdge {
  private static final String label = "NON_MATCH";

  public static String getLabel() {
    return label;
  }

  @Override
  public String toString() {
    return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
  }
}
