package org.folio.graph.nodes;

import java.util.Map;

public record JobProfileNode(String dataType) implements Profile {
  @Override
  public String getName() {
    return "Job Profile";
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "dataType", dataType);
  }

  public static Profile fromAttributes(Map<String, String> attributes) {
    return new JobProfileNode(attributes.get("dataType"));
  }
}
