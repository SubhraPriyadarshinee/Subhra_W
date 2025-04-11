package com.walmart.move.nim.receiving.witron.common;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DC_ONE_ATLAS_ENABLED;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.*;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.IS_DCFIN_API_DISABLED;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class GDCFlagReaderTest {

  @Mock private TenantSpecificConfigReader configUtils;
  @InjectMocks private GDCFlagReader gdcFlagReader;

  @BeforeMethod
  public void createDeliveryCacheServiceInMemoryImpl() throws Exception {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32612);
    TenantContext.setCorrelationId("a1-b2-c3");
  }

  @AfterMethod
  public void tearDown() {
    reset(configUtils);
  }

  @Test
  public void testIsManualGdcEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);

    assertEquals(gdcFlagReader.isManualGdcEnabled(), true);
  }

  @Test
  public void testPublishToWitronDisabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), PUBLISH_TO_WITRON_DISABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.publishToWitronDisabled(), true);
  }

  @Test
  public void testPublishToWFTDisabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), PUBLISH_TO_WFT_DISABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.publishToWFTDisabled(), true);
  }

  @Test
  public void testIsDCFinApiDisabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DCFIN_API_DISABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isDCFinApiDisabled(), true);
  }

  @Test
  public void testIsGLSApiEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_GLS_API_ENABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isGLSApiEnabled(), true);
  }

  @Test
  public void testIsSmartSlottingApiDisabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_SMART_SLOTTING_DISABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isSmartSlottingApiDisabled(), true);
  }

  @Test
  public void testIsLpnGenApiDisabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_LPN_GEN_DISABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isLpnGenApiDisabled(), true);
  }

  @Test
  public void testIsItemConfigApiEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), ITEM_CONFIG_SERVICE_ENABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isItemConfigApiEnabled(), true);
  }

  @Test
  public void testIsDCOneAtlasEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_DC_ONE_ATLAS_ENABLED, false))
        .thenReturn(true);
    assertEquals(gdcFlagReader.isDCOneAtlasEnabled(), true);
  }

  @Test
  public void testIsAutomatedDCTrue() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(false);

    assertEquals(gdcFlagReader.isAutomatedDC(), true);
  }

  @Test
  public void testIsAutomatedDCFalse() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_MANUAL_GDC_ENABLED, false))
        .thenReturn(true);

    assertEquals(gdcFlagReader.isAutomatedDC(), false);
  }

  @Test
  void testIsReceivingInstructsPutAwayMoveToMM() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM, true))
        .thenReturn(true);

    assertEquals(gdcFlagReader.isReceivingInstructsPutAwayMoveToMM(), true);
  }

  @Test
  void testIsReceivingProgressPubEnabled() {
    when(configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), IS_RECEIVING_PROGRESS_PUB_ENABLED, false))
        .thenReturn(true);

    assertEquals(gdcFlagReader.isReceivingProgressPubEnabled(), true);
  }

  @Test
  void test_getVirtualPrimeSlotForIntoOss() {
    when(configUtils.getCcmValue(
            eq(32612), eq(VIRTUAL_PRIME_SLOT_INTO_OSS), eq(DEFAULT_VIRTUAL_PRIME_SLOT_INTO_OSS)))
        .thenReturn(DEFAULT_VIRTUAL_PRIME_SLOT_INTO_OSS);
    assertEquals(gdcFlagReader.getVirtualPrimeSlotForIntoOss("3"), "glbl_3");
  }
}
