package com.walmart.move.nim.receiving.core.client.orderservice;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.orderservice.model.LpnUpdateRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OrderServiceRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(OrderServiceRestApiClient.class);
  @Autowired private Gson gson;
  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Async
  public void sendLabelUpdate(LpnUpdateRequest lpnsCancellationRequest, HttpHeaders httpHeaders) {
    HttpHeaders forwardableHeaders =
        ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    // Setting tenant context as it is an async function
    TenantContext.setFacilityNum(
        Integer.valueOf(
            Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM))
                .get(0)));
    TenantContext.setFacilityCountryCode(
        Objects.requireNonNull(forwardableHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE))
            .get(0));
    String orderServiceUrl =
        appConfig.getOrderServiceBaseUrl() + ReceivingConstants.UPDATE_REJECTED_LABELS_IN_OP_URI;

    LOGGER.info(
        "Sending label update to OP for PO: {} and item:{} in url:{} and labelUpdatePayLoad:{}, headers:{}",
        lpnsCancellationRequest.getPurchaseReferenceNumber(),
        lpnsCancellationRequest.getItemNumber(),
        orderServiceUrl,
        lpnsCancellationRequest,
        forwardableHeaders);

    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              orderServiceUrl,
              HttpMethod.PUT,
              new HttpEntity<>(gson.toJson(lpnsCancellationRequest), forwardableHeaders),
              String.class);
      LOGGER.info("Received response: {} from OP for the lpnsUpdate", response.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          orderServiceUrl,
          lpnsCancellationRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          orderServiceUrl,
          lpnsCancellationRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
    }
  }
}
