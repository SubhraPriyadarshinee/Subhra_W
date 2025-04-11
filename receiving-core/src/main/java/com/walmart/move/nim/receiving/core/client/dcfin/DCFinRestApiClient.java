package com.walmart.move.nim.receiving.core.client.dcfin;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.SUBCENTER_ID_HEADER;
import static com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow.DCFIN_ADJUST_OR_VTR;
import static com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow.DCFIN_PO_CLOSE;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.model.DcFinAdjustRequest;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.DCFinPOCloseRequestBody;
import com.walmart.move.nim.receiving.core.model.DCFinPOCloseStatusResponse;
import com.walmart.move.nim.receiving.core.model.DCFinRestApiClientErrorResponse;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.core.service.RapidRelayerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import javax.validation.Valid;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Client for DC-Financial Rest API
 *
 * @author v0k00fe
 */
@Component
public class DCFinRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(DCFinRestApiClient.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private AsyncPersister asyncPersister;
  @Autowired private RetryService jmsRecoveryService;
  @Autowired private RapidRelayerService rapidRelayerService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "restConnector")
  private RestConnector simpleRestConnector;

  private final Gson gson;

  public DCFinRestApiClient() {
    gson =
        new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
  }

  public static HttpHeaders buildHttpHeaders(Map<String, Object> httpHeaders, String dcFinApiKey) {
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(
        ReceivingConstants.TENENT_FACLITYNUM,
        String.valueOf(httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM)));
    requestHeaders.add(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        String.valueOf(httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE)));
    requestHeaders.add(
        CORRELATION_ID_HEADER_KEY, httpHeaders.get(CORRELATION_ID_HEADER_KEY).toString());
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
    requestHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, dcFinApiKey);
    requestHeaders.add(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    if (nonNull(httpHeaders.get(SUBCENTER_ID_HEADER)))
      requestHeaders.add(SUBCENTER_ID_HEADER, httpHeaders.get(SUBCENTER_ID_HEADER).toString());
    if (httpHeaders.containsKey(ORG_UNIT_ID_HEADER)
        && isNotBlank(httpHeaders.get(ORG_UNIT_ID_HEADER).toString()))
      requestHeaders.add(ORG_UNIT_ID_HEADER, httpHeaders.get(ORG_UNIT_ID_HEADER).toString());
    return requestHeaders;
  }

  /**
   * Finds Delivery details by delivery number
   *
   * @return DCFinPOCloseRequestBody
   * @throws DCFinRestApiClientException
   */
  @Timed(
      name = "poCloseTimed",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "poClose")
  @ExceptionCounted(
      name = "poCloseExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "poClose")
  public void poClose(
      @Valid DCFinPOCloseRequestBody dcFinPOCloseRequestBody, Map<String, Object> httpHeaders)
      throws DCFinRestApiClientException {
    final HttpHeaders requestHeaders =
        DCFinRestApiClient.buildHttpHeaders(httpHeaders, appConfig.getDcFinApiKey());
    final String uri = appConfig.getDcFinBaseUrl() + PO_CLOSURE;
    final String txnId = dcFinPOCloseRequestBody.getTxnId();
    final String cId = requestHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final String requestBody = gson.toJson(dcFinPOCloseRequestBody);
    final HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, requestHeaders);

    LOGGER.info(
        "DcFin:poClose for cId={} txnId={}, URI={}, Request={}", cId, txnId, uri, requestEntity);
    try {
      ResponseEntity<String> finalizePoResponseEntity =
          retryableRestConnector.exchange(uri, POST, requestEntity, String.class);

      LOGGER.info(
          "DcFin:poClose for cId={}, txnId={}, Response={}", cId, txnId, finalizePoResponseEntity);

    } catch (RestClientResponseException e) {
      LOGGER.error(
          "DcFin:poClose for CorrelationId={}, txnId={}, URI={}, error: statusCode={}, response={}",
          cId,
          txnId,
          uri,
          e.getRawStatusCode(),
          e.getResponseBodyAsString());
      throwDcFinException(e);
    }
  }

  /**
   * DcFin Po Close Async call persist in db and retry asynchronously
   *
   * @return DCFinPOCloseRequestBody
   */
  @Timed(
      name = "poCloseTimed",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "poCloseAsync")
  @ExceptionCounted(
      name = "poCloseExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "poCloseAsync")
  public void poCloseAsync(
      @Valid DCFinPOCloseRequestBody dcFinPOCloseRequestBody, Map<String, Object> headersMap) {
    final HttpHeaders httpHeaders =
        DCFinRestApiClient.buildHttpHeaders(headersMap, appConfig.getDcFinApiKey());
    final String txnId = dcFinPOCloseRequestBody.getTxnId();
    final String url = appConfig.getDcFinBaseUrl() + PO_CLOSURE;
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final String request = gson.toJson(dcFinPOCloseRequestBody);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        String.valueOf(headersMap.get(ReceivingConstants.TENENT_FACLITYNUM)),
        ReceivingConstants.OUTBOX_PATTERN_ENABLED,
        false)) {
      try {
        rapidRelayerService.produceHttpMessage(
            DCFIN_PO_CLOSURE, request, ReceivingUtils.convertHttpHeadersToHashMap(httpHeaders));
      } catch (ReceivingException e) {
        LOGGER.error(
            "DcFin:poClose using outbox for CorrelationId={}, txnId={}, url={}, error: response={}",
            cId,
            txnId,
            url,
            e.getErrorResponse());
      }
    } else {
      RetryEntity eventRetryEntity =
          jmsRecoveryService.putForRetries(url, POST, httpHeaders, request, DCFIN_PO_CLOSE);
      asyncPersister.asyncPost(cId, txnId, eventRetryEntity, url, httpHeaders, request);
    }
  }

  /**
   * DcFin adjustment i.e Receiving Correction or VTR Async call persist in db and retry
   * asynchronously.
   *
   * <pre>
   * DcFinAdjustRequest:
   * witron do only fixed wt correction but gls do both fixed and variable weight
   *
   * </pre>
   *
   * @return DcFinAdjustRequest
   */
  @Timed(
      name = "adjustOrVtrTimed",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "adjustOrVtr")
  @ExceptionCounted(
      name = "adjustOrVtrExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "adjustOrVtr")
  public void adjustOrVtr(
      @Valid DcFinAdjustRequest dcFinAdjustRequest, Map<String, Object> headers) {
    final HttpHeaders httpHeaders =
        DCFinRestApiClient.buildHttpHeaders(headers, appConfig.getDcFinApiKey());
    final String url = appConfig.getDcFinBaseUrl() + DC_FIN_POST_V2_ADJUST;
    final String txnId = dcFinAdjustRequest.getTxnId();
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    final Gson gsonAdjustFormat =
        new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonUTCDateAdapter("yyyy-MM-dd'T'HH:mm:ss.SSSZ"))
            .create();
    final String adjustJsonRequest = gsonAdjustFormat.toJson(dcFinAdjustRequest);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        headers.get(ReceivingConstants.TENENT_FACLITYNUM).toString(),
        ReceivingConstants.OUTBOX_PATTERN_ENABLED,
        false)) {
      try {
        rapidRelayerService.produceHttpMessage(
            DCFIN_ADJUST,
            adjustJsonRequest,
            ReceivingUtils.convertHttpHeadersToHashMap(httpHeaders));
      } catch (ReceivingException e) {
        LOGGER.error(
            "{} DcFin:Adjust using outbox for CorrelationId={}, txnId={}, url={}, error: response={}",
            SPLUNK_ALERT,
            cId,
            txnId,
            url,
            e.getErrorResponse());
      }
    } else {
      RetryEntity retryEntity =
          jmsRecoveryService.putForRetries(
              url, POST, httpHeaders, adjustJsonRequest, DCFIN_ADJUST_OR_VTR);
      asyncPersister.asyncPost(cId, txnId, retryEntity, url, httpHeaders, adjustJsonRequest);
    }
  }

  private void throwDcFinException(RestClientResponseException e)
      throws DCFinRestApiClientException {
    if (400 == e.getRawStatusCode()) {
      DCFinRestApiClientErrorResponse errorResponse =
          gson.fromJson(e.getResponseBodyAsString(), DCFinRestApiClientErrorResponse.class);
      throw new DCFinRestApiClientException(
          StringUtils.join(errorResponse.getBody(), ";"),
          HttpStatus.valueOf(e.getRawStatusCode()),
          errorResponse.getMeta().getStatusCode());
    }

    throw new DCFinRestApiClientException(
        e.getResponseBodyAsString(), HttpStatus.valueOf(e.getRawStatusCode()));
  }

  /**
   * Check DcFin - PO is finalized or not.
   *
   * <pre>
   * poNumber
   * deliveryNumber
   * </pre>
   *
   * @return Boolean
   */
  @Timed(
      name = "isPoFinalizedInDcFin",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "isPoFinalizedInDcFin")
  @ExceptionCounted(
      name = "isPoFinalizedInDcFinExceptionCount",
      level1 = "uwms-receiving",
      level2 = "dcFinApiClient",
      level3 = "isPoFinalizedInDcFin")
  public Boolean isPoFinalizedInDcFin(String deliveryNumber, String poNumber)
      throws ReceivingException {
    ResponseEntity<String> finalizePoResponseEntity = null;
    Boolean isPoFinalizedInDcFin = false;
    String facilityNumber = TenantContext.getFacilityNum().toString();
    String facilityCountryCode = TenantContext.getFacilityCountryCode();
    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, facilityNumber);
    requestHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode);
    requestHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
    requestHeaders.add(ReceivingConstants.REQUEST_ORIGINATOR, ReceivingConstants.APP_NAME_VALUE);
    String url = appConfig.getDcFinBaseUrl() + DCFIN_POCLOSURE_STATE_URI;
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.PURCHASE_ORDER_NUMBER, poNumber);
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    URI dcFinClosureUri =
        UriComponentsBuilder.fromUriString(url).buildAndExpand(pathParams).toUri();
    HttpEntity<String> entity = new HttpEntity<>(requestHeaders);
    DCFinPOCloseStatusResponse dcFinPOCloseStatusResponse = null;
    LOGGER.info(
        "DcFin:Check PO Closure Status  for deliveryNumber={}, poNumber={}",
        deliveryNumber,
        poNumber);
    try {
      finalizePoResponseEntity =
          simpleRestConnector.exchange(dcFinClosureUri.toString(), GET, entity, String.class);

      if (finalizePoResponseEntity.getStatusCode().is2xxSuccessful()) {
        dcFinPOCloseStatusResponse =
            gson.fromJson(
                finalizePoResponseEntity.getBody().toString(), DCFinPOCloseStatusResponse.class);

        if (ObjectUtils.allNotNull(
                dcFinPOCloseStatusResponse,
                dcFinPOCloseStatusResponse.getBody(),
                dcFinPOCloseStatusResponse.getBody().getPoClosureStatus())
            && dcFinPOCloseStatusResponse
                .getBody()
                .getPoClosureStatus()
                .equalsIgnoreCase(InstructionStatus.COMPLETED.toString())) {
          isPoFinalizedInDcFin = true;
        } else {
          LOGGER.error(
              "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={} response={}",
              deliveryNumber,
              poNumber,
              dcFinPOCloseStatusResponse);
          throw new ReceivingException(
              String.format(ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE_DCFIN, poNumber),
              BAD_REQUEST,
              ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
              ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
        }
      } else if (finalizePoResponseEntity.getStatusCode().is4xxClientError()) {
        LOGGER.error(
            "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={},statusCode={}, response={}",
            deliveryNumber,
            poNumber,
            finalizePoResponseEntity.getStatusCode(),
            finalizePoResponseEntity);
        throw new ReceivingException(
            String.format("Invalid request to check PO=%s closure in DcFin.", poNumber),
            BAD_REQUEST,
            ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
            ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      } else {
        LOGGER.error(
            "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={},statusCode={}, response={}",
            deliveryNumber,
            poNumber,
            finalizePoResponseEntity.getStatusCode(),
            finalizePoResponseEntity);
        throw new ReceivingException(
            String.format("Error while checking PO=%s for closure status in DcFin.", poNumber),
            BAD_REQUEST,
            ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
            ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
      }

    } catch (RestClientResponseException e) {
      LOGGER.error(
          "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={}, URI={}, error: statusCode={}, response={}",
          deliveryNumber,
          poNumber,
          dcFinClosureUri,
          e.getRawStatusCode(),
          e.getResponseBodyAsString());
      throw new ReceivingException(
          String.format(
              "Unable to verify DCFin close status for po=%s, Please contact your supervisor or support.",
              poNumber),
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
    } catch (ReceivingException re) {
      LOGGER.error(
          "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={}, URI={}, response={}. errorMessage={}",
          deliveryNumber,
          poNumber,
          dcFinClosureUri,
          finalizePoResponseEntity,
          re.getMessage());
      throw new ReceivingException(
          re.getMessage(),
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
    } catch (Exception e) {
      LOGGER.error(
          "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={}, URI={}, response={}. errorMessage={}",
          deliveryNumber,
          poNumber,
          dcFinClosureUri,
          finalizePoResponseEntity,
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          String.format(
              "Exception while checking DCFin close status for po=%s, Please contact your supervisor or support.",
              poNumber),
          BAD_REQUEST,
          ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE);
    }

    LOGGER.info(
        "DcFin:Check PO Closure Status for deliveryNumber={}, poNumber={}, ResponseEntity={}, Result={}",
        deliveryNumber,
        poNumber,
        finalizePoResponseEntity,
        isPoFinalizedInDcFin);
    return isPoFinalizedInDcFin;
  }
}
