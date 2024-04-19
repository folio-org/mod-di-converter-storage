package org.folio.graph.edges;

import org.jgrapht.graph.DefaultEdge;

public class RegularEdge extends DefaultEdge {
  private String label = "LINKS";

  public RegularEdge() {
  }

  public RegularEdge(String label) {
    this.label = label;
  }

  public String getLabel() {
    return label;
  }

  @Override
  public String toString() {
    return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
  }

  @Override
  public int hashCode() {
    return label.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegularEdge that = (RegularEdge) o;
    if (!label.equals(that.label)) {
      return false;
    } else if (!getSource().equals(that.getSource())) {
      return false;
    } else return getTarget().equals(that.getTarget());
  }
}
