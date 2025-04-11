package com.walmart.move.nim.receiving.core.client.itemupdate;

import static com.walmart.move.nim.receiving.core.advice.Type.REST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_UPDATE_SERVICE_ERROR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ITEM_UPDATE_SERVICE_URI;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.common.ItemUpdateUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateRequest;
import com.walmart.move.nim.receiving.core.model.itemupdate.ItemUpdateResponse;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ItemUpdateRestApiClient {
  public static final Logger LOGGER = LoggerFactory.getLogger(ItemUpdateRestApiClient.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private Gson gson;
  @Autowired private ItemUpdateUtils itemUpdateUtils;

  @TimeTracing(component = AppComponent.CORE, flow = "updateItem", externalCall = true, type = REST)
  @ExceptionCounted(
      name = "itemUpdateExceptionCount",
      level1 = "uwms-receiving",
      level2 = "itemUpdateRestApiClient",
      level3 = "itemUpdate")
  public ItemUpdateResponse updateItem(
      ItemUpdateRequest itemUpdateRequest, HttpHeaders httpHeaders) {
    HttpHeaders itemUpdateHeaders = itemUpdateUtils.getIqsItemUpdateHeaders(httpHeaders);
    LOGGER.info(
        "Calling item update api with headers = {} and request body = {} ",
        itemUpdateHeaders,
        itemUpdateRequest);
    ItemUpdateResponse itemUpdateResponse = itemUpdateService(itemUpdateRequest, itemUpdateHeaders);
    return itemUpdateResponse;
  }

  public ItemUpdateResponse itemUpdateService(
      ItemUpdateRequest itemUpdateRequest, HttpHeaders httpHeaders) {
    String uri = appConfig.getItemUpdateBaseUrl() + ITEM_UPDATE_SERVICE_URI;
    LOGGER.info(
        "Calling item update api with url = {} and request body = {} ", uri, itemUpdateRequest);
    ResponseEntity<String> responseEntity = null;
    try {
      HttpMethod requestMethod =
          configUtils.isFeatureFlagEnabled(ReceivingConstants.IQS_ITEM_UPSERT_ENABLED)
              ? HttpMethod.POST
              : HttpMethod.PUT;
      responseEntity =
          retryableRestConnector.exchange(
              uri,
              requestMethod,
              new HttpEntity<>(gson.toJson(itemUpdateRequest), httpHeaders),
              String.class);
      LOGGER.info(
          "Successfully received response from {} item update api with status = {}",
          requestMethod,
          responseEntity.getStatusCode());
      return gson.fromJson(responseEntity.getBody(), ItemUpdateResponse.class);
    } catch (RestClientResponseException e) {
      String errorCode = ExceptionCodes.ITEM_UPDATE_INTERNAL_SERVER_ERROR;
      if (HttpStatus.BAD_REQUEST.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.INVALID_ITEM_UPDATE_REQUEST;
      } else if (HttpStatus.SERVICE_UNAVAILABLE.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.ITEM_UPDATE_SERVICE_UNAVAILABLE;
      } else if (HttpStatus.NOT_FOUND.value() == e.getRawStatusCode()) {
        errorCode = ExceptionCodes.ITEM_UPDATE_NOT_FOUND;
      }

      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          errorCode);
      throw new ReceivingBadDataException(errorCode, ITEM_UPDATE_SERVICE_ERROR);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          StringUtils.EMPTY,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_ITEM_UPDATE_REQUEST,
          String.format(ReceivingConstants.ITEM_UPDATE_SERVICE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }
}
