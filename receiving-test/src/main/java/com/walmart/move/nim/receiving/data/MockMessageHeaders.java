package com.walmart.move.nim.receiving.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.FLOW_DESCRIPTOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REQUEST_ORIGINATOR;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.MessageHeaders;

public class MockMessageHeaders {

  private static final String facilityNum = "32818";
  private static final String facilityCountryCode = "US";
  private static final String userId = "sysadmin";
  private static final String requestOriginator = "inventory-api";

  public static MessageHeaders getHeaders() {
    Map<String, Object> headersMap = new HashMap<String, Object>();
    final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    headersMap.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).get(0));
    headersMap.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).get(0));
    headersMap.put(
        ReceivingConstants.JMS_USER_ID,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    headersMap.put(
        ReceivingConstants.JMS_CORRELATION_ID,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));

    headersMap.put(REQUEST_ORIGINATOR, httpHeaders.get(REQUEST_ORIGINATOR).get(0));

    String flowDescriptor =
        "{\"flowName\":\"SPLIT_PALLET_TRANSFER\",\"businessEvent\":\"INVENTORY_TRANSFER\",\"sourceCntrTrackingId\":\"J3261200001\",\"destCntrTrackingIds\":[\"J3261200002\"]}";
    headersMap.put(FLOW_DESCRIPTOR, flowDescriptor);

    MessageHeaders messageHeaders = new MessageHeaders(headersMap);
    return messageHeaders;
  }

  public static Map<String, Object> getHeadersMap() {
    Map<String, Object> headersMap = new HashMap<String, Object>();
    final HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    headersMap.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        httpHeaders.get(ReceivingConstants.TENENT_FACLITYNUM).get(0));
    headersMap.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        httpHeaders.get(ReceivingConstants.TENENT_COUNTRY_CODE).get(0));
    headersMap.put(
        ReceivingConstants.JMS_USER_ID,
        httpHeaders.get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    headersMap.put(
        ReceivingConstants.JMS_CORRELATION_ID,
        httpHeaders.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));

    headersMap.put(REQUEST_ORIGINATOR, httpHeaders.get(REQUEST_ORIGINATOR).get(0));

    return headersMap;
  }

  public static MessageHeaders getHeadersWithoutTenantInformation() {
    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put(
        ReceivingConstants.JMS_USER_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    headers.put(
        ReceivingConstants.JMS_CORRELATION_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));
    MessageHeaders messageHeaders = new MessageHeaders(headers);
    return messageHeaders;
  }

  public static MessageHeaders getHeadersWithEmptyStringAsTenantInformation() {
    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put(ReceivingConstants.TENENT_FACLITYNUM, "");
    headers.put(ReceivingConstants.TENENT_COUNTRY_CODE, "");
    headers.put(
        ReceivingConstants.JMS_USER_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    headers.put(
        ReceivingConstants.JMS_CORRELATION_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));
    MessageHeaders messageHeaders = new MessageHeaders(headers);
    return messageHeaders;
  }

  public static MessageHeaders getHeadersWithoutFacilityNum() {
    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE).get(0));
    headers.put(
        ReceivingConstants.JMS_USER_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    headers.put(
        ReceivingConstants.JMS_CORRELATION_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).get(0));
    MessageHeaders messageHeaders = new MessageHeaders(headers);
    return messageHeaders;
  }

  public static MessageHeaders getHeadersWithoutCorrelationId() {
    Map<String, Object> headers = new HashMap<String, Object>();
    headers.put(
        ReceivingConstants.TENENT_FACLITYNUM,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.TENENT_FACLITYNUM).get(0));
    headers.put(
        ReceivingConstants.TENENT_COUNTRY_CODE,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.TENENT_COUNTRY_CODE).get(0));
    headers.put(
        ReceivingConstants.JMS_USER_ID,
        MockHttpHeaders.getHeaders().get(ReceivingConstants.USER_ID_HEADER_KEY).get(0));
    MessageHeaders messageHeaders = new MessageHeaders(headers);
    return messageHeaders;
  }

  public static Map<String, byte[]> getMockKafkaListenerHeaders() {
    Map<String, byte[]> mapHeaders = new HashMap<>();
    mapHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode.getBytes());
    mapHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    mapHeaders.put(ReceivingConstants.JMS_CORRELATION_ID, UUID.randomUUID().toString().getBytes());
    mapHeaders.put(ReceivingConstants.JMS_USER_ID, userId.getBytes());
    mapHeaders.put(
        ReceivingConstants.INVENTORY_EVENT, (ReceivingConstants.INVENTORY_EVENT_MOVED).getBytes());
    mapHeaders.put(ReceivingConstants.REQUEST_ORIGINATOR, requestOriginator.getBytes());
    mapHeaders.put(ReceivingConstants.EVENT_TYPE, "LABELS_GENERATED".getBytes());
    mapHeaders.put(ReceivingConstants.TOTAL_MESSAGE_COUNT, "1".getBytes());
    return mapHeaders;
  }

  public static Map<String, byte[]> getKafkaHeadersForLabelGroupEvent() {
    Map<String, byte[]> mapHeaders = new HashMap<>();
    mapHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, facilityCountryCode.getBytes());
    mapHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    mapHeaders.put(ReceivingConstants.JMS_CORRELATION_ID, UUID.randomUUID().toString().getBytes());
    mapHeaders.put(ReceivingConstants.JMS_USER_ID, userId.getBytes());
    mapHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString().getBytes());
    mapHeaders.put(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA".getBytes());
    return mapHeaders;
  }
}
