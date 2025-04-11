package com.walmart.move.nim.receiving.core.common;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.message.service.RetryService;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class is to publish the event in the MaaS Queue/Topic
 *
 * @author sitakant
 */
@Component
public class JmsPublisher {
  private static final Logger log = LoggerFactory.getLogger(JmsPublisher.class);

  @ManagedConfiguration private AppConfig appConfig;

  @Autowired private RetryService jmsRecoveryService;

  @Autowired private AsyncPersister asyncPersister;

  @Autowired private JMSSyncPublisher jmsSyncPublisher;

  private Gson gson;

  public JmsPublisher() {
    gson = new Gson();
  }

  /**
   * This will publish the event to the queue/topic.
   *
   * @param queueName is the name of topic / queue
   * @param messageTemplate
   */
  public void publish(
      String queueName, ReceivingJMSEvent messageTemplate, Boolean isPutForRetriesEnabled) {

    if (appConfig.getJmsAsyncPublishEnabled()) {
      RetryEntity eventRetryEntity = null;
      if (isPutForRetriesEnabled) {
        eventRetryEntity = jmsRecoveryService.putForRetries(queueName, messageTemplate);
      }
      asyncPersister.asyncPublish(
          queueName, messageTemplate, eventRetryEntity, isPutForRetriesEnabled);
    } else {
      try {
        jmsSyncPublisher.publishInternal(queueName, messageTemplate);
      } catch (Exception exception) {
        log.error(
            "Unable to publish to queue {} {}",
            gson.toJson(messageTemplate),
            ExceptionUtils.getStackTrace(exception));
      }
    }
  }

  /**
   * @param queueName
   * @param events
   * @param isRetry
   */
  public void publishSequentially(
      String queueName, ArrayList<ReceivingJMSEvent> events, Boolean isRetry) {

    if (appConfig.getJmsAsyncPublishEnabled()) {
      asyncPersister.asyncPublishSequentially(queueName, events, isRetry);
    } else {
      for (ReceivingJMSEvent messageTemplate : events) {
        try {
          jmsSyncPublisher.publishInternal(queueName, messageTemplate);
        } catch (Exception exception) {
          log.error(
              "Unable to publish to queue {} {}",
              gson.toJson(messageTemplate),
              ExceptionUtils.getStackTrace(exception));
        }
      }
    }
  }
  /**
   * * This method will get the retry event from the database and will unmarshall the object and
   * will republish into queue
   *
   * @param jmsQueueName is the name of topic / queue
   * @param receivingJMSEvent
   * @param receivingJMSEvent
   */
  public void publishRetries(
      String jmsQueueName, RetryEntity jmsEventRetryEntity, ReceivingJMSEvent receivingJMSEvent) {

    try {
      // Publish message
      jmsSyncPublisher.publishInternal(jmsQueueName, receivingJMSEvent);
    } catch (Exception exception) {
      log.error(
          "(job)publishRetries Error body: {}, error: {}",
          receivingJMSEvent.getMessageBody(),
          ExceptionUtils.getStackTrace(exception));
      // Mark the record as pending and return
      jmsEventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
      jmsRecoveryService.save(jmsEventRetryEntity);
      return;
    }
    // Delete
    log.info("(job) will mark as delete entity: {}", jmsEventRetryEntity.getId());
    jmsEventRetryEntity.setEventTargetStatus(EventTargetStatus.DELETE);
    jmsRecoveryService.save(jmsEventRetryEntity);
  }
}
