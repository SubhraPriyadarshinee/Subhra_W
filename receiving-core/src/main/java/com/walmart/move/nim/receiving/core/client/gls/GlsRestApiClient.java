package com.walmart.move.nim.receiving.core.client.gls;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.replacePathParams;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESTUTILS_ERROR_MESSAGE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.lang.String.valueOf;
import static java.time.LocalDateTime.now;
import static java.time.ZoneOffset.UTC;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.ObjectUtils.allNotNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.gls.model.*;
import com.walmart.move.nim.receiving.core.client.gls.model.Error;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
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

/**
 * Client for GLS Rest API
 *
 * @author k0c0e5k
 */
@Component
public class GlsRestApiClient {

  private static final Logger LOG = LoggerFactory.getLogger(GlsRestApiClient.class);
  public static final String GLS_BASE_URL_DEFAULT = ""; // throws error for support to fix
  public static final String GLS_BASE_URL_FOR_TENANT = "glsBaseUrl";
  public static final String GLS_ADJUST_URI = "/adjust"; // VTR or Receiving Correction
  private static final String GLS_RECEIVE_URI = "/receive"; // GLS Receive uri
  private static final String GLS_CREATE_NEW_LPN_URI = "/createTag"; // create new GLS LPN
  private static final String GLS_DELIVERY_DETAILS =
      "/deliveryDetails/{deliveryNumber}"; // GLS Delivery details uri

  public static final String GLS_STATUS_OK = "OK";
  // TODO values can change based on usage or GLS contract changes
  public static final String GLS_ADJUST_BAD_REQUEST_CODE = "GLS_ADJUST_BAD_REQUEST";
  public static final String GLS_ADJUST_BAD_RESPONSE_CODE = "GLS_ADJUST_BAD_RESPONSE";
  public static final String GLS_RESPONSE_DEFAULT_ERROR_CODE = "GLS_RESPONSE_DEFAULT_ERROR_CODE";
  public static final String GLS_RESPONSE_EMPTY_ERROR_MESSAGE =
      "Receiving got unknown/empty error response from GLS. ";
  public static final String GLS_ADJUST_BAD_REQUEST_MSG =
      "GLS Adjust request missing mandatory data";
  public static final String GLS_ADJUST_BAD_RESPONSE_MSG =
      "GLS Adjust response missing mandatory data";

  // GLS Exception Constants
  public static final String GLS_RECEIVE_ERROR_CODE = "glsReceiveFailed";

  public static final String GLS_DELIVERY_DETAILS_ERROR_CODE = "glsDeliveryDetailsFailed";

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Autowired private Gson gson;

  @Counted(
      name = "adjustOrCancelHitCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "adjustOrCancel")
  @Timed(
      name = "adjustOrCancelTimed",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "adjustOrCancel")
  @ExceptionCounted(
      name = "adjustOrCancelExceptionCounted",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "adjustOrCancel")
  public GlsAdjustPayload adjustOrCancel(GlsAdjustPayload glsAdjustPayload, HttpHeaders headers)
      throws ReceivingException {
    validateGlsAdjustRequest(glsAdjustPayload);
    final String url = getGlsBaseUrl() + GLS_ADJUST_URI;
    final String request = gson.toJson(glsAdjustPayload);

    ResponseEntity<String> response = null;
    try {
      LOG.info("Calling GLS Adjust POST url={}, request={}", url, request);
      response = simpleRestConnector.post(url, request, headers, String.class);
      LOG.info("Called GLS Adjust url={}, response={}", url, response);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          request,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_ADJUST_BAD_RESPONSE_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          request,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_ADJUST_BAD_RESPONSE_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (Exception e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          request,
          "Unknown exception",
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_ADJUST_BAD_RESPONSE_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    }

    final GlsApiInfo glsApiInfo = validateResponseAndGetGlsApiInfo(response);
    return gson.fromJson(glsApiInfo.getPayload(), GlsAdjustPayload.class);
  }

  @Counted(
      name = "glsReceiveHitCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  @Timed(
      name = "glsReceiveTimed",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  @ExceptionCounted(
      name = "glsReceiveExceptionCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  public GLSReceiveResponse receive(GLSReceiveRequest glsReceiveRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    String url = getGlsBaseUrl() + GLS_RECEIVE_URI;
    ResponseEntity<String> response = null;
    final String jsonRequest = gson.toJson(glsReceiveRequest);
    try {
      LOG.info("Calling GLS Receive POST url={}, request={}", url, jsonRequest);
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(jsonRequest, httpHeaders), String.class);
      LOG.info("Called GLS Receive url={}, response={}", url, response);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          glsReceiveRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_RECEIVE_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          glsReceiveRequest,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_RECEIVE_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    }

    final GlsApiInfo glsApiInfo = validateResponseAndGetGlsApiInfo(response);
    return gson.fromJson(glsApiInfo.getPayload(), GLSReceiveResponse.class);
  }

  @Counted(
      name = "glsDeliveryDetailsHitCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "glsDeliveryDetails")
  @Timed(
      name = "glsDeliveryDetailsTimed",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "glsDeliveryDetails")
  @ExceptionCounted(
      name = "glsDeliveryDetailsExceptionCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "glsDeliveryDetails")
  public GLSDeliveryDetailsResponse deliveryDetails(String deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {

    String baseUri = getGlsBaseUrl() + GLS_DELIVERY_DETAILS;
    ResponseEntity<String> response = null;

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);

    final String url = replacePathParams(baseUri, pathParams).toString();
    try {
      LOG.info("Calling GLS Delivery details GET url={}, httpHeaders={}", url, httpHeaders);
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.GET, new HttpEntity<>(httpHeaders), String.class);
      LOG.info("Called GLS Delivery details url={}, response={}", url, response);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_DELIVERY_DETAILS_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          StringUtils.EMPTY,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_DELIVERY_DETAILS_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    }

    if (204 == response.getStatusCodeValue()) {
      return new GLSDeliveryDetailsResponse(deliveryNumber, new ArrayList<>());
    } else {
      final GlsApiInfo glsApiInfo = validateResponseAndGetGlsApiInfo(response);
      return gson.fromJson(glsApiInfo.getPayload(), GLSDeliveryDetailsResponse.class);
    }
  }

  @Counted(
      name = "createGlsLpnHitCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  @Timed(
      name = "createGlsLpnTimed",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  @ExceptionCounted(
      name = "createGlsLpnExceptionCount",
      level1 = "uwms-receiving",
      level2 = "glsRestApiClient",
      level3 = "receiveInGls")
  public GlsLpnResponse createGlsLpn(GlsLpnRequest glsLpnRequest, HttpHeaders httpHeaders)
      throws ReceivingException {

    final String url = getGlsBaseUrl() + GLS_CREATE_NEW_LPN_URI;
    ResponseEntity<String> response = null;
    final String jsonRequest = gson.toJson(glsLpnRequest);

    try {
      LOG.info("Call GLS create new LPN url POST={}, request={}", url, jsonRequest);
      response =
          simpleRestConnector.exchange(
              url, HttpMethod.POST, new HttpEntity<>(jsonRequest, httpHeaders), String.class);
      LOG.info("Call GLS create new LPN response={}", response);
    } catch (RestClientResponseException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          glsLpnRequest,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_RECEIVE_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (ResourceAccessException e) {
      LOG.error(
          RESTUTILS_ERROR_MESSAGE,
          url,
          glsLpnRequest,
          RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throwGlsException(GLS_RECEIVE_ERROR_CODE, INTERNAL_SERVER_ERROR, e.getMessage());
    }

    final GlsApiInfo glsApiInfo = validateResponseAndGetGlsApiInfo(response);
    return gson.fromJson(glsApiInfo.getPayload(), GlsLpnResponse.class);
  }

  private String getGlsBaseUrl() {
    final String glsBaseUrl =
        tenantSpecificConfigReader.getCcmValue(
            getFacilityNum(), GLS_BASE_URL_FOR_TENANT, GLS_BASE_URL_DEFAULT);
    return glsBaseUrl;
  }

  private void throwGlsException(String errCode, HttpStatus httpStatus, String errMsg)
      throws ReceivingException {
    throw new ReceivingException(errMsg, httpStatus, errCode);
  }

  public GlsApiInfo validateResponseAndGetGlsApiInfo(ResponseEntity<String> entityResponse)
      throws ReceivingException {
    if (isNull(entityResponse) || isNull(entityResponse.getBody())) {
      LOG.error(GLS_RESPONSE_EMPTY_ERROR_MESSAGE + "entityResponse={}", entityResponse);
      throwGlsException(
          GLS_RESPONSE_DEFAULT_ERROR_CODE, INTERNAL_SERVER_ERROR, GLS_RESPONSE_EMPTY_ERROR_MESSAGE);
    }

    final GlsApiInfo glsApiInfo = gson.fromJson(entityResponse.getBody(), GlsApiInfo.class);
    final String status = glsApiInfo.getStatus();
    final int statusCode = entityResponse.getStatusCodeValue();

    if (!GLS_STATUS_OK.equalsIgnoreCase(status) && statusCode != 204) {
      LOG.error(
          "GLS Response status is NOT OK, status={} and response code={}", status, statusCode);

      final ArrayList<Error> errors = glsApiInfo.getErrors();
      Error error;
      if (isNull(errors) || errors.size() <= 0 || isNull(errors.get(0))) {
        final String invalidErrors =
            GLS_DELIVERY_DETAILS_ERROR_CODE + " GLS response's errors or error[0] is null";
        LOG.error(invalidErrors);
        error = new Error(GLS_DELIVERY_DETAILS_ERROR_CODE, invalidErrors, null, null);
      } else {
        error = errors.get(0);
      }

      throwGlsException(
          error.code,
          entityResponse.getStatusCode().is2xxSuccessful()
                  || entityResponse.getStatusCode().is4xxClientError()
              ? BAD_REQUEST
              : INTERNAL_SERVER_ERROR,
          error.description);
    }

    LOG.info("returning glsApiInfo={}", glsApiInfo);
    return glsApiInfo;
  }

  private void validateGlsAdjustRequest(GlsAdjustPayload req) throws ReceivingException {
    if (isNull(req)
        || !allNotNull(
            req.getPalletTagId(),
            req.getNewQty(),
            req.getOriginalQty(),
            req.getQtyUOM(),
            req.getReasonCode())) {
      throwGlsException(
          GLS_ADJUST_BAD_REQUEST_CODE, INTERNAL_SERVER_ERROR, GLS_ADJUST_BAD_REQUEST_MSG);
    }
  }

  /**
   * Creates request for GLS Adjust end point for LPN cancellation (VTR) or Pallet Correction
   *
   * @param reasonCode
   * @param trackingId
   * @param newQtyVnpk
   * @param originalQtyVnpk
   * @return
   * @throws ReceivingException
   */
  public GlsAdjustPayload createGlsAdjustPayload(
      String reasonCode,
      String trackingId,
      Integer newQtyVnpk,
      Integer originalQtyVnpk,
      String userId)
      throws ReceivingException {

    if (!allNotNull(reasonCode, trackingId, newQtyVnpk, originalQtyVnpk)) {
      throwGlsException(
          GLS_ADJUST_BAD_REQUEST_CODE, INTERNAL_SERVER_ERROR, GLS_ADJUST_BAD_REQUEST_MSG);
    }

    GlsAdjustPayload payload = new GlsAdjustPayload();
    payload.setPalletTagId(trackingId);
    payload.setNewQty(newQtyVnpk);
    payload.setOriginalQty(originalQtyVnpk);
    payload.setQtyUOM(VNPK);
    payload.setReasonCode(reasonCode);
    payload.setOperationTimestamp(valueOf(now(UTC)));
    payload.setCreateUser(userId);

    return payload;
  }
}
