package com.walmart.move.nim.receiving.core.common;

import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.data.MockFeatureFlags;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class MarketConfigHelperTest {

  @InjectMocks private MarketConfigHelper marketConfigHelper;
  @Mock private MarketConfigReader marketConfigReader;

  private Gson gson = new Gson();

  @Before
  public void init() {
    ReflectionTestUtils.setField(marketConfigHelper, "gson", gson);
  }

  @Test
  public void testMarketType1() {
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    String featureConfig =
        JsonParser.parseString(MockFeatureFlags.MARKET_FEATURE_CONFIG_JSON)
            .getAsJsonObject()
            .get("STORE")
            .toString();
    Map<String, Object> featureConfigMap = gson.fromJson(featureConfig, type);

    String flagConfig =
        JsonParser.parseString(MockFeatureFlags.MARKET_FLAGS_CONFIG_JSON)
            .getAsJsonObject()
            .get("STORE")
            .toString();
    Map<String, Object> flagConfigMap = gson.fromJson(flagConfig, type);

    when(marketConfigReader.getSites("100", "us")).thenReturn("us-100");
    when(marketConfigReader.getFeatureConfig("100", "us")).thenReturn(featureConfigMap);
    when(marketConfigReader.getFlagConfig("100", "us")).thenReturn(flagConfigMap);

    String response = marketConfigHelper.getFeatureFlagsByFacility("100", "us");
    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

    assertEquals(
        "v3StoreInboundContainerCreate", jsonObject.get("containerCreateProcessor").getAsString());
    assertEquals(
        "kafkaDeliveryStatusProcessorV2", jsonObject.get("deliveryStatusHandler").getAsString());
    assertEquals("oSDRService", jsonObject.get("osdrService").getAsString());
    assertEquals("true", jsonObject.get("enable.store.pallet.osdr").getAsString());
  }

  @Test
  public void testMarketType2() {
    when(marketConfigReader.getSites("100", "us")).thenReturn("us-100");
    when(marketConfigReader.getFeatureConfig("100", "us")).thenReturn(Collections.EMPTY_MAP);
    when(marketConfigReader.getFlagConfig("100", "us")).thenReturn(Collections.EMPTY_MAP);

    String response = marketConfigHelper.getFeatureFlagsByFacility("100", "us");
    assertNull(response);
  }

  @Test
  public void testMarketType3() {
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    String featureConfig =
        JsonParser.parseString(MockFeatureFlags.MARKET_FEATURE_CONFIG_JSON)
            .getAsJsonObject()
            .get("STORE")
            .toString();
    Map<String, Object> featureConfigMap = gson.fromJson(featureConfig, type);

    when(marketConfigReader.getSites("100", "us")).thenReturn("us-100");
    when(marketConfigReader.getFeatureConfig("100", "us")).thenReturn(featureConfigMap);
    when(marketConfigReader.getFlagConfig("100", "us")).thenReturn(Collections.EMPTY_MAP);

    String response = marketConfigHelper.getFeatureFlagsByFacility("100", "us");
    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

    assertEquals(
        "v3StoreInboundContainerCreate", jsonObject.get("containerCreateProcessor").getAsString());
    assertEquals(
        "kafkaDeliveryStatusProcessorV2", jsonObject.get("deliveryStatusHandler").getAsString());
    assertEquals("oSDRService", jsonObject.get("osdrService").getAsString());
  }

  @Test
  public void testMarketType4() {
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    String flagConfig =
        JsonParser.parseString(MockFeatureFlags.MARKET_FLAGS_CONFIG_JSON)
            .getAsJsonObject()
            .get("STORE")
            .toString();
    Map<String, Object> flagConfigMap = gson.fromJson(flagConfig, type);

    when(marketConfigReader.getSites("100", "us")).thenReturn("us-100");
    when(marketConfigReader.getFeatureConfig("100", "us")).thenReturn(Collections.EMPTY_MAP);
    when(marketConfigReader.getFlagConfig("100", "us")).thenReturn(flagConfigMap);

    String response = marketConfigHelper.getFeatureFlagsByFacility("100", "us");
    JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

    assertEquals("true", jsonObject.get("enable.store.pallet.osdr").getAsString());
  }
}
