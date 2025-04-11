package com.walmart.move.nim.receiving.core.mock.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.RestRetryRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.HashMap;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

public class MockRetryEntity {
  private static Logger LOG = LoggerFactory.getLogger(MockRetryEntity.class);

  public static RetryEntity getForRestRetry() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, "a1-b1-c2");
    String strHttpHeaders = null;
    try {
      strHttpHeaders = SecurityUtil.encryptValue("", new Gson().toJson(httpHeaders));
      LOG.info("Successfully encrypted {} {}", new Gson().toJson(httpHeaders), strHttpHeaders);
    } catch (Exception e) {
      strHttpHeaders = new Gson().toJson(httpHeaders);
      LOG.error(
          ReceivingConstants.ENCRYPTION_ERROR_MESSAGE,
          "http headers",
          httpHeaders,
          ExceptionUtils.getStackTrace(e));
    }
    RestRetryRequest restRetryRequest =
        new RestRetryRequest(
            "http://localhost:8080/dcfin",
            HttpMethod.POST,
            strHttpHeaders,
            new String("{\"a\":\"a\"}"));
    RetryEntity restRetryEntity = new RetryEntity();
    restRetryEntity.setId(1L);
    restRetryEntity.setRetriesCount(0L);
    restRetryEntity.setRetryTargetType(RetryTargetType.REST);
    restRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
    restRetryEntity.setPayload(new Gson().toJson(restRetryRequest));
    return restRetryEntity;
  }

  public static RetryEntity getForRestRetryWithoutEncoding() {
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.DCFIN_WMT_API_KEY, "a1-b1-c2");
    String strHttpHeaders = new Gson().toJson(httpHeaders);
    RestRetryRequest restRetryRequest =
        new RestRetryRequest(
            "http://localhost:8080/dcfin",
            HttpMethod.POST,
            strHttpHeaders,
            new String("{\"a\":\"a\"}"));
    RetryEntity restRetryEntity = new RetryEntity();
    restRetryEntity.setId(1L);
    restRetryEntity.setRetriesCount(0L);
    restRetryEntity.setRetryTargetType(RetryTargetType.REST);
    restRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
    restRetryEntity.setPayload(new Gson().toJson(restRetryRequest));
    return restRetryEntity;
  }

  public static RetryEntity getForJmsRetry() {
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
    RetryEntity jmsEventRetryEntity = new RetryEntity();
    jmsEventRetryEntity.setId(0L);
    jmsEventRetryEntity.setJmsQueueName("QUEUE.TEST");
    jmsEventRetryEntity.setRetryTargetType(RetryTargetType.JMS);
    jmsEventRetryEntity.setIsAlerted(true);
    jmsEventRetryEntity.setPayload(new Gson().toJson(receivingJMSEvent));
    jmsEventRetryEntity.setRetriesCount(0L);
    jmsEventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
    return jmsEventRetryEntity;
  }

  public static RetryEntity getInRetryJmsEvents() {
    ReceivingJMSEvent receivingJMSEvent = new ReceivingJMSEvent(new HashMap<>(), "Hello Test");
    RetryEntity jmsEventRetryEntity = new RetryEntity();
    jmsEventRetryEntity.setId(0L);
    jmsEventRetryEntity.setJmsQueueName("QUEUE.TEST");
    jmsEventRetryEntity.setRetryTargetType(RetryTargetType.JMS);
    jmsEventRetryEntity.setIsAlerted(true);
    jmsEventRetryEntity.setPayload(new Gson().toJson(receivingJMSEvent));
    jmsEventRetryEntity.setRetriesCount(1L);
    jmsEventRetryEntity.setEventTargetStatus(EventTargetStatus.IN_RETRY);
    return jmsEventRetryEntity;
  }
}
