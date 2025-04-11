package com.walmart.move.nim.receiving.core.client.orderwell;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.OrderWellZoneRequest;
import com.walmart.move.nim.receiving.core.common.OrderWellZoneResponse;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

@Service
public class OrderWellClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderWellClient.class);

  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  public OrderWellZoneResponse getStoreMFCDistributionforStoreNbrandPO(
      OrderWellZoneRequest orderWellZoneRequest) {
    LOGGER.info("Hitting OrderWell Client with request :{}", orderWellZoneRequest);
    HttpHeaders httpHeaders = populateOrderWellRequestHeaders();
    ResponseEntity<String> orderWellZoneResponseBody = null;
    String uri =
        appConfig.getOrderWellBaseUrl()
            + ReceivingConstants.ORDER_WELL_STORAGE_ZONE_DISTRIBUTION_URL;
    String orderWellZoneRequestBody = gson.toJson(orderWellZoneRequest);
    HttpEntity<String> request = new HttpEntity<>(orderWellZoneRequestBody, httpHeaders);
    try {
      LOGGER.info("OrderWellResponse for  url={}, request={}", uri, request);
      orderWellZoneResponseBody =
          restConnector.exchange(uri, HttpMethod.POST, request, String.class);
      LOGGER.info("OrderWellResponse for response={}", orderWellZoneResponseBody.getBody());
    } catch (Exception e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getStackTrace(),
          ExceptionUtils.getStackTrace(e));
      return null;
    }
    return gson.fromJson(orderWellZoneResponseBody.getBody(), OrderWellZoneResponse.class);
  }

  private HttpHeaders populateOrderWellRequestHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.WM_CONSUMER_ID, appConfig.getOrderWellConsumerId());
    httpHeaders.set(ReceivingConstants.WM_SVC_NAME, appConfig.getOrderWellServiceName());
    httpHeaders.set(ReceivingConstants.WM_SVC_ENV, appConfig.getOrderWellServiceEnv());
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    return httpHeaders;
  }
}
