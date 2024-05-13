package org.folio.graph.nodes;

import java.util.Comparator;
import java.util.Map;

public record MappingProfileNode(String id, String incomingRecordType, String existingRecordType, int order) implements Profile {
  @Override
  public String getName() {
    return "Mapping Profile";
  }

  @Override
  public int getOrder() {
    return order;
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "incomingRecordType", incomingRecordType, "existingRecordType", existingRecordType,
      "order", String.valueOf(order));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Comparator<MappingProfileNode> getComparator() {
    return Comparator.comparing(MappingProfileNode::incomingRecordType)
      .thenComparing(MappingProfileNode::existingRecordType)
      .thenComparing(MappingProfileNode::order);
  }

  public static Profile fromAttributes(String id, Map<String, String> attributes) {
    return new MappingProfileNode(id, attributes.get("incomingRecordType"), attributes.get("existingRecordType"),
      Integer.parseInt(attributes.get("order")));
  }
}
