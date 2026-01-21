package org.folio.graph.edges;

import org.jgrapht.graph.DefaultEdge;

public class RegularEdge extends DefaultEdge {
  private String label = "LINKS";

  public RegularEdge() {
  }

  public RegularEdge(String label) {
    this.label = label;
  }

  /**
   * Creates a new RegularEdge. Note: the source and target parameters are not stored;
   * JGraphT sets them internally when the edge is added to a graph via graph.addEdge().
   * This constructor exists to support graph.addEdge(source, target, new RegularEdge(source, target)).
   *
   * @param source the source vertex (used by JGraphT, not stored directly)
   * @param target the target vertex (used by JGraphT, not stored directly)
   */
  public RegularEdge(Object source, Object target) {
    this.label = "LINKS";
  }

  public String getLabel() {
    return label;
  }

  public Object getSource() {
    return super.getSource();
  }

  public Object getTarget() {
    return super.getTarget();
  }

  @Override
  public String toString() {
    return "(" + getSource() + " : " + getTarget() + " : " + label + ")";
  }

  @Override
  public int hashCode() {
    return java.util.Objects.hash(getSource(), getTarget(), label);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RegularEdge that = (RegularEdge) o;
    if (!java.util.Objects.equals(label, that.label)) {
      return false;
    } else if (!java.util.Objects.equals(getSource(), that.getSource())) {
      return false;
    } else return java.util.Objects.equals(getTarget(), that.getTarget());
  }
}
