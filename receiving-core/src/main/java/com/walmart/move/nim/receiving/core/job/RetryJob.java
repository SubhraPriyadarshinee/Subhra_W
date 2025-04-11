/** */
package com.walmart.move.nim.receiving.core.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNodeCondition;
import com.walmart.move.nim.receiving.core.common.JmsPublisher;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.model.RestRetryRequest;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.Counted;
import java.lang.reflect.Type;
import java.util.*;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * This is a CRON job and will be responsible for retry the JMS Event
 *
 * @author sitakant
 */
@ConditionalOnExpression("${enable.jms.retry.job:true}")
@Conditional(EnableInPrimaryRegionNodeCondition.class)
@Component
public class RetryJob {
  private static final Logger LOG = LoggerFactory.getLogger(RetryJob.class);
  @Autowired private RetryService jmsRecoveryService;

  @Autowired private JmsPublisher jmsPublisher;

  @Autowired private RestUtils restUtils;

  @Autowired private AsyncPersister asyncPersister;

  @ManagedConfiguration private AppConfig appConfig;

  @Value("${secrets.key}")
  public String secretKey;
  // prevent Gson from expressing integers as floats

  private Gson gson =
      new GsonBuilder()
          .registerTypeAdapter(
              new TypeToken<Map<String, Object>>() {}.getType(),
              new JsonDeserializer<Map<String, Object>>() {

                @Override
                @SuppressWarnings("unchecked")
                public Map<String, Object> deserialize(
                    JsonElement json, Type typeOfT, JsonDeserializationContext context) {
                  return (Map<String, Object>) read(json);
                }

                public Object read(JsonElement in) {

                  if (in.isJsonArray()) {
                    List<Object> list = new ArrayList<>();
                    JsonArray arr = in.getAsJsonArray();
                    for (JsonElement anArr : arr) {
                      list.add(read(anArr));
                    }
                    return list;
                  } else if (in.isJsonObject()) {
                    Map<String, Object> map = new LinkedTreeMap<>();
                    JsonObject obj = in.getAsJsonObject();
                    Set<Map.Entry<String, JsonElement>> entitySet = obj.entrySet();
                    for (Map.Entry<String, JsonElement> entry : entitySet) {
                      map.put(entry.getKey(), read(entry.getValue()));
                    }
                    return map;
                  } else if (in.isJsonPrimitive()) {
                    JsonPrimitive prim = in.getAsJsonPrimitive();
                    if (prim.isBoolean()) {
                      return prim.getAsBoolean();
                    } else if (prim.isString()) {
                      return prim.getAsString();
                    } else if (prim.isNumber()) {
                      Number num = prim.getAsNumber();
                      if (Math.ceil(num.doubleValue()) == num.longValue()) return num.longValue();
                      else {
                        return num.doubleValue();
                      }
                    }
                  }
                  return null;
                }
              })
          .create();

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(
      name = "${shedlock.prefix:}" + "JmsRetryJob_retriesSchedule",
      lockAtLeastFor = 60000,
      lockAtMostFor = 90000)
  @Counted(name = "jmsRetryjobCount", level1 = "uwms-receiving")
  public void retriesSchedule() {
    // Picks the pending JMS events
    List<RetryEntity> applicationRetriesEntities = getPendingEntries();
    LOG.info("JMS total entries to retry = {}", applicationRetriesEntities.size());
    // Publish the messages

    applicationRetriesEntities.forEach(
        applicationRetriesEntity -> {
          LOG.info("(JOB) retrying {}", applicationRetriesEntity.getId());
          ReceivingJMSEvent payload =
              gson.fromJson(applicationRetriesEntity.getPayload(), ReceivingJMSEvent.class);
          jmsPublisher.publishRetries(
              applicationRetriesEntity.getJmsQueueName(), applicationRetriesEntity, payload);
        });

    // Picks the pending REST events
    List<RetryEntity> restRetriesEntities = getPendingRestEntries();
    LOG.info("Rest total entries to retry = {}", restRetriesEntities.size());
    // Retry REST
    restRetriesEntities.forEach(
        retryEntity -> {
          LOG.info(
              "Job restRetries [id={}] [retriesCount={}]",
              retryEntity.getId(),
              retryEntity.getRetriesCount());
          RestRetryRequest request =
              gson.fromJson(retryEntity.getPayload(), RestRetryRequest.class);

          retryRest(retryEntity, request);
        });
  }

  @Scheduled(fixedDelay = 60000)
  @SchedulerLock(
      name = "${shedlock.prefix:}" + "JmsRetryJob_identifyAndMarkStale",
      lockAtMostFor = 90000,
      lockAtLeastFor = 60000)
  public void identifyStaleAndMarkThemAsPending() {
    if (appConfig.getMarkRetryEventAsStaleRunEveryMin() == 0) {
      LOG.info("Identify Stale Events and Mark them as Pending is disabled. Returning");
      return;
    }

    Calendar cal = Calendar.getInstance();
    if (cal.get(Calendar.MINUTE) % appConfig.getMarkRetryEventAsStaleRunEveryMin() != 0) {
      LOG.info(
          "Identify Stale Events and Mark them as Pending is not enabled to run at {}. Returning",
          cal.getTime());
      return;
    }

    LOG.info("Identify Stale Events and Mark them as Pending : Starting");
    jmsRecoveryService.findAndUpdateStaleEntries();
  }

  public void retryRest(RetryEntity retryEntity, RestRetryRequest request) {
    ResponseEntity<String> response = null;

    String strHttpHeaders = null;
    try {
      strHttpHeaders = SecurityUtil.decryptValue(secretKey, request.getHttpHeaders());
    } catch (Exception ex) {
      strHttpHeaders = request.getHttpHeaders();
      LOG.error(
          ReceivingConstants.DECRYPTION_ERROR_MESSAGE,
          "http headers",
          request.getHttpHeaders(),
          ExceptionUtils.getStackTrace(ex));
    }
    HttpHeaders headers = gson.fromJson(strHttpHeaders, HttpHeaders.class);

    final String requestBody = request.getBody();
    final HttpMethod method = request.getHttpMethodType();
    LOG.info(
        "Job restRetries [url={}] [method={}] [payload={}]", request.getUrl(), method, requestBody);

    switch (method) {
      case POST:
        response = restUtils.post(request.getUrl(), headers, null, requestBody);
        break;
      case PUT:
        response = restUtils.put(request.getUrl(), headers, null, requestBody);
        break;
      default:
        break;
    }
    if ((Objects.isNull(response) || !response.getStatusCode().is2xxSuccessful())
        && Objects.nonNull(retryEntity.getRetryTargetFlow())
        && retryEntity.getRetriesCount() >= appConfig.getRestMaxRetries()
        && Objects.isNull(retryEntity.getIsAlerted())) {
      LOG.error(
          "Job restRetries alert for [id={}] [retriesCount={}] [method={}] [url={}] [headers={}] [request={}] [response={}]",
          retryEntity.getId(),
          retryEntity.getRetriesCount(),
          method,
          request.getUrl(),
          headers,
          request,
          response);
      String metricName =
          retryEntity.getRetryTargetFlow().name() + ReceivingConstants.EXCEPTION_COUNT;
      asyncPersister.publishMetric(metricName, "uwms-receiving", "RetryJob", "retryRest");
    }

    if (Objects.isNull(response) || !response.getStatusCode().is2xxSuccessful()) {
      final Long retriesCount = retryEntity.getRetriesCount();

      if (retriesCount < appConfig.getRestMaxRetries()) {
        LOG.warn(
            "Job restRetries [id={}] [retriesCount={}] [method={}] [url={}] [headers={}] [request={}] [response={}]",
            retryEntity.getId(),
            retryEntity.getRetriesCount(),
            method,
            request.getUrl(),
            headers,
            request,
            response);
      } else {
        LOG.error(
            "Job restRetries alert for [id={}] [retriesCount={}] [method={}] [url={}] [headers={}] [request={}] [response={}]",
            retryEntity.getId(),
            retryEntity.getRetriesCount(),
            method,
            request.getUrl(),
            headers,
            request,
            response);
        retryEntity.setIsAlerted(true);
        asyncPersister.publishMetric("retry_exhausted", "uwms-receiving", "RetryJob", "retryRest");
      }

      // Mark the record as pending and return
      retryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
      jmsRecoveryService.save(retryEntity);
      return;
    }
    // Delete
    LOG.info("Job restRetries will mark as delete entity: {}", retryEntity.getId());
    retryEntity.setEventTargetStatus(EventTargetStatus.DELETE);
    jmsRecoveryService.save(retryEntity);
  }

  public List<RetryEntity> getPendingRestEntries() {
    return jmsRecoveryService.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
        RetryTargetType.REST, EventTargetStatus.PENDING, appConfig.getRestMaxRetries());
  }

  public List<RetryEntity> getPendingEntries() {
    return jmsRecoveryService.findAndUpdateByRetryTargetTypeAndEventTargetStatus(
        RetryTargetType.JMS, EventTargetStatus.PENDING);
  }
}
