package org.folio.graph.nodes;

import java.util.Comparator;
import java.util.Map;

public record ActionProfileNode(String id, String action, String folioRecord, int order) implements Profile {
  @Override
  public String getName() {
    return "Action Profile";
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "action", action, "folioRecord", folioRecord,
      "order", String.valueOf(order));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Comparator<ActionProfileNode> getComparator() {
    return Comparator.comparing(ActionProfileNode::action)
      .thenComparing(ActionProfileNode::folioRecord)
      .thenComparing(ActionProfileNode::order);
  }

  public static Profile fromAttributes(String id, Map<String, String> attributes) {
    return new ActionProfileNode(id, attributes.get("action"), attributes.get("folioRecord"),
      Integer.parseInt(attributes.get("order")));
  }
}
