package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.*;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionRequest;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionResponse;
import com.walmart.move.nim.receiving.rdc.model.VoidLPNRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Objects;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class MirageRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(MirageRestApiClient.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private Gson gson;

  @Resource(name = ReceivingConstants.BEAN_RETRYABLE_CONNECTOR)
  private RestConnector retryableRestConnector;

  /**
   * Void LPN for SSTK Atlas Items
   *
   * @param voidLPNRequest
   * @param httpHeaders
   * @return
   */
  @Counted(
      name = "voidLPNHitCount",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "voidLPN")
  @Timed(
      name = "voidLPNTimed",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "voidLPN")
  @ExceptionCounted(
      name = "voidLPNExceptionCount",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "voidLPN")
  @Async
  public void voidLPN(VoidLPNRequest voidLPNRequest, HttpHeaders httpHeaders) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(ReceivingConstants.ACCEPT, APPLICATION_JSON);
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, APPLICATION_JSON);
    String receivingMirageBaseUrl =
        getMirageBaseUrlByTenant(httpHeaders.getFirst(TENENT_FACLITYNUM)) + VOID_LPN;

    String requestBody = gson.toJson(voidLPNRequest);

    LOGGER.info(
        "Making a call to receiving mirage to void LPN with url: {}, requestBody : {}, headers {} ",
        receivingMirageBaseUrl,
        requestBody,
        requestHeaders);
    try {
      ResponseEntity<String> voidLPNResponse =
          retryableRestConnector.exchange(
              receivingMirageBaseUrl,
              HttpMethod.POST,
              new HttpEntity<>(requestBody, requestHeaders),
              String.class);
      if (voidLPNResponse.getStatusCode().is2xxSuccessful()) {
        LOGGER.info(
            "Void LPN request for quantity: {} successfully posted to receiving mirage for delivery: {},purchaseReferenceNumber: {},"
                + "purchaseReferenceLineNumber{}",
            voidLPNRequest.getReceivedQuantityByLines().get(0).getReceivedQty(),
            voidLPNRequest.getDeliveryNumber(),
            voidLPNRequest.getReceivedQuantityByLines().get(0).getPurchaseReferenceNumber(),
            voidLPNRequest.getReceivedQuantityByLines().get(0).getPurchaseReferenceLineNumber());
      }
    } catch (Exception e) {
      LOGGER.info(
          "Request failed with status message: {} while updating void LPN to receiving mirage for delivery: {},purchaseReferenceNumber: {}, "
              + "purchaseReferenceLineNumber: {}",
          e.getMessage(),
          voidLPNRequest.getDeliveryNumber(),
          voidLPNRequest.getReceivedQuantityByLines().get(0).getPurchaseReferenceNumber(),
          voidLPNRequest.getReceivedQuantityByLines().get(0).getPurchaseReferenceLineNumber());
    }
  }

  /**
   * Process LPN Exceptions
   *
   * @param mirageExceptionRequest
   * @return
   */
  @Counted(
      name = "processExceptionMirageHitCount",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "processExceptionMirage")
  @Timed(
      name = "processExceptionMirageTimed",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "processExceptionMirage")
  @ExceptionCounted(
      name = "processExceptionMirageExceptionCount",
      level1 = "uwms-receiving",
      level2 = "mirageRestApiClinet",
      level3 = "processExceptionMirage")
  public MirageExceptionResponse processException(MirageExceptionRequest mirageExceptionRequest) {

    gson =
        new GsonBuilder()
            .registerTypeAdapter(
                Date.class,
                (JsonDeserializer<Date>)
                    (jsonElement, type, context) ->
                        new Date(jsonElement.getAsJsonPrimitive().getAsLong()))
            .create();
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(ReceivingConstants.ACCEPT, APPLICATION_JSON);
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, APPLICATION_JSON);
    String receivingMirageBaseUrl =
        getMirageBaseUrlByTenant(TenantContext.getFacilityNum().toString()) + ACL_EXCEPTION;
    HttpEntity httpEntity = new HttpEntity(gson.toJson(mirageExceptionRequest), requestHeaders);
    String requestBody = gson.toJson(mirageExceptionRequest);
    LOGGER.info(
        "Invoking receiving-mirage to receive the exception for LPN with url: {}, requestBody : {}, headers {}",
        receivingMirageBaseUrl,
        requestBody,
        requestHeaders);
    try {
      ResponseEntity<String> mirageExceptionReceivingResponseEntity =
          retryableRestConnector.exchange(
              receivingMirageBaseUrl,
              HttpMethod.POST,
              new HttpEntity<>(requestBody, requestHeaders),
              String.class);
      if (mirageExceptionReceivingResponseEntity.getStatusCode().is2xxSuccessful()) {
        LOGGER.info(
            "Request sent successfully with responseBody: {}",
            mirageExceptionReceivingResponseEntity);
        validateExceptionFromResponse(mirageExceptionReceivingResponseEntity);
      }
      return gson.fromJson(
          mirageExceptionReceivingResponseEntity.getBody(), MirageExceptionResponse.class);
    } catch (RestClientResponseException e) {
      LOGGER.info(
          "Exception e message {} and response body {}",
          e.getMessage(),
          e.getResponseBodyAsString());
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receivingMirageBaseUrl,
          httpEntity,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      if (e.getRawStatusCode() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
        throw new ReceivingInternalException(
            ExceptionCodes.MIRAGE_EXCEPTION_INTERNAL_SERVER,
            ReceivingConstants.MIRAGE_UNAVAILABLE_ERROR);
      }
      throw e;
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          receivingMirageBaseUrl,
          httpEntity,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingInternalException(
          ExceptionCodes.MIRAGE_EXCEPTION_INTERNAL_SERVER,
          String.format(ReceivingConstants.RESOURCE_MIRAGE_RESPONSE_ERROR_MSG, e.getMessage()));
    }
  }

  private String getMirageBaseUrlByTenant(String facilityNum) {

    String nimRdsJsonMap = appConfig.getReceivingMirageBaseUrl();
    JsonObject tenantBasedRdsMapJson = gson.fromJson(nimRdsJsonMap, JsonObject.class);

    JsonElement mirageUrlJsonElement = tenantBasedRdsMapJson.get(facilityNum);
    if (Objects.nonNull(mirageUrlJsonElement)) {
      return mirageUrlJsonElement.getAsString();
    } else {
      throw new ReceivingInternalException(
          ExceptionCodes.CONFIGURATION_ERROR,
          String.format(ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, facilityNum));
    }
  }

  private void validateExceptionFromResponse(
      ResponseEntity<String> lpnExceptionResponseResponseEntity) {
    JsonObject jsonObject =
        gson.fromJson(lpnExceptionResponseResponseEntity.getBody(), JsonObject.class);
    if (jsonObject.has("title") && jsonObject.has("message")) {
      // Exception message flow
      throw new RestClientResponseException(
          jsonObject.get("message").getAsString(),
          404,
          null,
          null,
          jsonObject.toString().getBytes(StandardCharsets.UTF_8),
          null);
    }
  }
}
