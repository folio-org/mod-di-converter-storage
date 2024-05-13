package org.folio.graph.nodes;

import java.util.Comparator;
import java.util.Map;

public record JobProfileNode(String id, String dataType, int order) implements Profile {
  @Override
  public String getName() {
    return "Job Profile";
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "dataType", dataType, "order", String.valueOf(order));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Comparator<JobProfileNode> getComparator() {
    return Comparator.comparing(JobProfileNode::dataType)
      .thenComparing(JobProfileNode::order);
  }

  public static Profile fromAttributes(String id, Map<String, String> attributes) {
    return new JobProfileNode(id, attributes.get("dataType"), Integer.parseInt(attributes.get("order")));
  }
}
