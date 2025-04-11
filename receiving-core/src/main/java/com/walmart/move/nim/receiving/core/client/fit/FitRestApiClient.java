package com.walmart.move.nim.receiving.core.client.fit;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.replacePathParams;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FIXIT_ENABLED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FIXIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_V1_URI;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for Fit Rest API
 *
 * @author v0k00fe
 */
@Component
public class FitRestApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(FitRestApiClient.class);

  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "restConnector")
  private RestConnector restConnector;

  @ManagedConfiguration private AppConfig appConfig;

  /**
   * Finds Problem count by Delivery and Problem Count is calculated by PO/PO Line.
   *
   * @param deliveryNumber
   * @return
   * @throws FitRestApiClientException
   */
  @Timed(
      name = "findProblemCountByDeliveryTimed",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "findProblemCountByDelivery")
  @ExceptionCounted(
      name = "findProblemCountByDeliveryExceptionCount",
      level1 = "uwms-receiving",
      level2 = "gdmApiClient",
      level3 = "findProblemCountByDelivery")
  public Optional<ProblemCountByDeliveryResponse> findProblemCountByDelivery(
      Long deliveryNumber, Map<String, Object> forwardableHeaders)
      throws FitRestApiClientException {

    HttpHeaders requestHeaders = new HttpHeaders();
    requestHeaders.set(
        ReceivingConstants.USER_ID_HEADER_KEY,
        forwardableHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).toString());
    requestHeaders.set(
        ReceivingConstants.TENENT_FACLITYNUM,
        forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString());
    requestHeaders.set(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        forwardableHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).toString());
    requestHeaders.set(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY,
        forwardableHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString());
    requestHeaders.set(ReceivingConstants.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber.toString());

    String baseUrl;
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        forwardableHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).toString(),
        FIXIT_ENABLED,
        false)) {
      requestHeaders =
          ReceivingUtils.getServiceMeshHeaders(
              requestHeaders,
              appConfig.getReceivingConsumerId(),
              appConfig.getFixitServiceName(),
              appConfig.getFixitServiceEnv());
      baseUrl =
          appConfig.getFixitPlatformBaseUrl()
              + PROBLEM_V1_URI
              + FIXIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI;
    } else {
      baseUrl = appConfig.getFitBaseUrl() + PROBLEM_V1_URI + FIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI;
    }

    String uri = replacePathParams(baseUrl, pathParams).toString();
    LOGGER.info(
        "Get problems statistics by delivery URI = {}, requestHeaders = {}", uri, requestHeaders);

    ResponseEntity<String> problemCntByDeliveryResponseEntity = null;
    try {
      problemCntByDeliveryResponseEntity =
          restConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(requestHeaders), String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          "Get problems statistics with deliveryNumber={} got responseCode={} & response={}",
          deliveryNumber,
          e.getRawStatusCode(),
          e.getResponseBodyAsString());

      if (HttpStatus.NOT_FOUND.equals(HttpStatus.valueOf(e.getRawStatusCode()))) {
        return Optional.empty();
      }
      throw new FitRestApiClientException(e.getMessage(), HttpStatus.valueOf(e.getRawStatusCode()));
    }

    LOGGER.info(
        "Get problems statistics with deliveryNumber={} got response={}",
        deliveryNumber,
        problemCntByDeliveryResponseEntity.getBody());

    return Optional.ofNullable(
        gson.fromJson(
            problemCntByDeliveryResponseEntity.getBody(), ProblemCountByDeliveryResponse.class));
  }
}
