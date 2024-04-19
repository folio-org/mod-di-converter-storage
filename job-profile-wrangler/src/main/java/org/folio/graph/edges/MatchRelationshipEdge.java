package org.folio.graph.edges;

public class MatchRelationshipEdge extends RegularEdge {
  public MatchRelationshipEdge() {
    super(getLabelValue());
  }

  public static String getLabelValue() {
    return "MATCH";
  }
}
