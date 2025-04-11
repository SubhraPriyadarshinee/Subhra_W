package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TRUE_STRING;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.reset;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseReferenceLineMeta;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OverrideInfoTest extends ReceivingTestBase {
  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void tearDown() {
    reset();
  }

  @Test
  public void testGetOverrideOverageManager_nullMeta() {
    final OverrideInfo oi = new OverrideInfo();
    final String overrideOverageManager = oi.getOverrideOverageManager();
    final boolean isOverrideOverage = oi.isOverrideOverage();

    assertNull(overrideOverageManager);
    assertFalse(isOverrideOverage);
  }

  @Test
  public void test_NoNull_PoLineMeta_but_NoOverage() {
    final PurchaseReferenceLineMeta poLineMeta = new PurchaseReferenceLineMeta();
    final OverrideInfo oi = new OverrideInfo(poLineMeta);

    final String overrideOverageManager = oi.getOverrideOverageManager();
    final boolean isOverrideOverage = oi.isOverrideOverage();

    assertNull(overrideOverageManager);
    assertFalse(isOverrideOverage);
    ;
  }

  @Test
  public void testGetOverrideOverageManager_valid_overageOverride() {
    final PurchaseReferenceLineMeta poLineMeta = new PurchaseReferenceLineMeta();
    poLineMeta.setIgnoreOverageBy("k0c0e5k");
    poLineMeta.setIgnoreOverage(TRUE_STRING);
    final OverrideInfo oi = new OverrideInfo(poLineMeta);

    final String overrideOverageManager = oi.getOverrideOverageManager();
    final boolean isOverrideOverage = oi.isOverrideOverage();

    assertNotNull(overrideOverageManager);
    assertTrue(isOverrideOverage);
  }
}
