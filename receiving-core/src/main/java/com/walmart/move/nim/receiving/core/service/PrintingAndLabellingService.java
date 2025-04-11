package com.walmart.move.nim.receiving.core.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintableLabelDataRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class PrintingAndLabellingService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrintingAndLabellingService.class);

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private Gson gson;

  public void postToLabelling(List<PrintableLabelDataRequest> request, HttpHeaders headers) {
    headers.set("WMT_ReqOriginTs", new Date().toString());
    headers.set("WMT_UserId", headers.getFirst(ReceivingConstants.USER_ID_HEADER_KEY));
    headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
    ResponseEntity<String> response = null;
    String uri =
        tenantSpecificConfigReader.getLabellingServiceUrlOrDefault(
            () -> appConfig.getLabellingServiceBaseUrl(),
            headers.getFirst(ReceivingConstants.FACILITY_NUM));

    String body = JacksonParser.writeValueAsStringExcludeNull(request);
    try {
      response = restConnector.post(uri, body, headers, String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
    } catch (ResourceAccessException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
    }
    if (response == null) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          null);
    } else {
      LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, body, response.getBody());
    }
  }

  public void postToLabellingInBatches(
      List<PrintableLabelDataRequest> printableLabelDataRequests, HttpHeaders headers) {
    Collection<List<PrintableLabelDataRequest>> partitionedRequestList =
        ReceivingUtils.batchifyCollection(
            printableLabelDataRequests, appConfig.getLabellingServiceCallBatchCount());
    LOGGER.info("postToLabellingInBatches: Number of batch : {}", partitionedRequestList.size());
    for (List<PrintableLabelDataRequest> request : partitionedRequestList) {
      postToLabelling(request, headers);
    }
  }

  public PrintableLabelDataRequest getPrintableLabelDataRequest(Map<String, Object> printRequest) {
    PrintableLabelDataRequest printableLabelDataRequest = new PrintableLabelDataRequest();
    printableLabelDataRequest.setClientId("RCV");
    printableLabelDataRequest.setFormatName(
        printRequest.get(ReceivingConstants.PRINT_LABEL_FORMAT_NAME).toString());
    printableLabelDataRequest.setLabelIdentifier(
        printRequest.get(ReceivingConstants.PRINT_LABEL_IDENTIFIER).toString());
    printableLabelDataRequest.setTtlInHours(appConfig.getLabelTTLInHours());
    printableLabelDataRequest.setPrintRequest(Boolean.FALSE);
    Type type = new TypeToken<List<Pair<String, String>>>() {}.getType();
    JsonArray printData =
        new JsonParser()
            .parse(gson.toJson(printRequest.get(ReceivingConstants.PRINT_DATA)))
            .getAsJsonArray();
    printableLabelDataRequest.setLabelData(gson.fromJson(printData, type));
    return printableLabelDataRequest;
  }
}
