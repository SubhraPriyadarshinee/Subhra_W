package com.walmart.move.nim.receiving.core.client.oms;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.replacePathParams;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.OMSPurchaseOrderResponse;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.libs.commons.lang.exception.ExceptionUtils;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/** Client for OMS Rest API */
@Component
public class OmsRestApiClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(OmsRestApiClient.class);

  @Resource(name = "retryableRestConnector")
  RetryableRestConnector retryableRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  @Timed(
      name = "fetchPODetailsFromOMSTimed",
      level1 = "uwms-receiving",
      level2 = "omsApiClient",
      level3 = "fetchPODetailsFromOMS")
  @ExceptionCounted(
      name = "fetchPODetailsFromOMSCount",
      level1 = "uwms-receiving",
      level2 = "omsApiClient",
      level3 = "fetchPODetailsFromOMS")
  public OMSPurchaseOrderResponse getPODetailsFromOMS(String poNumber) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.PURCHASE_ORDER_NUMBER, poNumber);
    String url =
        replacePathParams(
                appConfig.getOmsBaseUrl() + ReceivingConstants.OMS_FETCH_PURCHASE_ORDER_INFO,
                pathParams)
            .toString();

    LOGGER.info("Get PurchaseOrder from OMS URI = {}", url);

    ResponseEntity<String> response = null;
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(ACCEPT, APPLICATION_JSON);
    requestHeaders.set(CONTENT_TYPE, APPLICATION_JSON);
    try {
      response =
          retryableRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (Exception e) {
      LOGGER.error(
          OMS_GET_PO_DETAILS_ERROR, url, poNumber, requestHeaders, ExceptionUtils.getStackTrace(e));
      return null;
    }
    if (Objects.nonNull(response)
        && response.hasBody()
        && response.getStatusCode() == HttpStatus.OK) {
      return gson.fromJson(response.getBody(), OMSPurchaseOrderResponse.class);
    } else {
      LOGGER.error(OMS_GET_PO_DETAILS_UNSUCCESSFUL, poNumber, url, requestHeaders, response);
      return null;
    }
  }
}
