package org.folio.support;

import io.vertx.junit5.VertxTestContext;

/**
 * Base class for tests that verify behaviour of pre-seeded default profiles.
 * Overrides {@link #clearTables(VertxTestContext)} with a no-op so default profiles
 * inserted during tenant initialisation are preserved between test methods.
 */
public abstract class AbstractDefaultProfileTest extends AbstractRestTest {

  @Override
  protected void clearTables(VertxTestContext testContext) {
    testContext.completeNow();
  }
}
