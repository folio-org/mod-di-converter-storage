package org.folio.recordgen;

import java.util.LinkedList;
import java.util.Queue;

public class RoundRobinIdentifiers {
  private Queue<RoundRobinIdentifier> queue;

  public RoundRobinIdentifiers(Iterable<RoundRobinIdentifier> items) {
    queue = new LinkedList<>();
    for (RoundRobinIdentifier item : items) {
      queue.offer(item);
    }
  }

  public RoundRobinIdentifier get() {
    if (queue.isEmpty()) {
      return null;
    }
    return queue.peek();
  }

  public RoundRobinIdentifier next() {
    if (queue.isEmpty()) {
      return null;
    }
    RoundRobinIdentifier item = queue.poll();
    queue.offer(item);
    return queue.peek();
  }
}
