package org.folio.graph.edges;

public class NonMatchRelationshipEdge extends RegularEdge {
  public NonMatchRelationshipEdge() {
    super(getLabelValue());
  }

  public static String getLabelValue() {
    return "NON_MATCH";
  }
}
