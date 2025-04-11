package com.walmart.move.nim.receiving.core.client.epcis;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.RESTUTILS_ERROR_MESSAGE;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EpcisRestClient {

  @ManagedConfiguration private AppConfig appConfig;

  private static final Logger LOGGER = LoggerFactory.getLogger(EpcisRestClient.class);
  private static final String EPCIS_PUBLISH_URI = "/events/captureMany";
  private static final String EPCIS_VERIFY_URL = "/events/validation";

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @Autowired private Gson gson;

  @Counted(
      name = "publishReceiveEventHitCount",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "publishReceiveEvent")
  @Timed(
      name = "publishReceiveEventTimed",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "publishReceiveEvent")
  @ExceptionCounted(
      name = "publishReceiveEventExceptionCounted",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "publishReceiveEvent")
  public void publishReceiveEvent(List<EpcisRequest> request, HttpHeaders requestHeaders) {
    String captureUrl = appConfig.getEpcisServiceBaseUrl() + EPCIS_PUBLISH_URI;
    ResponseEntity<String> epcisResponseEntity;
    String requestBody = gson.toJson(request);
    try {
      epcisResponseEntity =
          retryableRestConnector.post(
              captureUrl, requestBody, constructEpcisHeaders(requestHeaders), String.class);
      LOGGER.info("Succesfully published to EPCIS {}", epcisResponseEntity);
      LOGGER.info("ASN ATTP eventJson: {}", requestBody);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          captureUrl,
          requestBody,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          captureUrl,
          requestBody,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
    } catch (Exception e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE, captureUrl, "", e.getMessage(), ExceptionUtils.getStackTrace(e));
    }
  }

  private HttpHeaders constructEpcisHeaders(HttpHeaders requestHeaders) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.set(ReceivingConstants.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
    httpHeaders.set(
        ReceivingConstants.WM_CORRELATIONID,
        requestHeaders.getFirst(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    httpHeaders.set(
        ReceivingConstants.WM_USERID,
        requestHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    httpHeaders.set(
        ReceivingConstants.WM_SITEID,
        String.valueOf(requestHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM)));
    httpHeaders =
        ReceivingUtils.getServiceMeshHeaders(
            httpHeaders,
            appConfig.getEpcisConsumerId(),
            appConfig.getEpcisServiceName(),
            appConfig.getEpcisServiceEnv(),
            appConfig.getEpcisServiceVersion());
    return httpHeaders;
  }

  @Counted(
      name = "verifySerializedDataHitCount",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "verifySerializedData")
  @Timed(
      name = "verifySerializedDataTimed",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "verifySerializedData")
  @ExceptionCounted(
      name = "verifySerializedDataExceptionCounted",
      level1 = "uwms-receiving",
      level2 = "epcisRestClient",
      level3 = "verifySerializedData")
  public ResponseEntity<String> verifySerializedData(
      EpcisVerifyRequest epcisVerifyRequest, HttpHeaders requestHeaders) {
    String verifyUrl = appConfig.getEpcisServiceBaseUrl() + EPCIS_VERIFY_URL;
    ResponseEntity<String> verifyResponseEntity = null;
    try {
      verifyResponseEntity =
          retryableRestConnector.post(verifyUrl, epcisVerifyRequest, requestHeaders, String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          verifyUrl,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.EPCIS_VERIFY_ERROR,
          String.format(
              ReceivingConstants.EPCIS_BAD_RESPONSE_ERROR_MSG,
              e.getRawStatusCode(),
              e.getResponseBodyAsString()));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          verifyUrl,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));

      throw new ReceivingBadDataException(
          ExceptionCodes.EPCIS_VERIFY_ERROR,
          String.format(ReceivingConstants.SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    } catch (Exception e) {
      LOGGER.error(
          RESTUTILS_ERROR_MESSAGE, verifyUrl, "", e.getMessage(), ExceptionUtils.getStackTrace(e));
      throw new ReceivingBadDataException(
          ExceptionCodes.EPCIS_VERIFY_ERROR,
          String.format(ReceivingConstants.SLOTTING_RESOURCE_RESPONSE_ERROR_MSG, e.getMessage()));
    }

    return verifyResponseEntity;
  }
}
