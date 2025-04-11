package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.*;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang.StringUtils;
import java.lang.reflect.Type;
import java.util.*;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MarketConfigReader implements ConfigReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(MarketConfigReader.class);

  @ManagedConfiguration private MarketBackendConfig marketBackendConfig;
  @ManagedConfiguration private MarketFlagConfig marketFlagConfig;
  @Autowired private MarketTenantConfig marketTenantConfig;
  @Autowired private Gson gson;

  @Override
  public Map<String, Object> getFeatureConfig(String facilityNum, String countryCode) {
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    try {
      String marketType = getMarketTypeByFacility(facilityNum, countryCode);
      if (StringUtils.isNotBlank(marketBackendConfig.getFeatureConfig())
          && StringUtils.isNotBlank(marketType)) {
        String featureConfig =
            JsonParser.parseString(marketBackendConfig.getFeatureConfig())
                .getAsJsonObject()
                .get(marketType)
                .toString();
        return gson.fromJson(featureConfig, type);
      }
    } catch (Exception exception) {
      LOGGER.error(
          SPLUNK_ALERT + " error while reading market config={}, may impact cell. StackTrace={}",
          facilityNum,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ReceivingException.MARKET_CONFIG_ERROR,
          "Exception while loading marketBackendConfig",
          exception);
    }
    return Collections.EMPTY_MAP;
  }

  @Override
  public Map<String, Object> getFlagConfig(String facilityNum, String countryCode) {
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    try {
      String marketType = getMarketTypeByFacility(facilityNum, countryCode);
      if (StringUtils.isNotBlank(marketFlagConfig.getFeatureFlags())
          && StringUtils.isNotBlank(marketType)) {
        String featureFlag =
            JsonParser.parseString(marketFlagConfig.getFeatureFlags())
                .getAsJsonObject()
                .get(marketType)
                .toString();
        return gson.fromJson(featureFlag, type);
      }
    } catch (Exception exception) {
      LOGGER.error(
          SPLUNK_ALERT
              + " error while reading market flag config={}, may impact cell. StackTrace={}",
          facilityNum,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ReceivingException.MARKET_CONFIG_ERROR,
          "Exception while loading marketFlagConfig",
          exception);
    }
    return Collections.EMPTY_MAP;
  }

  @Override
  public Object getConfigInstance(String facilityNum, String ccmKey, String defaultBeanName) {
    throw new NotImplementedException();
  }

  @Override
  public String getConfigProperties(String facilityNum, String ccmKey, String defaultProperty) {
    throw new NotImplementedException();
  }

  @Override
  public Boolean getConfigFlag(String facilityNum, String ccmKey, String defaultFlag) {
    throw new NotImplementedException();
  }

  /**
   * Return market type for a facility number. A facility belongs to a specific market.
   *
   * @param facilityNum
   * @param countryCode
   * @return
   */
  public String getMarketTypeByFacility(String facilityNum, String countryCode) {
    try {
      String sites = getSites(facilityNum, countryCode.toLowerCase());
      Optional<String> marketType =
          marketTenantConfig
              .getSites()
              .entrySet()
              .stream()
              .filter(
                  entry -> Objects.nonNull(entry.getValue()) && entry.getValue().contains(sites))
              .map(entry -> entry.getKey())
              .findFirst();

      if (marketType.isPresent()) return marketType.get();
    } catch (Exception exception) {
      LOGGER.error(
          SPLUNK_ALERT
              + " error while reading market type by tenant={}, may impact cell. StackTrace={}",
          facilityNum,
          ExceptionUtils.getStackTrace(exception));
      throw new ReceivingInternalException(
          ReceivingException.MARKET_CONFIG_ERROR,
          "Exception while loading marketSpecificBackendConfig",
          exception);
    }

    return null;
  }

  public String getSites(String facilityNum, String countryCode) {
    return countryCode.toLowerCase() + "-" + facilityNum;
  }
}
