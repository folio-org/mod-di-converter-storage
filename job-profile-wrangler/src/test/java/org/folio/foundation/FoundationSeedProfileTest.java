package org.folio.foundation;

import org.folio.graph.edges.RegularEdge;
import org.folio.graph.nodes.Profile;
import org.jgrapht.Graph;
import org.junit.Test;
import org.marc4j.marc.MarcFactory;
import org.marc4j.marc.Record;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FoundationSeedProfileTest {
  private static final MarcFactory MARC_FACTORY = MarcFactory.newInstance();

  @Test
  public void builtInSeedProfileGraphsCanBeLoaded() throws Exception {
    for (FoundationSeedProfile seedProfile : FoundationSeedProfile.values()) {
      Graph<Profile, RegularEdge> graph = seedProfile.graph();

      assertFalse(graph.vertexSet().isEmpty());
      assertFalse(graph.edgeSet().isEmpty());
    }
  }

  @Test
  public void inventoryRecordBucketsPreferItemThenHoldingsThenInstance() {
    Record item = MARC_FACTORY.newRecord();
    item.setLeader(MARC_FACTORY.newLeader("00000nam a2200000 i 4500"));
    item.addVariableField(MARC_FACTORY.newDataField("852", ' ', ' '));
    item.addVariableField(MARC_FACTORY.newDataField("945", ' ', ' '));

    Record holdings = MARC_FACTORY.newRecord();
    holdings.setLeader(MARC_FACTORY.newLeader("00000nam a2200000 i 4500"));
    holdings.addVariableField(MARC_FACTORY.newDataField("852", ' ', ' '));

    Record instance = MARC_FACTORY.newRecord();
    instance.setLeader(MARC_FACTORY.newLeader("00000nam a2200000 i 4500"));

    Record authority = MARC_FACTORY.newRecord();
    authority.setLeader(MARC_FACTORY.newLeader("00000nz  a2200000n  4500"));

    assertEquals(FoundationSeedProfile.ITEM, FoundationSeedProfile.forInventoryRecord(item).orElseThrow());
    assertEquals(FoundationSeedProfile.HOLDINGS, FoundationSeedProfile.forInventoryRecord(holdings).orElseThrow());
    assertEquals(FoundationSeedProfile.INSTANCE, FoundationSeedProfile.forInventoryRecord(instance).orElseThrow());
    assertTrue(FoundationSeedProfile.forInventoryRecord(authority).isEmpty());
  }
}
