package com.walmart.move.nim.receiving.core.client.printlabel;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelServiceResponse;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Client for Printing and Labelling Rest API
 *
 * @author vn50o7n
 */
@Component
public class PrintLabelRestApiClient {

  private static final Logger log = LoggerFactory.getLogger(PrintLabelRestApiClient.class);

  private static final String GET_PRINT_LABEL = "/labels?labelIdentifier={labelIdentifier}";

  public static final String PRINTLABEL_CONTENT_TYPE = "application/vnd.printlabel+json";

  @Resource(name = "retryableRestConnector")
  private RestConnector retryableRestConnector;

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private Gson gson;

  /**
   * Gets Print Label for given LPN
   *
   * @param
   * @return
   */
  @Timed(
      name = "getPrintLabelTimed",
      level1 = "uwms-receiving",
      level2 = "printLabelApiClient",
      level3 = "getPrintLabel")
  @ExceptionCounted(
      name = "getPrintLabelExceptionCount",
      level1 = "uwms-receiving",
      level2 = "getPrintLabelApiClient",
      level3 = "getPrintLabel")
  public PrintLabelServiceResponse getPrintLabel(String trackingId, Map<String, Object> headers)
      throws ReceivingException {
    HttpHeaders printLabelHeaders = new HttpHeaders();
    printLabelHeaders.set(TENENT_FACLITYNUM, headers.get(TENENT_FACLITYNUM).toString());
    printLabelHeaders.set(TENENT_COUNTRY_CODE, headers.get(TENENT_COUNTRY_CODE).toString());
    printLabelHeaders.set(
        PRINT_LABEL_CORRELATION_ID, headers.get(CORRELATION_ID_HEADER_KEY).toString());
    printLabelHeaders.set(PRINT_LABEL_USER_ID, headers.get(USER_ID_HEADER_KEY).toString());
    printLabelHeaders.set(HttpHeaders.ACCEPT, PRINTLABEL_CONTENT_TYPE);
    Map<String, String> pathParams = new HashMap<>();
    pathParams.put(PRINT_LABEL_IDENTIFIER, trackingId);

    String uri =
        ReceivingUtils.replacePathParams(appConfig.getLpaasBaseUrl() + GET_PRINT_LABEL, pathParams)
            .toString();

    log.info("Get Print Label URI = {}", uri);

    ResponseEntity<String> responseEntity = null;
    try {
      responseEntity =
          retryableRestConnector.exchange(
              uri, HttpMethod.GET, new HttpEntity<>(printLabelHeaders), String.class);
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      throw e;
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      throw e;
    }
    final String jsonBody = responseEntity.getBody();
    log.info("For lpn={} got response={} from Print Label service", trackingId, jsonBody);

    final PrintLabelServiceResponse[] printLabelServiceResponses =
        gson.fromJson(jsonBody, PrintLabelServiceResponse[].class);

    if (printLabelServiceResponses == null || printLabelServiceResponses.length == 0) {
      log.error(
          "getPrintLabel print service returned null or empty for trackingId = {} printLabelServiceResponses=",
          trackingId,
          printLabelServiceResponses);
      throw new ReceivingException(
          ADJUST_PALLET_QUANTITY_ERROR_MSG,
          NOT_FOUND,
          ADJUST_PALLET_QUANTITY_ERROR_CODE,
          ADJUST_PALLET_QUANTITY_ERROR_HEADER);
    }

    return printLabelServiceResponses[0]; // return fist print object
  }
}
