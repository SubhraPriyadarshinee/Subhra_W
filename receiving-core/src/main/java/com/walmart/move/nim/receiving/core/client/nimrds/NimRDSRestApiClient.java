package com.walmart.move.nim.receiving.core.client.nimrds;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_REINDUCT_ROUTING_LABEL;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.client.nimrds.model.*;
import com.walmart.move.nim.receiving.core.client.nimrds.model.Error;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.net.URI;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Rest Client for NIM RDS Services
 *
 * @author v0k00fe
 */
@Component
public class NimRDSRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(NimRDSRestApiClient.class);

  private static final String RECEIVE_CONTAINERS = "/rds-services/receive/containers";
  private static final String QUANTITY_CHANGE = "/rds-services/quantity-change";
  private static final String QUANTITY_RECEIVED = "/rds-services/quantity/received";
  private static final String ITEM_DETAILS = "/rds-services/items/itemDetails";
  private static final String QUANTITY_RECEIVED_BY_PO = "/rds-services/delivery/qtyByPo/";
  private static final String RECEIPTS_BY_DELIVERIES = "/rds-services/receipts/deliveries";
  private static final String QUANTITY_RECEIVED_BY_POLINE =
      "/rds-services/delivery/qtyByPoLine/{deliveryNumber}";
  private static final String DSDC_RECEIVE = "/rds-services/dsdc";
  private static final String DA_BACKOUT_LABELS = "/rds-services/labelbackout/labels";

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  private RestConnector retryableRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  private HttpHeaders buildHttpHeaders(Map<String, Object> httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(
        ReceivingConstants.TENENT_FACLITYNUM,
        String.valueOf(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)));
    requestHeaders.add(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        String.valueOf(httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE)));

    String correlationId =
        Objects.isNull(TenantContext.getCorrelationId())
            ? UUID.randomUUID().toString()
            : TenantContext.getCorrelationId();
    requestHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, correlationId);
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
    return requestHeaders;
  }

  /**
   * Calls Receive Containers Endpoint and Gets the response back with slot information
   *
   * @param receiveContainersRequestBody
   * @param httpHeaders
   * @return
   * @throws ReceivingException
   */
  @Counted(
      name = "receiveContainersHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "receiveContainers")
  @Timed(
      name = "receiveContainersTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "receiveContainers")
  @ExceptionCounted(
      name = "receiveContainersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "receiveContainers")
  public ReceiveContainersResponseBody receiveContainers(
      ReceiveContainersRequestBody receiveContainersRequestBody, Map<String, Object> httpHeaders) {
    String facilityNumber = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString();
    TenantContext.setFacilityNum(Integer.parseInt(facilityNumber));
    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    if (Objects.nonNull(httpHeaders.get(IS_REINDUCT_ROUTING_LABEL))) {
      requestHeaders.add(IS_REINDUCT_ROUTING_LABEL, String.valueOf(Boolean.TRUE));
    }
    String uri = getRdsBaseUrlByTenant() + RECEIVE_CONTAINERS;

    String requestBody = gson.toJson(receiveContainersRequestBody);
    LOGGER.info("NIM RDS Receive Containers POST URI = {}, requestBody : {} ", uri, requestBody);

    try {
      ResponseEntity<String> receiveContainersResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.POST, new HttpEntity<>(requestBody, requestHeaders), String.class);
      LOGGER.info(
          "NIM RDS Receive Containers POST URI : {}, response body {}, header {}",
          uri,
          receiveContainersResponseEntity.getBody(),
          receiveContainersResponseEntity.getHeaders());
      ReceiveContainersResponseBody response =
          gson.fromJson(
              receiveContainersResponseEntity.getBody(), ReceiveContainersResponseBody.class);
      if (CollectionUtils.isNotEmpty(response.getErrors())) {
        LOGGER.error(
            "Error NIM RDS Receive Containers POST URI : {}, response body {}, headers {}",
            uri,
            receiveContainersResponseEntity.getBody(),
            receiveContainersResponseEntity.getHeaders());

        Error error = response.getErrors().get(0);
        throw new ReceivingBadDataException(
            ExceptionCodes.INVALID_RDS_SLOTTING_REQ,
            String.format(
                ReceivingConstants.SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG, error.getMessage()),
            new Object[] {error.getErrorCode(), error.getMessage()});
      }
      return response;
    } catch (RestClientResponseException e) {
      Error errorResponse = gson.fromJson(e.getResponseBodyAsString(), Error.class);
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_RDS_SLOTTING_REQ,
          String.format(
              ReceivingConstants.SLOTTING_BAD_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()),
          new Object[] {errorResponse.getErrorCode(), errorResponse.getMessage()});
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_SLOTTING_REQ,
          String.format(
              ReceivingConstants.SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  private String getRdsBaseUrlByTenant() {

    String nimRdsJsonMap = appConfig.getNimRDSServiceBaseUrl();
    JsonObject tenantBasedRdsMapJson = gson.fromJson(nimRdsJsonMap, JsonObject.class);

    JsonElement rdsUrlJsonElement =
        tenantBasedRdsMapJson.get(TenantContext.getFacilityNum().toString());
    if (Objects.nonNull(rdsUrlJsonElement)) {
      return rdsUrlJsonElement.getAsString();
    } else {
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(
              ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
    }
  }

  /**
   * Change the pallet quantity that is already reported
   *
   * @param quantityChangeRequestBody
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "quantityChangeHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityChange")
  @Timed(
      name = "quantityChangeTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityChange")
  @ExceptionCounted(
      name = "quantityChangeExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityChange")
  public QuantityChangeResponseBody quantityChange(
      QuantityChangeRequestBody quantityChangeRequestBody, Map<String, Object> httpHeaders) {

    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    String uri = getRdsBaseUrlByTenant() + QUANTITY_CHANGE;
    String requestBody = gson.toJson(quantityChangeRequestBody);
    LOGGER.info("NIM RDS Quantity Change PUT URI = {}, requestBody : {} ", uri, requestBody);

    try {
      ResponseEntity<String> qtyChangeResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.PUT, new HttpEntity<>(requestBody, requestHeaders), String.class);
      LOGGER.info(
          "NIM RDS Quantity Change PUT URI = {}, response body {}",
          uri,
          qtyChangeResponseEntity.getBody());
      return gson.fromJson(qtyChangeResponseEntity.getBody(), QuantityChangeResponseBody.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ,
          String.format(
              ReceivingConstants.SLOTTING_QTY_CORRECTION_BAD_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_QUANTITY_CORRECTION_REQ,
          String.format(
              ReceivingConstants.SLOTTING_QTY_CORRECTION_RESOURCE_RESPONSE_ERROR_MSG,
              e.getMessage()));
    }
  }

  /**
   * Get the Quantity Received by PO and PO Line
   *
   * @param rdsReceiptsRequest
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "quantityReceivedHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityReceived")
  @Timed(
      name = "quantityReceivedTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityReceived")
  @ExceptionCounted(
      name = "quantityReceivedExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "quantityReceived")
  public RdsReceiptsResponse quantityReceived(
      RdsReceiptsRequest rdsReceiptsRequest, Map<String, Object> httpHeaders) {

    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);

    String facilityNumber = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString();
    TenantContext.setFacilityNum(Integer.parseInt(facilityNumber));

    String uri = getRdsBaseUrlByTenant() + QUANTITY_RECEIVED;

    String requestBody = gson.toJson(rdsReceiptsRequest);
    LOGGER.info("NIM RDS Quantity Received POST URI = {}, requestBody : {} ", uri, requestBody);

    try {
      ResponseEntity<String> qtyChangeResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.POST, new HttpEntity<>(requestBody, requestHeaders), String.class);
      LOGGER.info(
          "NIM RDS Quantity Received POST URI = {}, response body {}",
          uri,
          qtyChangeResponseEntity.getBody());
      return gson.fromJson(qtyChangeResponseEntity.getBody(), RdsReceiptsResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_QUANTITY_RECEIVED_REQ,
          String.format(
              ReceivingConstants.RDS_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_QUANTITY_RECEIVED_REQ,
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  /**
   * Get received quantity summary by PO Level for the given delivery number
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "getReceivedQtySummaryByPoHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPo")
  @Timed(
      name = "getReceivedQtySummaryByPoTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPo")
  @ExceptionCounted(
      name = "getReceivedQtySummaryByPoExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPo")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "RDS-Get-ReceivedQtySummaryByPo",
      type = Type.REST,
      externalCall = true)
  public RdsReceiptsSummaryByPoResponse getReceivedQtySummaryByPo(
      Long deliveryNumber, Map<String, Object> httpHeaders) throws ReceivingException {

    String facilityNumber = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString();
    TenantContext.setFacilityNum(Integer.parseInt(facilityNumber));
    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    String uri = getRdsBaseUrlByTenant() + QUANTITY_RECEIVED_BY_PO + deliveryNumber;
    LOGGER.info(
        "NIM RDS Quantity Received by delivery number GET URI = {}, deliveryNumber : {} ",
        uri,
        deliveryNumber);

    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
      return gson.fromJson(response.getBody(), RdsReceiptsSummaryByPoResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          deliveryNumber,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_FROM_RDS,
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          deliveryNumber,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_PO_ERROR_FROM_RDS);
    }
  }

  /**
   * Get received quantity summary by Line Level for the given purchase reference number
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "getReceivedQtySummaryByPoLineHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPoLine")
  @Timed(
      name = "getReceivedQtySummaryByPoLineTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPoLine")
  @ExceptionCounted(
      name = "getReceivedQtySummaryByPoLineExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByPoLine")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "RDS-Get-ReceivedQtySummaryByPoLine",
      type = Type.REST,
      externalCall = true)
  public ReceiptSummaryQtyByPoLineResponse getReceivedQtySummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, Map<String, Object> httpHeaders)
      throws ReceivingException {

    String facilityNumber = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString();
    TenantContext.setFacilityNum(Integer.parseInt(facilityNumber));
    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);

    Map<String, Long> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    URI receiptsSummaryByPoLineUri =
        UriComponentsBuilder.fromUriString(getRdsBaseUrlByTenant() + QUANTITY_RECEIVED_BY_POLINE)
            .buildAndExpand(pathParams)
            .toUri();
    receiptsSummaryByPoLineUri =
        UriComponentsBuilder.fromUriString(receiptsSummaryByPoLineUri.toString())
            .queryParam(ReceivingConstants.PURCHASE_REFERENCE_NUMBER, purchaseReferenceNumber)
            .build()
            .toUri();

    LOGGER.info(
        "NIM RDS Received Qty Summary by PoLine GET URI = {}, deliveryNumber : {} ",
        receiptsSummaryByPoLineUri,
        deliveryNumber);

    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              receiptsSummaryByPoLineUri.toString(),
              HttpMethod.GET,
              new HttpEntity<>(requestHeaders),
              String.class);
      return gson.fromJson(response.getBody(), ReceiptSummaryQtyByPoLineResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receiptsSummaryByPoLineUri,
          deliveryNumber,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS,
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receiptsSummaryByPoLineUri,
          deliveryNumber,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ExceptionCodes.RECEIVED_QTY_SUMMARY_BY_POLINE_ERROR_FROM_RDS);
    }
  }

  /**
   * Get the Item details for given item numbers
   *
   * @param itemNumbers
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "itemDetailsHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "itemDetails")
  @Timed(
      name = "itemDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "itemDetails")
  @ExceptionCounted(
      name = "itemDetailsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "itemDetails")
  public ItemDetailsResponseBody itemDetails(
      List<String> itemNumbers, Map<String, Object> httpHeaders) {

    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);

    String uri = getRdsBaseUrlByTenant() + ITEM_DETAILS;

    String requestBody = gson.toJson(new ItemDetailsRequestBody(itemNumbers));
    LOGGER.info("NIM RDS Item Details POST URI = {}, requestBody : {} ", uri, requestBody);

    try {
      ResponseEntity<String> itemDetailResponseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.POST, new HttpEntity<>(requestBody, requestHeaders), String.class);
      LOGGER.info(
          "NIM RDS Item Details POST URI = {}, response body {}",
          uri,
          itemDetailResponseEntity.getBody());
      return gson.fromJson(itemDetailResponseEntity.getBody(), ItemDetailsResponseBody.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      return null;
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return null;
    }
  }

  /**
   * Get received quantity summary by delivery number
   *
   * @param receiptSummaryQtyByDeliveries
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "getReceivedQtySummaryByDeliveryNumbersHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByDeliveryNumbers")
  @Timed(
      name = "getReceivedQtySummaryByDeliveryNumbersTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByDeliveryNumbers")
  @ExceptionCounted(
      name = "getReceivedQtySummaryByDeliveryNumbersExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getReceivedQtySummaryByDeliveryNumbers")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "RDS-Get-ReceivedQtySummaryByDeliveryNumbers",
      type = Type.REST,
      externalCall = true)
  public List<ReceiptQtySummaryByDeliveryNumberResponse> getReceivedQtySummaryByDeliveryNumbers(
      ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries, Map<String, Object> httpHeaders)
      throws ReceivingException {

    String facilityNumber = httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString();
    TenantContext.setFacilityNum(Integer.parseInt(facilityNumber));
    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    String receiptSummaryByDeliveriesUri = getRdsBaseUrlByTenant() + RECEIPTS_BY_DELIVERIES;
    String requestBody = gson.toJson(receiptSummaryQtyByDeliveries);
    LOGGER.info(
        "NIM RDS Get receipts by delivery numbers POST URI = {}, requestBody : {} ",
        receiptSummaryByDeliveriesUri,
        requestBody);

    try {
      ResponseEntity<String> response =
          retryableRestConnector.exchange(
              receiptSummaryByDeliveriesUri,
              HttpMethod.POST,
              new HttpEntity<>(requestBody, requestHeaders),
              String.class);
      return Arrays.asList(
          gson.fromJson(response.getBody(), ReceiptQtySummaryByDeliveryNumberResponse[].class));
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receiptSummaryByDeliveriesUri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.RDS_RECEIVED_QTY_SUMMARY_BY_DELIVERY_NUMBERS,
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receiptSummaryByDeliveriesUri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          String.format(ReceivingConstants.RDS_RESPONSE_ERROR_MSG, e.getMessage()),
          HttpStatus.INTERNAL_SERVER_ERROR,
          ExceptionCodes.RDS_RECEIVED_QTY_SUMMARY_BY_DELIVERY_NUMBERS);
    }
  }

  /**
   * Back out given DA labels
   *
   * @param daLabelBackoutRequest
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "labelBackoutHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "labelBackout")
  @Timed(
      name = "labelBackoutTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "labelBackout")
  @ExceptionCounted(
      name = "labelBackoutExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "labelBackout")
  public DALabelBackoutResponse labelBackout(
      DALabelBackoutRequest daLabelBackoutRequest, Map<String, Object> httpHeaders) {

    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    String uri = getRdsBaseUrlByTenant() + DA_BACKOUT_LABELS;
    String requestBody = gson.toJson(daLabelBackoutRequest);
    LOGGER.info("NIM RDS DA Label backout PUT URI = {}, requestBody : {} ", uri, requestBody);
    ResponseEntity<String> response = null;
    try {
      response =
          retryableRestConnector.exchange(
              uri, HttpMethod.PUT, new HttpEntity<>(requestBody, requestHeaders), String.class);
      LOGGER.info(
          "NIM RDS DA Label backout PUT URI = {}, response body {}", uri, response.getBody());
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_DA_LBL_BACKOUT_REQUEST,
          ReceivingConstants.INVALID_DA_LBL_BACKOUT_REQUEST);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.NIM_RDS_SERVICE_UNAVAILABLE_ERROR,
          ReceivingConstants.NIM_RDS_SERVICE_UNAVAILABLE_ERROR);
    }
    return gson.fromJson(response.getBody(), DALabelBackoutResponse.class);
  }

  /**
   * Receive a DSDC pack
   *
   * @param dsdcReceiveRequest
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "dsdcReceiveHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "dsdcReceive")
  @Timed(
      name = "dsdcReceiveTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "dsdcReceive")
  @ExceptionCounted(
      name = "dsdcReceiveExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "dsdcReceive")
  public DsdcReceiveResponse receiveDsdcPack(
      DsdcReceiveRequest dsdcReceiveRequest, Map<String, Object> httpHeaders) {

    HttpHeaders requestHeaders = buildHttpHeaders(httpHeaders);
    String dsdcPackReceivingUri = getRdsBaseUrlByTenant() + DSDC_RECEIVE;
    String dsdcPackReceivingrequest = gson.toJson(dsdcReceiveRequest);
    LOGGER.info(
        "NIM RDS Dsdc receive POST URI = {}, requestBody : {} ",
        dsdcPackReceivingUri,
        dsdcReceiveRequest);
    ResponseEntity<String> dsdcReceiveResponse = null;
    DsdcReceiveResponse response = null;

    try {
      dsdcReceiveResponse =
          retryableRestConnector.exchange(
              dsdcPackReceivingUri,
              HttpMethod.POST,
              new HttpEntity<>(dsdcPackReceivingrequest, requestHeaders),
              String.class);
      response = gson.fromJson(dsdcReceiveResponse.getBody(), DsdcReceiveResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_WITH_ERROR_DETAILS,
          dsdcPackReceivingUri,
          dsdcPackReceivingrequest,
          e.getResponseBodyAsString(),
          e.getRawStatusCode(),
          ExceptionUtils.getStackTrace(e));
      response = gson.fromJson(e.getResponseBodyAsString(), DsdcReceiveResponse.class);
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          dsdcPackReceivingUri,
          dsdcPackReceivingrequest,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.NIM_RDS_SERVICE_UNAVAILABLE_ERROR,
          ReceivingConstants.NIM_RDS_SERVICE_UNAVAILABLE_ERROR);
    }
    return response;
  }

  /**
   * Get StoreDistribution by delivery document line
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "getStoreDistributionByDeliveryDocumentLineHitCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getStoreDistributionByDeliveryDocumentLine")
  @Timed(
      name = "getStoreDistributionByDeliveryDocumentLineTimed",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getStoreDistributionByDeliveryDocumentLine")
  @ExceptionCounted(
      name = "getStoreDistributionByDeliveryDocumentLineExceptionCount",
      level1 = "uwms-receiving",
      level2 = "nimRDSRestApiClient",
      level3 = "getStoreDistributionByDeliveryDocumentLine")
  @TimeTracing(
      component = AppComponent.CORE,
      executionFlow = "RDS-Get-StoreDistributionByDeliveryDocumentLine",
      type = Type.REST,
      externalCall = true)
  public Pair<Integer, List<StoreDistribution>> getStoreDistributionByDeliveryDocumentLine(
      String purchaseReferenceNumber,
      int purchaseReferenceLineNumber,
      Map<String, Object> httpHeaders) {
    List<StoreDistribution> storeDistributionList = new ArrayList<>();
    int totalReceivedQty = 0;
    RdsReceiptsRequest rdsReceiptsRequest = new RdsReceiptsRequest();
    List<OrderLines> orderLinesList = new ArrayList<>();
    OrderLines orderLine = new OrderLines();
    orderLine.setPoNumber(purchaseReferenceNumber);
    orderLine.setPoLine(purchaseReferenceLineNumber);
    orderLinesList.add(orderLine);
    rdsReceiptsRequest.setOrderLines(orderLinesList);
    RdsReceiptsResponse rdsReceiptsResponse = quantityReceived(rdsReceiptsRequest, httpHeaders);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        ReceivingUtils.handleRDSResponse(rdsReceiptsResponse);

    if (!receivedQuantityResponseFromRDS.getErrorResponseMapByPoAndPoLine().isEmpty()) {
      String errorResponsePoPoLineKey =
          receivedQuantityResponseFromRDS
              .getErrorResponseMapByPoAndPoLine()
              .keySet()
              .stream()
              .findFirst()
              .orElse(null);
      LOGGER.error(
          "Error in fetching received quantity information from RDS for PO:{} and PO Line:{}",
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
      throw new ReceivingBadDataException(
          ExceptionCodes.GET_STORE_DIST_ERROR_FROM_RDS,
          String.format(
              ReceivingConstants.RDS_RESPONSE_ERROR_MSG,
              receivedQuantityResponseFromRDS
                  .getErrorResponseMapByPoAndPoLine()
                  .get(errorResponsePoPoLineKey)));
    } else if (CollectionUtils.isNotEmpty(rdsReceiptsResponse.getFound())) {
      String receivedQtyPoPoLineKey =
          receivedQuantityResponseFromRDS
              .getReceivedQtyMapByPoAndPoLine()
              .keySet()
              .stream()
              .findFirst()
              .orElse(null);
      totalReceivedQty =
          Math.toIntExact(
              receivedQuantityResponseFromRDS
                  .getReceivedQtyMapByPoAndPoLine()
                  .get(receivedQtyPoPoLineKey));
      List<Found> foundListResponse = rdsReceiptsResponse.getFound();
      storeDistributionList = foundListResponse.get(0).getResults().get(0).getStoreDistrib();
    }
    return new Pair<>(totalReceivedQty, storeDistributionList);
  }
}
