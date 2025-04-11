package com.walmart.move.nim.receiving.core.client.orderfulfillment;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.orderfulfillment.model.PrintShippingLabelRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class OrderFulfillmentRestApiClient {
  private static final Logger LOGGER = LoggerFactory.getLogger(OrderFulfillmentRestApiClient.class);
  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Timed(
      name = "printShippingLabelFromRoutingLabelTimed",
      level1 = "uwms-receiving",
      level2 = "orderFulfillmentRestApiClient",
      level3 = "printShippingLabelFromRoutingLabel")
  @ExceptionCounted(
      name = "printShippingLabelFromRoutingLabelExceptionCount",
      level1 = "uwms-receiving",
      level2 = "orderFulfillmentRestApiClient",
      level3 = "printShippingLabelFromRoutingLabel")
  public Optional<Map<String, Object>> printShippingLabelFromRoutingLabel(
      PrintShippingLabelRequest printShippingLabelRequest, HttpHeaders httpHeaders) {
    HttpHeaders forwardableHeaders =
        ReceivingUtils.getForwardableHttpHeadersWithRequestOriginator(httpHeaders);
    String printShippingLabelFromRlUrl =
        appConfig.getOrderFulfillmentBaseUrl()
            + ReceivingConstants.PRINT_SHIPPING_LABEL_FROM_ROUTING_LABEL_URI;

    LOGGER.info(
        "Order fulfillment print shipping label from routing label api call with, Url: [{}], payload: [{}] headers: [{}]",
        printShippingLabelFromRlUrl,
        printShippingLabelRequest,
        httpHeaders);
    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              printShippingLabelFromRlUrl,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(printShippingLabelRequest), forwardableHeaders),
              String.class);
      LOGGER.info(
          "Received response: {} from Order Fulfillment print SL from RL api call",
          response.getBody());
      return Optional.ofNullable(
          gson.fromJson(response.getBody(), new TypeToken<Map<String, Object>>() {}.getType()));
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          printShippingLabelFromRlUrl,
          printShippingLabelRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.ORDER_FULFILLMENT_PRINT_SL_FROM_RL_FAILED,
          String.format(
              ReceivingConstants.ORDER_FULFILLMENT_PRINT_SL_FROM_RL_FAILED,
              printShippingLabelRequest.getRoutingLabelId()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          printShippingLabelFromRlUrl,
          printShippingLabelRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.OF_SERVER_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
  }
}
