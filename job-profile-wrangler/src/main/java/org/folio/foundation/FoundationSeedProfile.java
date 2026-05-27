package org.folio.foundation;

import org.folio.graph.GraphReader;
import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.marc4j.marc.Record;

import java.io.IOException;
import java.util.Optional;

/**
 * Built-in job profiles used to seed prerequisite inventory before a generated
 * import record is run through the developer-selected target profile.
 */
public enum FoundationSeedProfile {
  INSTANCE(900, "/foundation-seed-profiles/jp-900.dot"),
  HOLDINGS(901, "/foundation-seed-profiles/jp-901.dot"),
  ITEM(902, "/foundation-seed-profiles/jp-902.dot");

  private final int repoId;
  private final String resourcePath;

  FoundationSeedProfile(int repoId, String resourcePath) {
    this.repoId = repoId;
    this.resourcePath = resourcePath;
  }

  public int repoId() {
    return repoId;
  }

  public Graph<Profile, RegularEdge> graph() throws IOException {
    return GraphReader.readResource(resourcePath);
  }

  public static Optional<FoundationSeedProfile> forInventoryRecord(Record record) {
    if (record == null || record.getLeader() == null || record.getLeader().getTypeOfRecord() == 'z') {
      return Optional.empty();
    }
    // Item seed records also carry 852 so FOLIO can link the Item to its Holdings;
    // check 945 first to avoid routing Item-capable records through the Holdings seed profile.
    if (record.getVariableField("945") != null) {
      return Optional.of(ITEM);
    }
    if (record.getVariableField("852") != null) {
      return Optional.of(HOLDINGS);
    }
    return Optional.of(INSTANCE);
  }
}
