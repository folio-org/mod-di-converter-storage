package org.folio.recordgen;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class RoundRobinIdentifiersTest {

  @Test
  public void testConstructorWithEmptyIterable() {
    RoundRobinIdentifiers identifiers = new RoundRobinIdentifiers(Collections.emptyList());
    Assert.assertNull(identifiers.get());
  }

  @Test
  public void testGetWithEmptyQueue() {
    RoundRobinIdentifiers identifiers = new RoundRobinIdentifiers(Collections.emptyList());
    Assert.assertNull(identifiers.get());
  }

  @Test
  public void testGetWithSingleItemQueue() {
    RoundRobinIdentifier roundRobinIdentifier = RoundRobinIdentifier.builder()
      .instanceId("instanceId")
      .holdingsId("holdingsId")
      .itemId("itemId")
      .sourceRecordId("sourceRecordId")
      .build();

    RoundRobinIdentifiers identifiers = new RoundRobinIdentifiers(Collections.singletonList(roundRobinIdentifier));
    Assert.assertEquals(roundRobinIdentifier, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier, identifiers.next());
    Assert.assertEquals(roundRobinIdentifier, identifiers.get());
  }

  @Test
  public void testGetWithMultipleItemsQueue() {
    RoundRobinIdentifier roundRobinIdentifier1 = new RoundRobinIdentifier(
      "instanceId1", "holdingsId1", "itemId1", "authorityId","sourceRecordId1");
    RoundRobinIdentifier roundRobinIdentifier2 = new RoundRobinIdentifier(
      "instanceId2", "holdingsId2", "itemId2", "authorityId", "sourceRecordId2");
    RoundRobinIdentifier roundRobinIdentifier3 = new RoundRobinIdentifier(
      "instanceId3", "holdingsId3", "itemId3","authorityId", "sourceRecordId3");
    RoundRobinIdentifiers identifiers = new RoundRobinIdentifiers(Arrays.asList(
      roundRobinIdentifier1, roundRobinIdentifier2, roundRobinIdentifier3));
    Assert.assertEquals(roundRobinIdentifier1, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier1, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier2, identifiers.next());
    Assert.assertEquals(roundRobinIdentifier2, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier3, identifiers.next());
    Assert.assertEquals(roundRobinIdentifier3, identifiers.get());
    Assert.assertEquals(roundRobinIdentifier1, identifiers.next());
    Assert.assertEquals(roundRobinIdentifier1, identifiers.get());
  }
}
