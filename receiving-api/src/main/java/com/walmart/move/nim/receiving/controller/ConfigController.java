package com.walmart.move.nim.receiving.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.config.AtlasUiConfig;
import com.walmart.move.nim.receiving.core.config.AtlasUiConstants;
import com.walmart.move.nim.receiving.core.config.UserOverridenClientConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("configuration")
public class ConfigController {

  @ManagedConfiguration private AtlasUiConfig uiConfig;
  @ManagedConfiguration private UserOverridenClientConfig userOverridenClientConfig;
  @ManagedConfiguration private AtlasUiConstants uiConstants;
  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  @GetMapping(path = "/clientconfig", produces = "application/json")
  public Map<String, Object> getClientConfig(@RequestHeader HttpHeaders headers) {
    String facilityNumber = TenantContext.getFacilityNum().toString();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();

    JsonObject uiConfigData = new JsonParser().parse(uiConfig.getFeatureFlags()).getAsJsonObject();

    String userId = headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

    if (uiConfigData.get(facilityNumber) == null) {
      return gson.fromJson(uiConfigData.get("default").getAsJsonObject(), type);
    }

    Map<String, Object> response =
        gson.fromJson(uiConfigData.get(facilityNumber).getAsJsonObject(), type);

    if (!StringUtils.isEmpty(userId) && userOverridenClientConfig.getUsers().contains(userId)) {
      JsonObject userOverRiddenConfigJson =
          new JsonParser().parse(userOverridenClientConfig.getFeatureFlags()).getAsJsonObject();
      JsonElement overRiddenConfigForDC = userOverRiddenConfigJson.get(facilityNumber);
      if (overRiddenConfigForDC != null) {
        response = gson.fromJson(overRiddenConfigForDC.getAsJsonObject(), type);
      }
    }

    return response;
  }

  @GetMapping(path = "/clientconstants", produces = "application/json")
  public Map<String, Object> getClientConstants(@RequestHeader HttpHeaders headers) {

    String facilityNumber = TenantContext.getFacilityNum().toString();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    JsonObject uiConstantsData =
        new JsonParser().parse(uiConstants.getConstants()).getAsJsonObject();

    if (uiConstantsData.get(facilityNumber) == null) {
      return gson.fromJson(uiConstantsData.get("default").getAsJsonObject(), type);
    } else {
      return gson.fromJson(uiConstantsData.get(facilityNumber).getAsJsonObject(), type);
    }
  }

  @GetMapping(path = "/appConfig/{propertyName}")
  public String getAppConfigProperty(@PathVariable("propertyName") String propertyName)
      throws NoSuchFieldException, IllegalAccessException {
    Field property = appConfig.getClass().getDeclaredField(propertyName);
    property.setAccessible(true);
    return (String) property.get(appConfig);
  }
}
