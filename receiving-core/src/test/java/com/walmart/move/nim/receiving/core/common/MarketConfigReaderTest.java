package com.walmart.move.nim.receiving.core.common;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.*;
import com.walmart.move.nim.receiving.data.MockFeatureFlags;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.CollectionUtils;

@RunWith(MockitoJUnitRunner.class)
public class MarketConfigReaderTest {
  @InjectMocks private MarketConfigReader marketConfigReader;
  @Mock private MarketBackendConfig marketBackendConfig;
  @Mock private MarketFlagConfig marketFlagConfig;
  @Mock private MarketTenantConfig marketTenantConfig;

  @Mock private MarketConfigHelper marketConfigHelper;

  private Gson gson = new Gson();

  private Map<String, Set<String>> marketTenants = new HashMap<>();

  @Before
  public void init() {
    Set<String> storeSites = new HashSet<>();
    storeSites.add("us-100");
    storeSites.add("us-3268");
    Set<String> mfcSites = new HashSet<>();
    mfcSites.add("us-5504");
    mfcSites.add("us-3284");
    marketTenants.put("STORE", storeSites);
    marketTenants.put("MFC", mfcSites);
    ReflectionTestUtils.setField(marketConfigReader, "gson", gson);
  }

  @Test
  public void testMarketType1() {
    when(marketBackendConfig.getFeatureConfig())
        .thenReturn(MockFeatureFlags.MARKET_FEATURE_CONFIG_JSON);
    when(marketFlagConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.MARKET_FLAGS_CONFIG_JSON);
    when(marketTenantConfig.getSites()).thenReturn(marketTenants);

    Map<String, Object> response1 = marketConfigReader.getFeatureConfig("5504", "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig("5504", "us");

    assertEquals("v2StoreInboundContainerCreate", response1.get("containerCreateProcessor"));
    assertEquals("kafkaDeliveryStatusProcessor", response1.get("deliveryStatusHandler"));
    assertEquals("mfcOSDRService", response1.get("osdrService"));
    assertEquals("false", response2.get("enable.store.pallet.osdr"));
  }

  @Test
  public void testMarketType2() {
    when(marketBackendConfig.getFeatureConfig())
        .thenReturn(MockFeatureFlags.MARKET_FEATURE_CONFIG_JSON);
    when(marketFlagConfig.getFeatureFlags()).thenReturn(MockFeatureFlags.MARKET_FLAGS_CONFIG_JSON);
    when(marketTenantConfig.getSites()).thenReturn(marketTenants);

    Map<String, Object> response1 = marketConfigReader.getFeatureConfig("100", "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig("100", "us");

    assertEquals("v3StoreInboundContainerCreate", response1.get("containerCreateProcessor"));
    assertEquals("kafkaDeliveryStatusProcessorV2", response1.get("deliveryStatusHandler"));
    assertEquals("oSDRService", response1.get("osdrService"));
    assertEquals("true", response2.get("enable.store.pallet.osdr"));
  }

  @Test(expected = ReceivingInternalException.class)
  public void testMarketType3() {
    when(marketTenantConfig.getSites()).thenReturn(null);

    Map<String, Object> response1 = marketConfigReader.getFeatureConfig("100", "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig("100", "us");

    assertNull(response1);
    assertNull(response2);
  }

  @Test
  public void testMarketType4() {
    Map<String, Object> response1 = marketConfigReader.getFeatureConfig("100", "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig("100", "us");
    assertEquals(CollectionUtils.isEmpty(response1), true);
    assertEquals(CollectionUtils.isEmpty(response2), true);
  }

  @Test
  public void testMarketType5() {
    when(marketTenantConfig.getSites()).thenReturn(marketTenants);

    Map<String, Object> response1 = marketConfigReader.getFeatureConfig(null, "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig(null, "us");
    assertEquals(CollectionUtils.isEmpty(response1), true);
    assertEquals(CollectionUtils.isEmpty(response2), true);
  }

  @Test
  public void testMarketType6() {
    when(marketTenantConfig.getSites()).thenReturn(marketTenants);

    Map<String, Object> response1 = marketConfigReader.getFeatureConfig("1234", "us");
    Map<String, Object> response2 = marketConfigReader.getFlagConfig("1234", "us");
    assertEquals(CollectionUtils.isEmpty(response1), true);
    assertEquals(CollectionUtils.isEmpty(response2), true);
  }
}
