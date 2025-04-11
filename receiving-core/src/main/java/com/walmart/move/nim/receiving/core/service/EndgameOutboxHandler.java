package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ACCEPT;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_COUNTRY_CODE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENENT_FACLITYNUM;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static java.lang.String.valueOf;
import static java.util.Collections.emptyMap;
import static java.util.Objects.requireNonNull;
import static java.util.UUID.randomUUID;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EndgameOutboxHandler {

  @Autowired private IOutboxPublisherService iOutboxPublisherService;

  public void sentToOutbox(
      String messageString, String serviceName, Map<String, Object> parameters) {
    String correlationId = randomUUID().toString();
    Map<String, Object> headers = getHeaderMap(correlationId);
    Integer facilityNumber = Integer.parseInt(String.valueOf(headers.get(TENENT_FACLITYNUM)));
    String facilityCountryCode = String.valueOf(headers.get(TENENT_COUNTRY_CODE));
    iOutboxPublisherService.publishToHTTP(
        correlationId,
        messageString,
        headers,
        serviceName,
        facilityNumber,
        facilityCountryCode,
        parameters);
  }

  public void sentToOutbox(
      String messageString,
      String serviceName,
      Map<String, Object> parameters,
      Map<String, Object> headers) {
    String correlationId = String.valueOf(headers.get(CORRELATION_ID_HEADER_KEY));
    Integer facilityNumber = Integer.parseInt(String.valueOf(headers.get(TENENT_FACLITYNUM)));
    String facilityCountryCode = String.valueOf(headers.get(TENENT_COUNTRY_CODE));
    iOutboxPublisherService.publishToHTTP(
        correlationId,
        messageString,
        headers,
        serviceName,
        facilityNumber,
        facilityCountryCode,
        parameters);
  }

  public void sendToOutbox(String payload, String serviceName, HttpHeaders headers) {
    sendToOutbox(
        payload, serviceName, new HashMap<>(headers.toSingleValueMap()), TenantContext.getCorrelationId(), emptyMap());
  }

  public void sendToOutbox(
      String payload,
      String serviceName,
      Map<String, Object> headers,
      String partitionKey,
      Map<String, Object> uriVariables) {
    iOutboxPublisherService.publishToHTTP(
        randomUUID().toString(),
        payload,
        headers,
        serviceName,
        TenantContext.getFacilityNum(),
        TenantContext.getFacilityCountryCode(),
        uriVariables,
        partitionKey);
  }

  private Map<String, Object> getHeaderMap(String correlationId) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    Map<String, Object> headers = new HashMap<>();
    headers.put(ACCEPT, APPLICATION_JSON_VALUE);
    headers.put(CONTENT_TYPE, APPLICATION_JSON_VALUE);
    headers.put(CORRELATION_ID_HEADER_KEY, correlationId);
    headers.put(USER_ID_HEADER_KEY, httpHeaders.getFirst(USER_ID_HEADER_KEY));
    headers.put(
        TENENT_FACLITYNUM,
        valueOf(Integer.parseInt(requireNonNull(httpHeaders.getFirst(TENENT_FACLITYNUM)))));
    headers.put(TENENT_COUNTRY_CODE, (httpHeaders.getFirst(TENENT_COUNTRY_CODE)));
    return headers;
  }
}
