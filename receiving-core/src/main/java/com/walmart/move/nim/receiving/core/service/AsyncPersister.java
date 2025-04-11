/** */
package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.constants.EventTargetStatus.DELETE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;

import com.walmart.move.nim.receiving.core.common.JMSSyncPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.utils.common.TenantData;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import io.strati.metrics.DuplicatedMetricsException;
import io.strati.metrics.InvalidArgumentException;
import io.strati.metrics.MetricDimensions;
import io.strati.metrics.MetricRegistry;
import io.strati.metrics.MetricsService;
import io.strati.metrics.TooManyRegisteredMetricsException;
import io.strati.metrics.instrument.Instrument;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.HandlerMethod;

/** @author a0b02ft */
@Component
public class AsyncPersister {
  private static final Logger log = LoggerFactory.getLogger(AsyncPersister.class);

  @Autowired private MetricsService metricsService;
  @Autowired private JMSSyncPublisher jmsSyncPublisher;
  @Autowired private RetryService jmsRecoveryService;
  @Autowired private RestUtils restUtils;

  @Async
  public void publishMetric(
      WebRequest request,
      ReceivingException ex,
      HandlerMethod handlerMethod,
      TenantData tenantData) {
    try {
      String facilityNumber = null;
      String correlationId = null;
      if (!Objects.isNull(tenantData)) {
        facilityNumber = tenantData.getFacilityCountryCode() + tenantData.getFacilityNum();
        correlationId = tenantData.getCorrelationId();
      }
      MetricRegistry metricRegistry = metricsService.getMetricRegistry();
      String controllerName = handlerMethod.getMethod().getDeclaringClass().getSimpleName();
      String qualifiedMethodClassName = ex.getStackTrace()[0].getClassName();
      String methodClassName =
          qualifiedMethodClassName.substring(qualifiedMethodClassName.lastIndexOf('.') + 1);
      String method = handlerMethod.getMethod().getName();
      Integer lineNumber = ex.getStackTrace()[0].getLineNumber();
      // removing special characters and space in the exceptionName,methodName,
      // causeForException
      // variables
      String methodName =
          controllerName
              + "-"
              + methodClassName
              + "."
              + method
              + "."
              + String.valueOf(lineNumber).replaceAll("[^a-zA-Z0-9.]+", "").trim().toLowerCase();
      String causeForException = ex.getMessage().replaceAll("[^a-zA-Z]+", "").trim().toLowerCase();
      // restricting exceptionName,methodName and causeForException to 255 characters
      String level1 =
          facilityNumber + "-" + methodName.substring(0, Math.min(methodName.length(), 248));
      String level2 = "CorrelationId-" + correlationId;
      String level3 = causeForException.substring(0, Math.min(causeForException.length(), 255));
      MetricDimensions dim1 = metricsService.createMetricDimensions(level1, level2, level3);
      metricRegistry
          .counter(dim1, "uwms-Exception-alert", Instrument.MeasurementUnit.REQUEST_COUNT, true)
          .inc();
    } catch (InvalidArgumentException
        | TooManyRegisteredMetricsException
        | DuplicatedMetricsException e) {
      log.error("Error while publishing metrics [error={}]", ExceptionUtils.getStackTrace(e));
    }
  }

  @Async
  public void publishMetric(String name, String level1, String level2, String level3) {
    try {
      MetricRegistry metricRegistry = metricsService.getMetricRegistry();
      MetricDimensions dim1 = metricsService.createMetricDimensions(level1, level2, level3);
      metricRegistry.counter(dim1, name, Instrument.MeasurementUnit.REQUEST_COUNT, true).inc();
      log.info(
          "Metrics published successfully for [name={}] [level1={}] [level2={}] [level3={}]",
          name,
          level1,
          level2,
          level3);
    } catch (InvalidArgumentException
        | TooManyRegisteredMetricsException
        | DuplicatedMetricsException e) {
      log.error("Error while publishing metrics [error={}]", ExceptionUtils.getStackTrace(e));
    }
  }

  /**
   * Async call to publish a single event and mark for deletion
   *
   * @param queueName
   * @param messageTemplate
   * @param eventRetryEntity
   * @param isPutForRetriesEnabled
   */
  @Async
  public void asyncPublish(
      String queueName,
      ReceivingJMSEvent messageTemplate,
      RetryEntity eventRetryEntity,
      Boolean isPutForRetriesEnabled) {
    // Existing BAU code for single event publish and mark for delete
    publishAnEventAndMarkForDelete(
        queueName, messageTemplate, eventRetryEntity, isPutForRetriesEnabled);
  }

  /**
   * call to publish a single event and mark for deletion can be used by single or multiple events
   * in a loop
   *
   * @param queueName
   * @param messageTemplate
   * @param eventRetryEntity
   * @param isPutForRetriesEnabled
   */
  private void publishAnEventAndMarkForDelete(
      String queueName,
      ReceivingJMSEvent messageTemplate,
      RetryEntity eventRetryEntity,
      Boolean isPutForRetriesEnabled) {
    try {
      // Publish message

      jmsSyncPublisher.publishInternal(queueName, messageTemplate);

    } catch (Exception exception) {
      log.error(
          "Error in async publishing message. Message detail: {}, error: {}",
          messageTemplate.getMessageBody(),
          ExceptionUtils.getStackTrace(exception));
      if (!isPutForRetriesEnabled) {
        jmsRecoveryService.putForRetries(queueName, messageTemplate);
      }
      return;
    }
    if (isPutForRetriesEnabled) {
      log.info("Mark as delete entity : {}", eventRetryEntity.getId());
      eventRetryEntity.setEventTargetStatus(DELETE);
      saveEvent(eventRetryEntity);
    }
  }

  /**
   * Async call to publish a multiple events and mark for deletion, sequentially (in given order)
   *
   * @param queueName
   * @param receivingJMSEventsSequentially this given sequence of order the events get published
   * @param isRetry
   */
  @Async
  public void asyncPublishSequentially(
      String queueName,
      ArrayList<ReceivingJMSEvent> receivingJMSEventsSequentially,
      Boolean isRetry) {
    for (ReceivingJMSEvent messageTemplate : receivingJMSEventsSequentially) {
      RetryEntity eventRetryEntity = jmsRecoveryService.putForRetries(queueName, messageTemplate);
      publishAnEventAndMarkForDelete(queueName, messageTemplate, eventRetryEntity, isRetry);
    }
  }
  /**
   * This is an async method which makes a rest call. If call is successful RetryEntity is marked as
   * deleted. If call fails, RetryEntity is still in pending state and is later picked up by
   * scheduler. Before calling this method RetryEntity should be committed in DB. Ensure that this
   * method is preceded by jmsRecoveryService.putForRetries. Ensure that the parent method which
   * invokes jmsRecoveryService.putForRetries and this is not transactional.
   *
   * @param cId
   * @param txnId
   * @param eventRetryEntity
   * @param url
   * @param httpHeaders
   * @param payload
   */
  @Async
  public void asyncPost(
      String cId,
      String txnId,
      RetryEntity eventRetryEntity,
      String url,
      HttpHeaders httpHeaders,
      String payload) {

    final Long id = eventRetryEntity.getId();
    log.info(
        "DcFin:call for [cId={}] [txnId={}] [dBid={}] [url={}] [payload={}] [httpHeaders={}]",
        cId,
        txnId,
        id,
        url,
        payload,
        httpHeaders);
    ResponseEntity<String> response = restUtils.post(url, httpHeaders, new HashMap<>(), payload);
    final boolean isSuccess = response.getStatusCode().is2xxSuccessful();
    if (isSuccess) {
      log.info(
          "DcFin:call for [cId={}] [txnId={}] [id={}] [url={}] [request={}] [response={}] [responseCode={}] Marking as delete",
          cId,
          txnId,
          id,
          url,
          payload,
          response,
          response.getStatusCode());
      eventRetryEntity.setEventTargetStatus(DELETE);
      saveEvent(eventRetryEntity);
    } else {
      log.warn(
          "DcFin:call failed but RetryJob will trigger for [cId={}] [txnId={}] [id={}], [url={}] [request={}] [response={}]",
          cId,
          txnId,
          id,
          url,
          payload,
          response);
    }
  }

  @Transactional(propagation = Propagation.NEVER)
  public void saveEvent(RetryEntity eventRetryEntity) {
    jmsRecoveryService.save(eventRetryEntity);
  }

  /**
   * This is Async method calls persistHttpCall
   *
   * @param httpMethod
   * @param url
   * @param request
   * @param httpHeaders
   */
  @Async
  public void persistAsyncHttp(
      HttpMethod httpMethod,
      String url,
      String request,
      HttpHeaders httpHeaders,
      RetryTargetFlow retryTargetFlow) {
    persistHttpCall(httpMethod, url, request, httpHeaders, retryTargetFlow);
  }

  /**
   * This is sync httpMethod first persists request data for re-try and makes HTTP calls(POST,PUT)
   * If call is successful then marks the previously persisted data to delete. If call fails then
   * previously persisted request data will be picked by scheduler to retry
   *
   * @param httpMethod user given http Method
   * @param url fully qualified URL
   * @param request request payload as json string
   * @param httpHeaders
   */
  public void persistHttpCall(
      HttpMethod httpMethod,
      String url,
      String request,
      HttpHeaders httpHeaders,
      RetryTargetFlow retryTargetFlow) {
    final String cId = httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY);
    RetryEntity eventRetryEntity =
        jmsRecoveryService.putForRetries(url, httpMethod, httpHeaders, request, retryTargetFlow);
    final Long id = eventRetryEntity.getId();

    log.info(
        "persistHttpCall {} call for cId={}, id={}, url={}, request={}",
        httpMethod,
        cId,
        id,
        url,
        request);

    ResponseEntity<String> re = null;
    switch (httpMethod) {
      case POST:
        re = restUtils.post(url, httpHeaders, new HashMap<>(), request);
        log.info(
            "POST response for cId={},id={},url={},code={},body={}",
            cId,
            id,
            url,
            re.getStatusCode(),
            re.getBody());
        persistHttpResponseHandle(cId, httpMethod, id, eventRetryEntity, re);
        break;
      case PUT:
        re = restUtils.put(url, httpHeaders, new HashMap<>(), request);
        log.info(
            "PUT response for cId={},id={},url={},code={},body={}",
            cId,
            id,
            url,
            re.getStatusCode(),
            re.getBody());

        persistHttpResponseHandle(cId, httpMethod, id, eventRetryEntity, re);
        break;
      default:
        log.error("persistHttpCall {} call not implemented yet", httpMethod);
        break;
    }
  }

  private void persistHttpResponseHandle(
      String cId,
      HttpMethod httpMethod,
      Long id,
      RetryEntity eventRetryEntity,
      ResponseEntity<String> response) {
    final boolean isSuccessful = response.getStatusCode().is2xxSuccessful();

    if (isSuccessful) {
      log.info(
          (isSuccessful ? "Success" : "Invalid request caused Failed")
              + " {} call for cId={} mark id={} to delete",
          httpMethod,
          cId,
          id);
      eventRetryEntity.setEventTargetStatus(DELETE);
      saveEvent(eventRetryEntity);
    } else {
      log.info("failed {} call, will re-try for cId={}, id={}", httpMethod, cId, id);
    }
  }
}
