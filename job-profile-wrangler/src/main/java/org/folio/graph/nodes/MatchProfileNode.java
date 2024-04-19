package org.folio.graph.nodes;

import java.util.Map;

public record MatchProfileNode(String incomingRecordType, String existingRecordType) implements Profile {
  @Override
  public String getName() {
    return "Match Profile";
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "incomingRecordType", incomingRecordType, "existingRecordType", existingRecordType);
  }

  public static Profile fromAttributes(Map<String, String> attributes) {
    return new MatchProfileNode(attributes.get("incomingRecordType"), attributes.get("existingRecordType"));
  }
}
