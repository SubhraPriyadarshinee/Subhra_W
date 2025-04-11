package com.walmart.move.nim.receiving.core.client.damage;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.replacePathParams;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for Damage Rest API
 *
 * @author v0k00fe
 */
@Component
public class DamageRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(DamageRestApiClient.class);

  private static final String GET_DAMAGES_BY_DELIVERY = "/delivery/{deliveryNumber}/damages";

  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @ManagedConfiguration private AppConfig appConfig;

  /**
   * Finds Damage Details by Delivery Number
   *
   * @param deliveryNumber
   * @param headers
   * @return
   * @throws DamageRestApiClientException
   */
  @Timed(
      name = "findDamagesByDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "findDamagesByDelivery")
  @ExceptionCounted(
      name = "findDamagesByDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "findDamagesByDelivery")
  public Optional<List<DamageDeliveryInfo>> findDamagesByDelivery(
      Long deliveryNumber, Map<String, Object> forwardableHeaders)
      throws DamageRestApiClientException {

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY,
        forwardableHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    requestHeaders.set(
        ReceivingConstants.TENENT_FACLITYNUM,
        forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString());
    requestHeaders.set(
        ReceivingConstants.COUNTRY_CODE,
        forwardableHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    requestHeaders.set(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        forwardableHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());

    String baseUrl;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString(),
        FIXIT_ENABLED,
        false)) {
      requestHeaders =
          ReceivingUtils.getServiceMeshHeaders(
              requestHeaders,
              appConfig.getReceivingConsumerId(),
              appConfig.getFixitServiceName(),
              appConfig.getFixitServiceEnv());
      baseUrl =
          appConfig.getFixitPlatformBaseUrl() + PROBLEM_V1_URI + FIXIT_GET_DAMAGES_BY_DELIVERY_URI;
    } else {
      baseUrl = appConfig.getDamageAppBaseUrl() + GET_DAMAGES_BY_DELIVERY;
    }

    String uri = replacePathParams(baseUrl, pathParams).toString();
    LOGGER.info("Get damages by delivery URI = {}, requestHeaders = {}", uri, requestHeaders);

    ResponseEntity<String> deliveryDamagesResponseEntity = null;
    try {
      deliveryDamagesResponseEntity =
          restConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          "Get damages with deliveryNumber={} got responseCode={} & response={}",
          deliveryNumber,
          e.getRawStatusCode(),
          e.getResponseBodyAsString());

      throw new DamageRestApiClientException(
          e.getResponseBodyAsString(), HttpStatus.valueOf(e.getRawStatusCode()));
    }

    LOGGER.info(
        "Get damages with deliveryNumber={} got response={}",
        deliveryNumber,
        deliveryDamagesResponseEntity.getBody());

    Type type = new TypeToken<List<DamageDeliveryInfo>>() {}.getType();
    return Optional.ofNullable(gson.fromJson(deliveryDamagesResponseEntity.getBody(), type));
  }
}
