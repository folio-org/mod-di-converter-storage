package org.folio.hydration;

import org.folio.rest.jaxrs.model.MappingDetail;
import org.folio.rest.jaxrs.model.MappingRule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

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

  @Test
  public void holdingsTypeDoesNotUseLiteralNameForUuidField() {
    MappingDetail mappingDetail = MappingDetailsFactory.createHoldingsMappingDetails();

    MappingRule holdingsTypeField = mappingDetail.getMappingFields().stream()
      .filter(field -> "holdingsTypeId".equals(field.getName()))
      .findFirst()
      .orElseThrow();

    assertEquals("holdings.holdingsTypeId", holdingsTypeField.getPath());
    assertEquals("false", holdingsTypeField.getEnabled());
    assertEquals("", holdingsTypeField.getValue());
  }

  @Test
  public void defaultHoldingsDiscoverySuppressKeepsTenantDefaultMapping() {
    MappingDetail mappingDetail = MappingDetailsFactory.createHoldingsMappingDetails();

    MappingRule discoverySuppressField = findField(mappingDetail, "discoverySuppress");

    assertEquals("holdings.discoverySuppress", discoverySuppressField.getPath());
    assertEquals("true", discoverySuppressField.getEnabled());
    assertEquals("", discoverySuppressField.getValue());
    assertNull(discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void foundationHoldingsDiscoverySuppressSeedsExplicitFalse() {
    MappingDetail mappingDetail = MappingDetailsFactory.createFoundationHoldingsMappingDetails();

    MappingRule discoverySuppressField = findField(mappingDetail, "discoverySuppress");

    assertEquals("holdings.discoverySuppress", discoverySuppressField.getPath());
    assertEquals("true", discoverySuppressField.getEnabled());
    assertEquals(MappingRule.BooleanFieldAction.ALL_FALSE, discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void foundationInstanceDiscoverySuppressSeedsExplicitFalse() {
    MappingDetail mappingDetail = MappingDetailsFactory.createFoundationInstanceMappingDetails();

    MappingRule discoverySuppressField = findField(mappingDetail, "discoverySuppress");

    assertEquals("instance.discoverySuppress", discoverySuppressField.getPath());
    assertEquals(MappingRule.BooleanFieldAction.ALL_FALSE, discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void foundationItemDiscoverySuppressSeedsExplicitFalse() {
    MappingDetail mappingDetail = MappingDetailsFactory.createFoundationItemMappingDetails();

    MappingRule discoverySuppressField = findField(mappingDetail, "discoverySuppress");

    assertEquals("item.discoverySuppress", discoverySuppressField.getPath());
    assertEquals(MappingRule.BooleanFieldAction.ALL_FALSE, discoverySuppressField.getBooleanFieldAction());
  }

  @Test
  public void foundationDiscoverySuppressGuardFailsWhenExpectedFieldIsMissing() {
    MappingDetail mappingDetail = new MappingDetail()
      .withName("holdings")
      .withMappingFields(List.of(new MappingRule()
        .withName("notDiscoverySuppress")
        .withPath("holdings.otherField")));

    IllegalStateException exception = assertThrows(IllegalStateException.class,
      () -> MappingDetailsFactory.withDiscoverySuppressFalse(mappingDetail, "holdings.discoverySuppress"));

    assertEquals("Mapping detail is missing required foundation seed field: holdings.discoverySuppress",
      exception.getMessage());
  }

  private MappingRule findField(MappingDetail mappingDetail, String name) {
    return mappingDetail.getMappingFields().stream()
      .filter(field -> name.equals(field.getName()))
      .findFirst()
      .orElseThrow();
  }
}
