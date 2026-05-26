package org.folio.hydration;

import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MappingDetailsFactoryTest {
  @Test
  public void holdingsHridFieldUsesHoldingsHridPath() {
    MappingDetail mappingDetail = MappingDetailsFactory.createHoldingsMappingDetails();

    MappingRule hridField = mappingDetail.getMappingFields().stream()
      .filter(field -> "hrid".equals(field.getName()))
      .findFirst()
      .orElseThrow();

    assertEquals("holdings.hrid", hridField.getPath());
  }
}
