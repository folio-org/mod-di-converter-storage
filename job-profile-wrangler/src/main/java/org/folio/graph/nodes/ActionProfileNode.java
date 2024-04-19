package org.folio.graph.nodes;

import java.util.Map;

public record ActionProfileNode(String action, String folioRecord) implements Profile {
  @Override
  public String getName() {
    return "Action Profile";
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "action", action, "folioRecord", folioRecord);
  }

  public static Profile fromAttributes(Map<String, String> attributes) {
    return new ActionProfileNode(attributes.get("action"), attributes.get("folioRecord"));
  }
}
