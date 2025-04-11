package com.walmart.move.nim.receiving.core.client.iqs;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityCountryCode;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.UUID.randomUUID;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemBulkResponseDto;
import com.walmart.move.nim.receiving.core.client.iqs.model.ItemRequestDto;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.retry.RetryableRestConnector;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.lang.reflect.Type;
import java.util.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class IqsRestApiClient {

  public static final Logger LOGGER = LoggerFactory.getLogger(IqsRestApiClient.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RetryableRestConnector retryableRestConnector;
  @Autowired private Gson gson;

  @Timed(
      name = "notifyBackoutAdjustmentTimed",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "cancelLabel")
  @ExceptionCounted(
      name = "notifyBackoutAdjustmentCount",
      level1 = "uwms-receiving",
      level2 = "inventoryRestApiClient",
      level3 = "cancelLabel")
  public Optional<ItemBulkResponseDto> getItemDetailsFromItemNumber(
      Set<String> itemNumbers, String facilityNumber, HttpHeaders httpHeaders)
      throws IqsRestApiClientException {
    HttpHeaders iqsHttpHeaders = addHttpHeadersForItem(httpHeaders);
    String url = appConfig.getIqsBaseUrl() + IQS_DEFAULT_API_PATH;
    ItemRequestDto bulkRequest =
        ItemRequestDto.builder().ids(itemNumbers).facilityNumber(facilityNumber).build();

    LOGGER.info(
        "Get IQS item details URI = {}, requestHeaders = {}, request = {} ",
        url,
        iqsHttpHeaders,
        bulkRequest);
    ResponseEntity<String> response = null;
    try {
      response =
          retryableRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(bulkRequest), iqsHttpHeaders),
              String.class);
      LOGGER.info("Get IQS item details response: {} ", response);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new IqsRestApiClientException(
          ReceivingException.IQS_ITEM_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ReceivingException.IQS_SEARCH_ITEM_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new IqsRestApiClientException(
          ReceivingException.IQS_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.IQS_SEARCH_ITEM_ERROR_CODE);
    }
    Type type = new TypeToken<ItemBulkResponseDto>() {}.getType();
    return Optional.ofNullable(gson.fromJson(response.getBody(), type));
  }

  private HttpHeaders addHttpHeadersForItem(HttpHeaders httpHeaders) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(APQ_ID, appConfig.getReceivingItemApqId());
    headers.add(ACCEPT, ReceivingConstants.APPLICATION_JSON);
    headers.add(IQS_CONSUMER_ID_KEY, appConfig.getIqsReceivingConsumerId());
    headers.add(ReceivingConstants.CONTENT_TYPE, ReceivingConstants.APPLICATION_JSON);
    if (!StringUtils.hasLength(httpHeaders.getFirst(USER_ID_HEADER_KEY)))
      headers.add(USER_ID_HEADER_KEY, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    String correlationId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    headers.add(
        IQS_CORRELATION_ID_KEY,
        !StringUtils.hasLength(correlationId) ? correlationId : randomUUID().toString());
    headers.add(
        IQS_COUNTRY_CODE,
        !StringUtils.hasLength(getFacilityCountryCode())
            ? getFacilityCountryCode()
            : httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    return headers;
  }
}
