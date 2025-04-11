package com.walmart.move.nim.receiving.core.client.scheduler;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.walmart.move.nim.receiving.core.client.scheduler.model.PoAppendRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SchedulerRestApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(SchedulerRestApiClient.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RetryableRestConnector retryableRestConnector;

  public void appendPoToDelivery(PoAppendRequest requestBody, HttpHeaders requestHeaders) {
    String url =
        appConfig.getSchedulerBaseUrl() + ReceivingConstants.SCHEDULER_APPEND_PO_TO_DELIVERY;
    LOG.info(
        "Trying Append PO to Delivery URI = {} , AppendRequestBody = {} ",
        url,
        ReceivingUtils.stringfyJson(requestBody));

    ResponseEntity<String> response = null;
    HttpHeaders headers = new HttpHeaders();
    headers.set(SCH_FACILITY_COUNTRY_CODE, requestHeaders.getFirst(TENENT_COUNTRY_CODE));
    headers.set(SCH_FACILITY_NUMBER, requestHeaders.getFirst(TENENT_FACLITYNUM));
    headers.set(CONTENT_TYPE, APPLICATION_JSON);
    try {
      response =
          retryableRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), String.class);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          requestBody,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
    } catch (Exception e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          requestBody,
          RESTUTILS_UNKNOWN_EXCEPTION,
          ExceptionUtils.getStackTrace(e));
    }
    LOG.info(
        SCHEDULER_APPEND_PO_TO_DELIVERY_DETAILS,
        url,
        ReceivingUtils.stringfyJson(requestBody),
        response);

    if (Objects.nonNull(response) && response.getStatusCode().is2xxSuccessful()) {
      LOG.info(
          SCHEDULER_APPEND_PO_TO_DELIVERY_RESPONSE, response.getStatusCodeValue(), requestBody);
    } else {
      LOG.error(SCHEDULER_APPEND_PO_TO_DELIVERY_ERROR, url, requestBody, response, headers);
    }
  }
}
