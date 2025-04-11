package com.walmart.move.nim.receiving.core.client.hawkeye;

import static org.apache.commons.lang3.StringUtils.isBlank;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.client.hawkeye.model.*;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import io.strati.txnmarking.libs.opentelemetry.api.internal.StringUtils;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class HawkeyeRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(HawkeyeRestApiClient.class);

  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  /**
   * Get History Deliveries with given time from HawkEye
   *
   * @param deliverySearchRequest
   * @param headers
   * @return Optional<List<String>>
   * @throws ReceivingException
   */
  @Timed(
      name = "getHistoryDeliveriesFromHawkeyeTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "getHistoryDeliveriesFromHawkeye")
  @ExceptionCounted(
      name = "getHistoryDeliveriesFromHawkeyeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "getHistoryDeliveriesFromHawkeye")
  public Optional<List<String>> getHistoryDeliveriesFromHawkeye(
      DeliverySearchRequest deliverySearchRequest, HttpHeaders headers)
      throws ReceivingBadDataException {
    Map<String, String> queryParams = prepareQueryParamsForDeliverySearch(deliverySearchRequest);
    HttpHeaders requestHeaders = ReceivingUtils.getForwardableHttpHeaders(headers);
    String url = prepareFindDeliveriesURL(queryParams, headers);
    LOGGER.info("Hawkeye Url is:{}", url);
    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          retryableRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == 500) {
        LOGGER.error(
            "Hawkeye responseCode={} & response={}",
            e.getRawStatusCode(),
            e.getResponseBodyAsString());
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_RECEIVE_ERROR,
            ReceivingConstants.HAWKEYE_RECEIVE_ERROR_DESCRIPTION);
      } else if (e.getRawStatusCode() == 400) {
        LOGGER.error(
            "Hawkeye responseCode={} & response={}",
            e.getRawStatusCode(),
            e.getResponseBodyAsString());
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_RECEIVE_INVALID_INPUT,
            ReceivingConstants.HAWKEYE_RECEIVE_INVALID_INPUT_DESCRIPTION);
      }
    }
    if (deliveryResponseEntity != null) {
      LOGGER.info(
          "Get deliveries with request {} got response={}",
          deliverySearchRequest,
          deliveryResponseEntity.getBody());
      return Optional.ofNullable(
          ((Map<String, List<String>>)
                  gson.fromJson(
                      deliveryResponseEntity.getBody(),
                      new TypeToken<Map<String, List<String>>>() {}.getType()))
              .get("groupNbrList"));
    } else {
      return Optional.empty();
    }
  }

  /**
   * Label Update to Hawkeye - This method can be called to update the status(VOID, DOWNLOADED) of
   * the Label to Hawkeye
   *
   * @param labelUpdateRequests
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "labelUpdateToHawkeyeTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "labelUpdateToHawkeye")
  @ExceptionCounted(
      name = "labelUpdateToHawkeyeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "labelUpdateToHawkeye")
  public void labelUpdateToHawkeye(
      List<LabelUpdateRequest> labelUpdateRequests, HttpHeaders headers)
      throws ReceivingBadDataException, ReceivingInternalException {

    HttpHeaders requestHeaders = ReceivingUtils.getHawkeyeForwardableHeaders(headers);
    String labelUpdateUrl = getHawkeyeUrlByTenant() + ReceivingConstants.LABEL_UPDATE_URI;

    LOGGER.info(
        "Label update to Hawkeye, Url: [{}], payload: [{}] headers: [{}]",
        labelUpdateUrl,
        labelUpdateRequests,
        requestHeaders);
    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              labelUpdateUrl,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(labelUpdateRequests), requestHeaders),
              String.class);
      LOGGER.info(
          "Received response: {} from Hawkeye for the label update api call",
          response.getStatusCodeValue());

    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_LABEL_DETAILS,
            ReceivingConstants.LABEL_UPDATE_INVALID_REQUEST_DESCRIPTION,
            e.getResponseBodyAsString());
      } else if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.CONFLICT_LABEL_DETAILS,
            ReceivingConstants.LABEL_UPDATE_CONFLICT_DESCRIPTION,
            e.getResponseBodyAsString());
      } else if (e.getRawStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.LABEL_DETAILS_ERROR,
            ReceivingConstants.LABEL_UPDATE_INTERNAL_SERVER_ERROR_DESCRIPTION,
            e.getResponseBodyAsString());
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
  }

  /**
   * Item update to Hawkeye to set catalogGtin or rejectReason
   *
   * @param hawkeyeItemUpdateRequest
   * @param httpHeaders
   */
  @Async
  public void itemUpdateToHawkeye(
      HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest, HttpHeaders httpHeaders) {
    TenantContext.setFacilityCountryCode(
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    TenantContext.setFacilityNum(
        Integer.parseInt(httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
    try {
      long startTime = System.currentTimeMillis();
      sendItemUpdateToHawkeye(hawkeyeItemUpdateRequest, httpHeaders);
      LOGGER.info(
          "Time taken for hawkeyeRestApiClient is {} ms", System.currentTimeMillis() - startTime);
    } catch (ReceivingBadDataException | ReceivingInternalException e) {
      LOGGER.error(
          "Item update failed for request with error code {} and description {}",
          e.getErrorCode(),
          e.getDescription());
    }
  }

  /**
   * Item Update to Hawkeye - This method is called when user updates (1) Catalog UPC for an item
   * (2) Verifies regulated Items (3) Item Overrides
   *
   * @param hawkeyeItemUpdateRequest
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "sendItemUpdateToHawkeyeTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "sendItemUpdateToHawkeye")
  @ExceptionCounted(
      name = "sendItemUpdateToHawkeyeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "sendItemUpdateToHawkeye")
  public void sendItemUpdateToHawkeye(
      HawkeyeItemUpdateRequest hawkeyeItemUpdateRequest, HttpHeaders headers)
      throws ReceivingBadDataException, ReceivingInternalException {

    HttpHeaders requestHeaders = ReceivingUtils.getHawkeyeForwardableHeaders(headers);
    String itemUpdateUrl = getHawkeyeUrlByTenant() + ReceivingConstants.HAWKEYE_ITEM_UPDATE_URI;

    LOGGER.info(
        "item update to Hawkeye, Url: [{}], payload: [{}] headers: [{}]",
        itemUpdateUrl,
        hawkeyeItemUpdateRequest,
        requestHeaders);
    try {
      ResponseEntity<String> updateItemResponse =
          retryableRestConnector.exchange(
              itemUpdateUrl,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(hawkeyeItemUpdateRequest), requestHeaders),
              String.class);
      LOGGER.info(
          "Received response: {} from Hawkeye for the item update api call",
          updateItemResponse.getStatusCodeValue());

    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          itemUpdateUrl,
          hawkeyeItemUpdateRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.HAWKEYE_ITEM_UPDATE_FAILED,
          ReceivingConstants.HAWKEYE_ITEM_UPDATE_FAILED,
          hawkeyeItemUpdateRequest);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          itemUpdateUrl,
          hawkeyeItemUpdateRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
  }

  protected String prepareFindDeliveriesURL(
      Map<String, String> queryParams, HttpHeaders requestHeaders) {
    String baseUrl = getHawkeyeUrlByTenant() + ReceivingConstants.HAWKEYE_GET_DELIVERIES_URI;
    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(baseUrl, null, queryParams).toString();
    LOGGER.info(
        "Get deliveries URL from Hawkeye = {}, search criteria params = {}", url, requestHeaders);
    return url;
  }

  protected String getHawkeyeUrlByTenant() {
    String baseUrl = null;
    try {
      String siteId =
          org.apache.commons.lang3.StringUtils.leftPad(
              TenantContext.getFacilityNum().toString(), 5, "0");
      baseUrl =
          String.format(getExternalServiceBaseUrlByTenant(appConfig.getHawkeyeBaseUrl()), siteId);
    } catch (Exception e) {
      if (StringUtils.isNullOrEmpty(baseUrl)) {
        throw new ReceivingInternalException(
            ExceptionCodes.CONFIGURATION_ERROR,
            String.format(
                ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
      }
    }
    return baseUrl;
  }

  protected String getExternalServiceBaseUrlByTenant(String baseUrl) {
    if (Objects.nonNull(baseUrl)) {
      JsonObject tenantBasedUrlJson = gson.fromJson(baseUrl, JsonObject.class);

      JsonElement serviceUrlJsonElement =
          tenantBasedUrlJson.get(TenantContext.getFacilityNum().toString());
      if (Objects.nonNull(serviceUrlJsonElement)) {
        return serviceUrlJsonElement.getAsString();
      } else {
        throw new ReceivingInternalException(
            ExceptionCodes.CONFIGURATION_ERROR,
            String.format(
                ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
      }
    } else {
      LOGGER.error("Base url is empty in getExternalServiceBaseUrlByTenant()");
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(
              ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
    }
  }

  private Map<String, String> prepareQueryParamsForDeliverySearch(
      DeliverySearchRequest deliverySearchRequest) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.UPC.toLowerCase(), deliverySearchRequest.getUpc());
    if (Objects.nonNull(deliverySearchRequest.getLocationId())) {
      pathParams.put(ReceivingConstants.LOCATION_ID, deliverySearchRequest.getLocationId());
    }
    if (Objects.nonNull(deliverySearchRequest.getFromDate())) {
      pathParams.put(
          ReceivingConstants.DELIVERY_SEARCH_PARAM_FROM_DATE,
          formatToHawkeyeDate(deliverySearchRequest.getFromDate(), Boolean.TRUE));
    }
    if (Objects.nonNull(deliverySearchRequest.getToDate())) {
      pathParams.put(
          ReceivingConstants.DELIVERY_SEARCH_PARAM_TO_DATE,
          formatToHawkeyeDate(deliverySearchRequest.getToDate(), Boolean.FALSE));
    }
    return pathParams;
  }

  /**
   * This method is to convert the Given zone datetime to UTC dateTime
   *
   * @param dateString
   * @param isFromDate
   * @return
   */
  public String formatToHawkeyeDate(String dateString, Boolean isFromDate) {
    try {
      if (ReceivingUtils.isValidDeliverySearchClientTimeFormat(dateString)) {
        return convertDeliverySearchClientTimeFormatToUTC(dateString, isFromDate);
      } else {
        SimpleDateFormat inputFormat = new SimpleDateFormat(ReceivingConstants.UTC_DATE_FORMAT);
        inputFormat.setTimeZone(TimeZone.getTimeZone(ReceivingConstants.UTC_TIME_ZONE));
        Date inputDate = inputFormat.parse(dateString);
        return ReceivingUtils.dateInProvidedPatternAndTimeZone(
            inputDate, ReceivingConstants.HAWKEYE_DATE_FORMAT, ReceivingConstants.UTC_TIME_ZONE);
      }
    } catch (Exception e) {
      LOGGER.error("Error converting zone date and time to UTC date and time");
      throw new ReceivingBadDataException(
          ExceptionCodes.HAWKEYE_RECEIVE_INVALID_INPUT,
          ReceivingConstants.HAWKEYE_RECEIVE_INVALID_INPUT_DESCRIPTION);
    }
  }

  /**
   * This method is to convert Date with DELIVERY_SEARCH_CLIENT_TIME_FORMAT to HAWKEYE_DATE_FORMAT
   *
   * @param dateString
   * @param isFromDate
   * @return
   * @throws ParseException
   */
  private String convertDeliverySearchClientTimeFormatToUTC(String dateString, Boolean isFromDate)
      throws ParseException {
    String dcTimeZone = tenantSpecificConfigReader.getDCTimeZone(TenantContext.getFacilityNum());
    dcTimeZone = isBlank(dcTimeZone) ? ReceivingConstants.UTC_TIME_ZONE : dcTimeZone;
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeZone(TimeZone.getTimeZone(dcTimeZone));
    Date inputDate =
        ReceivingUtils.parseDateToProvidedTimeZone(
            dateString, ReceivingConstants.DELIVERY_SEARCH_CLIENT_TIME_FORMAT, dcTimeZone);
    if (Boolean.TRUE.equals(isFromDate)) {
      calendar.setTime(inputDate);
      calendar.set(Calendar.MILLISECOND, ReceivingConstants.MILLISECONDS_START_OF_DAY);
      inputDate = calendar.getTime();
    } else {
      calendar.setTime(inputDate);
      calendar.set(Calendar.HOUR_OF_DAY, ReceivingConstants.HOUR_END_OF_DAY);
      calendar.set(Calendar.MINUTE, ReceivingConstants.MINUTE_END_OF_DAY);
      calendar.set(Calendar.SECOND, ReceivingConstants.SECOND_END_OF_DAY);
      calendar.set(Calendar.MILLISECOND, ReceivingConstants.MILLISECONDS_END_OF_DAY);
      inputDate = calendar.getTime();
    }
    return ReceivingUtils.dateInProvidedPatternAndTimeZone(
        inputDate, ReceivingConstants.HAWKEYE_DATE_FORMAT, ReceivingConstants.UTC_TIME_ZONE);
  }

  /**
   * Label Readiness to Hawkeye - This method can be called to check whether the location is ready
   * for linking new delivery
   *
   * @param labelReadinessRequest
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "checkLabelReadinessTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "checkLabelReadiness")
  @ExceptionCounted(
      name = "checkLabelReadinessExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "checkLabelReadiness")
  public ResponseEntity<String> checkLabelGroupReadinessStatus(
      LabelReadinessRequest labelReadinessRequest, HttpHeaders headers)
      throws ReceivingBadDataException, ReceivingInternalException {
    Map<String, String> queryParams =
        prepareQueryParamsForLabelGroupReadinessStatus(labelReadinessRequest);
    String url = prepareLabelReadinessURL(queryParams, headers);
    LOGGER.info("Label Readiness Url: [{}], headers: [{}]", url, headers);
    ResponseEntity<String> response = null;
    try {
      response =
          retryableRestConnector.exchange(
              url,
              HttpMethod.GET,
              new HttpEntity<>(gson.toJson(labelReadinessRequest), headers),
              String.class);
      LOGGER.info("Received response: {} from label readiness", response.getStatusCodeValue());

    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_LABEL_DETAILS,
            ReceivingConstants.LABEL_READINESS_INVALID_REQUEST_DESCRIPTION,
            e.getResponseBodyAsString());
      } else if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        return new ResponseEntity<>(
            e.getResponseBodyAsString(), HttpStatus.valueOf(e.getRawStatusCode()));
      } else if (e.getRawStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.LABEL_DETAILS_ERROR,
            ReceivingConstants.LABEL_READINESS_INTERNAL_SERVER_ERROR_DESCRIPTION,
            e.getResponseBodyAsString());
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
    if (response != null) {
      LOGGER.info(
          "label readiness request {} got response= {}", labelReadinessRequest, response.getBody());
      return response;
    } else {
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_LABEL_DETAILS,
          ReceivingConstants.LABEL_READINESS_INVALID_REQUEST_DESCRIPTION);
    }
  }

  private static Map<String, String> prepareQueryParamsForLabelGroupReadinessStatus(
      LabelReadinessRequest labelReadinessRequest) {
    Map<String, String> queryParams = new HashMap<>();
    if (!Objects.isNull(labelReadinessRequest.getGroupNbr())) {
      queryParams.put(ReceivingConstants.GROUP_NBR, labelReadinessRequest.getGroupNbr());
    }
    if (!Objects.isNull(labelReadinessRequest.getLocationId())) {
      queryParams.put(ReceivingConstants.LOCATION_ID, labelReadinessRequest.getLocationId());
    }
    if (!Objects.isNull(labelReadinessRequest.getGroupType())) {
      queryParams.put(ReceivingConstants.GROUP_TYPE, labelReadinessRequest.getGroupType());
    }
    return queryParams;
  }

  protected String prepareLabelReadinessURL(Map<String, String> queryParams, HttpHeaders headers) {
    String baseUrl = getHawkeyeUrlByTenant() + ReceivingConstants.HAWKEYE_LABEL_READINESS_URL;
    String url =
        ReceivingUtils.replacePathParamsAndQueryParams(baseUrl, null, queryParams).toString();
    LOGGER.info("Get Label Readiness URL from Hawkeye :[{}],headers :[{}]", url, headers);
    return url;
  }

  /**
   * Fetch Lpns from Hawkeye - This method is called to fetch next available lpn based on quantity
   * and store number(optional)
   *
   * @param hawkeyeGetLpnsRequest
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "getLpnsFromHawkeyeTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "getLpnsFromHawkeyeHawkeye")
  @ExceptionCounted(
      name = "getLpnsFromHawkeyeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "getLpnsFromHawkeye")
  public Optional<List<String>> getLpnsFromHawkeye(
      HawkeyeGetLpnsRequest hawkeyeGetLpnsRequest, HttpHeaders headers)
      throws ReceivingBadDataException, ReceivingInternalException {

    HttpHeaders requestHeaders = ReceivingUtils.getHawkeyeForwardableHeaders(headers);
    String getLpnsUrl = getHawkeyeUrlByTenant() + ReceivingConstants.HAWKEYE_FETCH_LPNS_URI;
    ResponseEntity<String> response = null;
    LOGGER.info(
        "Get Lpns from Hawkeye, Url: [{}], payload: [{}] headers: [{}]",
        getLpnsUrl,
        hawkeyeGetLpnsRequest,
        requestHeaders);
    try {
      response =
          retryableRestConnector.exchange(
              getLpnsUrl,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(hawkeyeGetLpnsRequest), requestHeaders),
              String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          getLpnsUrl,
          hawkeyeGetLpnsRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()
          && e.getResponseBodyAsString()
              .contains(ReceivingConstants.HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND)) {
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED,
            ReceivingConstants.HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND_DESCRIPTION,
            hawkeyeGetLpnsRequest);
      }
      throw new ReceivingBadDataException(
          ExceptionCodes.HAWKEYE_FETCH_LPNS_FAILED,
          ReceivingConstants.HAWKEYE_FETCH_LPNS_FAILED,
          hawkeyeGetLpnsRequest);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          getLpnsUrl,
          hawkeyeGetLpnsRequest,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
    if (response != null) {
      LOGGER.info(
          "Received response: {} from Hawkeye for the get lpns api call", response.getBody());
      return Optional.ofNullable(
          gson.fromJson(response.getBody(), new TypeToken<List<String>>() {}.getType()));
    }
    return Optional.empty();
  }
  /**
   * Label Group Update to Hawkeye - This method can be called for linking new delivery
   *
   * @param hawkeyeLabelGroupUpdateRequest
   * @param deliveryNumber
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "sendDeliveryLinkTimed",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "endDeliveryLink")
  @ExceptionCounted(
      name = "endDeliveryLinkExceptionCount",
      level1 = "uwms-receiving",
      level2 = "hawkeyeApiClient",
      level3 = "endDeliveryLink")
  public ResponseEntity<String> sendLabelGroupUpdateToHawkeye(
      HawkeyeLabelGroupUpdateRequest hawkeyeLabelGroupUpdateRequest,
      Long deliveryNumber,
      HttpHeaders headers)
      throws ReceivingBadDataException, ReceivingInternalException {
    String url = prepareLabelGroupUpdateUrl(deliveryNumber, headers);
    ResponseEntity<String> response = null;
    try {
      response =
          retryableRestConnector.exchange(
              url,
              HttpMethod.POST,
              new HttpEntity<>(gson.toJson(hawkeyeLabelGroupUpdateRequest), headers),
              String.class);
      LOGGER.info(
          "Received response: {} from Hawkeye for Label Group Update",
          response.getStatusCodeValue());
    } catch (RestClientResponseException e) {
      if (e.getRawStatusCode() == HttpStatus.BAD_REQUEST.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_LABEL_GROUP_UPDATE_FAILED,
            ReceivingConstants.HAWKEYE_LABEL_GROUP_UPDATE_FAILED,
            hawkeyeLabelGroupUpdateRequest);
      } else if (e.getRawStatusCode() == HttpStatus.CONFLICT.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_LABEL_GROUP_UPDATE_CONFLICT,
            ReceivingConstants.HAWKEYE_LABEL_GROUP_UPDATE_CONFLICT,
            e.getResponseBodyAsString());
      } else if (e.getRawStatusCode() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
        throw new ReceivingBadDataException(
            ExceptionCodes.HAWKEYE_LABEL_GROUP_UPDATE_ERROR,
            HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
            e.getResponseBodyAsString());
      }
    } catch (ResourceAccessException e) {
      throw new ReceivingInternalException(
          ExceptionCodes.HAWK_EYE_ERROR, ReceivingConstants.SYSTEM_BUSY);
    }
    return response;
  }

  private String prepareLabelGroupUpdateUrl(Long deliveryNumber, HttpHeaders headers) {
    String url = getHawkeyeUrlByTenant() + ReceivingConstants.HAWKEYE_LABEL_GROUP_UPDATE_URL;
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, String.valueOf(deliveryNumber));
    url = ReceivingUtils.replacePathParamsAndQueryParams(url, pathParams, null).toString();
    LOGGER.info("Send Label Group Update Link URL to Hawkeye :[{}],headers :[{}]", url, headers);
    return url;
  }
}
