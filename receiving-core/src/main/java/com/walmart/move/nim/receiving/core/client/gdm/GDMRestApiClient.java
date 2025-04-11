package com.walmart.move.nim.receiving.core.client.gdm;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GDM_EVENT_POSTING;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ORG_UNIT_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SUBCENTER_ID_HEADER;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.*;
import com.walmart.move.nim.receiving.core.common.exception.GDMTrailerTemperatureServiceFailedException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.GdmDeliveryHistoryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.*;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for GDM Rest API
 *
 * @author v0k00fe
 */
@Component
public class GDMRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(GDMRestApiClient.class);

  private static final String GET_DELIVERY_V3 = "/api/deliveries/{deliveryNumber}";

  private static final String APPLICATION_VND_DELIVERYRESPONSE1_JSON =
      "application/vnd.deliveryresponse1+json";

  private static final String PUT_FINALIZE_PO =
      "/api/deliveries/{deliveryNumber}/purchase-orders/{poNumber}/finalize";

  private static final String PO_FINALIZE_CONTENT_TYPE_HEADER = "application/vnd.PoFinalize1+json";

  private static final String RECEIVING_GDM_EVENT_PATH = "/api/deliveries/receive/event";

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  @Autowired private AsyncPersister asyncPersister;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private RapidRelayerService rapidRelayerService;

  /**
   * Finds Delivery details by delivery number
   *
   * @param deliveryNumber
   * @return
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "getDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDelivery")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDelivery")
  public DeliveryWithOSDRResponse getDelivery(Long deliveryNumber, Map<String, Object> httpHeaders)
      throws GDMRestApiClientException {
    ResponseEntity<String> deliveryResponseEntity =
        getDeliveryResponseFromGDM(deliveryNumber, httpHeaders);
    return gson.fromJson(deliveryResponseEntity.getBody(), DeliveryWithOSDRResponse.class);
  }

  /**
   * Finds Delivery details by delivery number
   *
   * @param deliveryNumber
   * @return Delivery
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "getDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryWithDeliveryResponse")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryWithDeliveryResponse")
  public Delivery getDeliveryWithDeliveryResponse(Long deliveryNumber, HttpHeaders httpHeaders)
      throws GDMRestApiClientException {
    Map<String, Object> httpHeadersMap = new HashMap<>();
    httpHeadersMap.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM));
    httpHeadersMap.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.getFirst(ReceivingConstants.TENENT_COUNTRY_CODE));
    httpHeadersMap.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY));
    ResponseEntity<String> deliveryResponseEntity =
        getDeliveryResponseFromGDM(deliveryNumber, httpHeadersMap);
    return gson.fromJson(deliveryResponseEntity.getBody(), Delivery.class);
  }

  private ResponseEntity<String> getDeliveryResponseFromGDM(
      Long deliveryNumber, Map<String, Object> httpHeaders) throws GDMRestApiClientException {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString());
    requestHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    requestHeaders.set(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    requestHeaders.set(HttpHeaders.ACCEPT, APPLICATION_VND_DELIVERYRESPONSE1_JSON);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());

    String uri =
        ReceivingUtils.replacePathParams(appConfig.getGdmBaseUrl() + GET_DELIVERY_V3, pathParams)
            .toString();

    LOGGER.info("Get Delivery URI = {}", uri);

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestHeaders,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new GDMRestApiClientException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestHeaders,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new GDMRestApiClientException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    LOGGER.info(
        "For deliveryNumber={} got response={} from GDM",
        deliveryNumber,
        deliveryResponseEntity.getBody());
    return deliveryResponseEntity;
  }

  /**
   * Finalize Purchase Order in GDM given delivery Number and PO Number
   *
   * @param deliveryNumber
   * @param poNumber
   * @param finalizePORequestBody
   * @param httpHeaders
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "finalizePurchaseOrderTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "finalizePurchaseOrder")
  @ExceptionCounted(
      name = "finalizePurchaseOrderExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "finalizePurchaseOrder")
  public void finalizePurchaseOrder(
      Long deliveryNumber,
      String poNumber,
      @Valid FinalizePORequestBody finalizePORequestBody,
      Map<String, Object> httpHeaders)
      throws GDMRestApiClientException {

    HttpHeaders requestHeaders = getFinalizePoHttpHeaders(httpHeaders);
    String uri = getFinalizePoUrl(deliveryNumber, poNumber);
    String requestBody = getFinalizePoRequestBody(finalizePORequestBody);

    LOGGER.info("Finalize Purchase Order PUT URI = {}, requestBody : {} ", uri, requestBody);
    try {
      final HttpEntity<String> request = new HttpEntity<>(requestBody, requestHeaders);
      LOGGER.info("Finalize Purchase Order PUT URI = {}, request : {} ", uri, request);
      ResponseEntity<String> finalizePoResponseEntity =
          simpleRestConnector.exchange(uri, HttpMethod.PUT, request, String.class);
      LOGGER.info(
          "Response from GDM for uri : {}, response body {}",
          uri,
          finalizePoResponseEntity.getBody());
    } catch (RestClientResponseException e) {
      processFinalizePoException(uri, e);
    }
  }

  /**
   * Finalize Purchase Order in GDM given delivery Number and PO Number
   *
   * @param deliveryNumber
   * @param poNumber
   * @param finalizePORequestBody
   * @param httpHeaders
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "persistFinalizePoOsdrToGdmTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "postFinalizePoOsdrToGdm")
  @ExceptionCounted(
      name = "persistFinalizePoOsdrToGdmExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "persistFinalizePoOsdrToGdm")
  public void persistFinalizePoOsdrToGdm(
      Long deliveryNumber,
      String poNumber,
      @Valid FinalizePORequestBody finalizePORequestBody,
      Map<String, Object> httpHeaders)
      throws GDMRestApiClientException {

    HttpHeaders requestHeaders = getFinalizePoHttpHeaders(httpHeaders);
    String url = getFinalizePoUrl(deliveryNumber, poNumber);
    String requestBody = getFinalizePoRequestBody(finalizePORequestBody);

    asyncPersister.persistAsyncHttp(
        HttpMethod.PUT, url, requestBody, requestHeaders, RetryTargetFlow.GDM_PO_FINALIZE);
  }

  /**
   * Save trailer temperature for zones in GDM given delivery Number
   *
   * @param deliveryNumber
   * @param deliveryTrailerTemperatureBody
   * @param httpHeaders
   */
  @Timed(
      name = "saveZoneTrailerTemperatureTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "saveZoneTrailerTemperature")
  @ExceptionCounted(
      name = "saveZoneTrailerTemperatureExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "saveZoneTrailerTemperature")
  public ResponseEntity<GDMTemperatureResponse> saveZoneTrailerTemperature(
      Long deliveryNumber,
      GDMDeliveryTrailerTemperatureInfo deliveryTrailerTemperatureBody,
      HttpHeaders httpHeaders) {
    String uri = getTrailerTemperatureUrl(deliveryNumber);

    HttpHeaders requestHeaders = getTrailerTemperatureHttpHeaders(httpHeaders);
    String requestBody = getTrailerTemperatureRequestBody(deliveryTrailerTemperatureBody);

    HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, requestHeaders);

    try {
      ResponseEntity<GDMTemperatureResponse> trailerTempResponseEntity =
          simpleRestConnector.exchange(
              uri, HttpMethod.PUT, httpEntity, GDMTemperatureResponse.class);
      LOGGER.info(
          "Response from GDM for uri : {}, response body {}",
          uri,
          trailerTempResponseEntity.getBody());
      return trailerTempResponseEntity;
    } catch (Exception e) {
      throw new GDMTrailerTemperatureServiceFailedException(
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_CODE,
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_DESCRIPTION,
          ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_MESSAGE);
    }
  }

  private void processFinalizePoException(String uri, RestClientResponseException e)
      throws GDMRestApiClientException {
    LOGGER.error(
        "Response from GDM for uri : {}, response status code : {},  response body {}",
        uri,
        e.getRawStatusCode(),
        e.getResponseBodyAsString());

    if (400 == e.getRawStatusCode()) {
      JsonObject errorResponse = gson.fromJson(e.getResponseBodyAsString(), JsonObject.class);
      throw new GDMRestApiClientException(
          errorResponse.get("description").getAsJsonArray().get(0).getAsString(),
          HttpStatus.valueOf(e.getRawStatusCode()),
          errorResponse.get("errorCode").getAsString());
    }

    throw new GDMRestApiClientException(
        e.getResponseBodyAsString(), HttpStatus.valueOf(e.getRawStatusCode()));
  }

  private String getFinalizePoRequestBody(FinalizePORequestBody finalizePORequestBody) {
    return new GsonBuilder()
        .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
        .create()
        .toJson(finalizePORequestBody);
  }

  private HttpHeaders getFinalizePoHttpHeaders(Map<String, Object> httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(
        ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    requestHeaders.add(
        ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    requestHeaders.add(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, PO_FINALIZE_CONTENT_TYPE_HEADER);
    if (nonNull(httpHeaders.get(SUBCENTER_ID_HEADER)))
      requestHeaders.add(SUBCENTER_ID_HEADER, httpHeaders.get(SUBCENTER_ID_HEADER).toString());
    if (httpHeaders.containsKey(ORG_UNIT_ID_HEADER)
        && isNotBlank(httpHeaders.get(ORG_UNIT_ID_HEADER).toString()))
      requestHeaders.add(ORG_UNIT_ID_HEADER, httpHeaders.get(ORG_UNIT_ID_HEADER).toString());
    return requestHeaders;
  }

  private String getFinalizePoUrl(Long deliveryNumber, String poNumber) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());
    pathParams.put(ReceivingConstants.PURCHASE_ORDER_NUMBER, poNumber);
    return ReceivingUtils.replacePathParams(appConfig.getGdmBaseUrl() + PUT_FINALIZE_PO, pathParams)
        .toString();
  }

  private HttpHeaders getTrailerTemperatureHttpHeaders(HttpHeaders httpHeaders) {
    httpHeaders.remove(HttpHeaders.CONTENT_TYPE);
    httpHeaders.add(
        HttpHeaders.CONTENT_TYPE,
        ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_CONTENT_TYPE_HEADER);
    return httpHeaders;
  }

  private String getTrailerTemperatureUrl(Long deliveryNumber) {
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());
    return ReceivingUtils.replacePathParams(
            appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_DELIVERY_TRAILER_TEMPERATURE_URI,
            pathParams)
        .toString();
  }

  private String getTrailerTemperatureRequestBody(
      GDMDeliveryTrailerTemperatureInfo trailerTemperatureRequestBody) {
    return new GsonBuilder()
        .registerTypeAdapter(Date.class, new GsonUTCDateAdapter())
        .create()
        .toJson(trailerTemperatureRequestBody);
  }

  /**
   * Get TrailerZone temperature by delivery number
   *
   * @param deliveryNumber
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "getTrailerZoneTemperatureTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getTrailerZoneTemperature")
  @ExceptionCounted(
      name = "getTrailerZoneTemperatureExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getTrailerZoneTemperature")
  public GDMDeliveryTrailerTemperatureInfo buildTrailerZoneTemperatureResponse(
      Long deliveryNumber, HttpHeaders headers) throws ReceivingException {

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());

    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GET_ZONETEMPERATURE_V3, pathParams)
            .toString();

    LOGGER.info("Get TrailerZone URI = {}", uri);

    ResponseEntity<String> temperatureZoneResponseEntity = null;
    try {
      temperatureZoneResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          String.format(
              ReceivingException.TRAILER_TEMPERATURE_NOT_FOUND_ERROR_MESSAGE, deliveryNumber),
          ReceivingException.DELIVERY_NOT_FOUND);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          ReceivingException.GDM_SERVICE_DOWN, ReceivingException.DELIVERY_NOT_FOUND);
    }
    LOGGER.info(
        "For deliveryNumber={} got response={} from GDM",
        deliveryNumber,
        temperatureZoneResponseEntity.getBody());

    return gson.fromJson(
        temperatureZoneResponseEntity.getBody(), GDMDeliveryTrailerTemperatureInfo.class);
  }

  /**
   * @param deliveryNumber
   * @param headers
   * @return trailerTempZonesRecorded
   * @throws ReceivingException
   */
  public Integer getTrailerTempZonesRecorded(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {
    Integer trailerTempZonesRecorded = 0;
    GDMDeliveryTrailerTemperatureInfo gdmDeliveryTrailerTemperatureInfo =
        buildTrailerZoneTemperatureResponse(deliveryNumber, headers);

    if (gdmDeliveryTrailerTemperatureInfo != null
        && !CollectionUtils.isEmpty(gdmDeliveryTrailerTemperatureInfo.getZones())) {
      trailerTempZonesRecorded = gdmDeliveryTrailerTemperatureInfo.getZones().size();
    }

    return trailerTempZonesRecorded;
  }

  /**
   * Finds Delivery history details by delivery number
   *
   * @param deliveryNumber
   * @return
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "getDeliveryHistoryTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryHistory")
  @ExceptionCounted(
      name = "getDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryHistory")
  public GdmDeliveryHistoryResponse getDeliveryHistory(Long deliveryNumber, HttpHeaders httpHeaders)
      throws GDMRestApiClientException {
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());
    String uri =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_DELIVERY_HISTORY, pathParams)
            .toString();
    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      LOGGER.info("cId={}, Get Delivery history URI = {}", cId, uri);
      deliveryResponseEntity =
          simpleRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info(
          "cId={}, For deliveryNumber={} got response={} from GDM",
          cId,
          deliveryNumber,
          deliveryResponseEntity);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          cId,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new GDMRestApiClientException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          cId,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new GDMRestApiClientException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE);
    }
    return gson.fromJson(deliveryResponseEntity.getBody(), GdmDeliveryHistoryResponse.class);
  }

  /**
   * Generic Receiving events to GDM for History
   *
   * @param receiveEventRequestBody
   * @param httpHeaders
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "receivingToGDMEventTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "receivingToGDMEvent")
  @ExceptionCounted(
      name = "receivingToGDMEventExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "receivingToGDMEvent")
  public void receivingToGDMEvent(
      @Valid ReceiveEventRequestBody receiveEventRequestBody, Map<String, Object> httpHeaders)
      throws GDMRestApiClientException {

    HttpHeaders requestHeaders = getReceivingToGDMEventHeader(httpHeaders);
    String url = appConfig.getGdmBaseUrl() + RECEIVING_GDM_EVENT_PATH;
    String cId = httpHeaders.get(CORRELATION_ID_HEADER_KEY).toString();

    try {
      LOGGER.info(
          "Calling GDM ReceiveEvent Request Body = {} correlation-id {}",
          receiveEventRequestBody,
          cId);
      if (!Objects.isNull(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM))
          && tenantSpecificConfigReader.getConfiguredFeatureFlag(
              String.valueOf(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)),
              ReceivingConstants.OUTBOX_PATTERN_ENABLED,
              false)) {
        requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
        rapidRelayerService.produceHttpMessage(
            GDM_EVENT_POSTING,
            gson.toJson(receiveEventRequestBody),
            ReceivingUtils.convertHttpHeadersToHashMap(requestHeaders));
      } else {
        ResponseEntity<String> response =
            simpleRestConnector.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(receiveEventRequestBody, requestHeaders),
                String.class);
        LOGGER.info("Response GDM ReceiveEvent, correlation-id={} Response={}", cId, response);
      }
    } catch (Exception e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          cId,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new GDMRestApiClientException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GDM_RECEIVE_EVENT_ERROR_CODE);
    }
  }

  private HttpHeaders getReceivingToGDMEventHeader(Map<String, Object> httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(
        ReceivingConstants.TENENT_FACLITYNUM, TenantContext.getFacilityNum().toString());
    requestHeaders.add(
        ReceivingConstants.TENENT_COUNTRY_CODE, TenantContext.getFacilityCountryCode());
    requestHeaders.add(
        ReceivingConstants.USER_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    requestHeaders.add(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    if (httpHeaders.containsKey(ORG_UNIT_ID_HEADER)
        && isNotBlank(httpHeaders.get(ORG_UNIT_ID_HEADER).toString()))
      requestHeaders.add(ORG_UNIT_ID_HEADER, httpHeaders.get(ORG_UNIT_ID_HEADER).toString());
    return requestHeaders;
  }

  /**
   * Finds Delivery history details by delivery number
   *
   * @param poNumber
   * @return
   * @throws GDMRestApiClientException
   */
  @Timed(
      name = "getPurchaseOrderTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getPurchaseOrder")
  @ExceptionCounted(
      name = "getPurchaseOrderExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getPurchaseOrder")
  public PurchaseOrder getPurchaseOrder(String poNumber) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(ReceivingConstants.ACCEPT, ReceivingConstants.GDM_PURCHASE_ORDER_ACCEPT_TYPE);
    String uri = appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_GET_PURCHASE_ORDER + poNumber;
    LOGGER.info("Get Purchase Order URI = {}", uri);
    ResponseEntity<String> purchaseOrderResponseEntity = null;
    try {
      purchaseOrderResponseEntity =
          simpleRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info(
          "For poNumber={} got response={} from GDM", poNumber, purchaseOrderResponseEntity);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE,
          ReceivingException.PURCHASE_ORDER_NOT_FOUND);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE, ReceivingException.GDM_SERVICE_DOWN);
    }
    return gson.fromJson(purchaseOrderResponseEntity.getBody(), PurchaseOrder.class);
  }
  /**
   * Get All Re-Receiving Containers from GDM which has scanned GTIN and Status as Pending Receive!
   *
   * @param gtin -> the upc which was scanned in receiving UI
   * @param httpHeaders
   */
  @Timed(
      name = "getReReceivingLPNTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getReReceivingContainerResponseFromGDM")
  @ExceptionCounted(
      name = "getReReceivingContainerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getReReceivingContainerResponseFromGDM")
  public String getReReceivingContainerResponseFromGDM(String gtin, HttpHeaders httpHeaders) {
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.UPC_NUMBER, gtin);
    httpHeaders.add(
        HttpHeaders.ACCEPT, ReceivingConstants.GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v3);
    Map<String, String> queryParameters = new HashMap<>();
    queryParameters.put(
        ReceivingConstants.RECEIVING_STATUS, ReceivingConstants.RECEIVING_STATUS_OPEN);
    String uri =
        ReceivingUtils.replacePathParamsAndQueryParams(
                appConfig.getGdmBaseUrl() + ReceivingConstants.GDM_LPN_LIST_URI,
                pathParams,
                queryParameters)
            .toString();
    ResponseEntity<String> reReceivingContainerListResponseEntity = null;
    try {
      LOGGER.info("correlationId={}, Get ReReceivingContainerList URI = {}", cId, uri);
      reReceivingContainerListResponseEntity =
          simpleRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOGGER.info("Got response from GDM = {}", reReceivingContainerListResponseEntity);
      if (reReceivingContainerListResponseEntity.getStatusCode() == HttpStatus.NO_CONTENT
          || reReceivingContainerListResponseEntity.getStatusCode() == HttpStatus.PARTIAL_CONTENT)
        return null;
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.NOT_FOUND) return null;
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(ex));
      throw new ReceivingDataNotFoundException(
          ReceivingException.GDM_SERVICE_DOWN, ReceivingException.GDM_GET_LPN_LIST);
    } catch (Exception e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingDataNotFoundException(
          ReceivingException.GDM_SERVICE_DOWN, ReceivingException.GDM_GET_LPN_LIST);
    }
    return reReceivingContainerListResponseEntity.getBody();
  }

  @Counted(
      name = "getDeliveryDocumentsByItemNumberHitCount",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "getDeliveryDocumentsByItemNumber")
  @Timed(
      name = "getDeliveryDocumentsByItemNumberTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryDocumentsByItemNumber")
  @ExceptionCounted(
      name = "getDeliveryDocumentsByItemNumberExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "getDeliveryDocumentsByItemNumber")
  public String getDeliveryDocumentsByItemNumber(
      String deliveryNumber, Integer itemNumber, HttpHeaders headers) throws ReceivingException {
    GdmError gdmError;
    ResponseEntity<String> response;
    TenantContext.get().setAtlasRcvGdmGetDocLineStart(System.currentTimeMillis());
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    pathParams.put(ReceivingConstants.ITEM_NUMBER, String.valueOf(itemNumber));
    headers.set(HttpHeaders.ACCEPT, ReceivingConstants.GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE);
    String getDeliveryDocumentsUrl =
        ReceivingUtils.replacePathParams(
                appConfig.getGdmBaseUrl()
                    + ReceivingConstants.GDM_FETCH_DELIVERY_DOC_BY_ITEM_AND_DELIVERY_URI,
                pathParams)
            .toString();
    try {
      response =
          retryableRestConnector.exchange(
              getDeliveryDocumentsUrl, HttpMethod.GET, new HttpEntity<>(headers), String.class);
      if (isNull(response) || org.apache.commons.lang3.StringUtils.isEmpty(response.getBody())) {
        LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, getDeliveryDocumentsUrl, "", "");
        gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(gdmError.getErrorMessage())
                .errorCode(gdmError.getErrorCode())
                .errorHeader(gdmError.getLocalisedErrorHeader())
                .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.NOT_FOUND)
            .errorResponse(errorResponse)
            .build();
      } else {
        LOGGER.info(
            ReceivingConstants.RESTUTILS_INFO_MESSAGE,
            getDeliveryDocumentsUrl,
            "",
            response.getBody());
      }
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          getDeliveryDocumentsUrl,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(gdmError.getErrorMessage())
              .errorCode(gdmError.getErrorCode())
              .errorHeader(gdmError.getLocalisedErrorHeader())
              .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.NOT_FOUND)
          .errorResponse(errorResponse)
          .build();

    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          getDeliveryDocumentsUrl,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);

      throw new GDMServiceUnavailableException(
          gdmError.getErrorMessage(), gdmError.getErrorCode(), gdmError.getErrorHeader());
    }
    TenantContext.get().setAtlasRcvGdmGetDocLineEnd(System.currentTimeMillis());
    return response.getBody();
  }

  /**
   * Gets delivery details based on URL in delivery message and retries in case of failure
   *
   * @param url url which needs to be hit for the delivery document
   * @param deliveryNumber the delivery number
   * @return {@link DeliveryDetails} delivery details based in v2 contract
   */
  @Timed(
      name = "getDeliveryDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  @ExceptionCounted(
      name = "getDeliveryDetailsExceptionCount",
      cause = GDMServiceUnavailableException.class,
      level1 = "uwms-receiving",
      level2 = "deliveryService",
      level3 = "deliveryDocumentDetails")
  public DeliveryDetails getDeliveryDetails(String url, Long deliveryNumber)
      throws ReceivingException {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();

    ResponseEntity<String> deliveryResponseEntity = null;
    try {
      deliveryResponseEntity =
          retryableRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ExceptionCodes.DELIVERY_NOT_FOUND);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          url,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          ReceivingException.GDM_SERVICE_DOWN,
          HttpStatus.SERVICE_UNAVAILABLE,
          ExceptionCodes.GDM_NOT_ACCESSIBLE);
    }
    if (Objects.isNull(deliveryResponseEntity)
        || org.apache.commons.lang3.StringUtils.isEmpty(deliveryResponseEntity.getBody())) {
      LOGGER.error(ReceivingConstants.RESTUTILS_INFO_MESSAGE, url, "", "");
      throw new ReceivingException(
          ReceivingException.DELIVERY_NOT_FOUND,
          HttpStatus.NOT_FOUND,
          ExceptionCodes.DELIVERY_NOT_FOUND);
    }
    DeliveryDetails deliveryDetails =
        JacksonParser.convertJsonToObject(deliveryResponseEntity.getBody(), DeliveryDetails.class);
    LOGGER.info(
        ReceivingConstants.RESTUTILS_INFO_MESSAGE,
        url,
        deliveryNumber,
        deliveryResponseEntity.getBody());
    return deliveryDetails;
  }
}
