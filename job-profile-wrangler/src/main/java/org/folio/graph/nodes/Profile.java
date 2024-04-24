package org.folio.graph.nodes;

import java.util.Comparator;
import java.util.Map;

public interface Profile {

  String getName();

  int getOrder();

  Map<String, String> getAttributes();

  <T extends Profile> Comparator<T> getComparator();
}
