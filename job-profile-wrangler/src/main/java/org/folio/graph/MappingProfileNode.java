package org.folio.graph;

import java.util.Map;

public record MappingProfileNode(String id, String incomingRecordType, String existingRecordType) implements Profile {
  @Override
  public String getName() {
    return "Mapping Profile";
  }

  @Override
  public Map<String, String> getAttributes() {
    return Map.of("name", getName(), "incomingRecordType", incomingRecordType, "existingRecordType", existingRecordType);
  }

  public static Profile fromAttributes(Map<String, String> attributes) {
    return new MappingProfileNode(attributes.get("name"), attributes.get("incomingRecordType"), attributes.get("existingRecordType"));
  }
}
