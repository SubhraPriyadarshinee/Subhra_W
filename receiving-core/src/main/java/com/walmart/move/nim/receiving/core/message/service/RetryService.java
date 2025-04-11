/** */
package com.walmart.move.nim.receiving.core.message.service;

import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** @author sitakant */
public interface RetryService {
  /**
   * Save and JmsEventRetryEntity.
   *
   * @param entity
   * @return
   */
  RetryEntity save(RetryEntity entity);

  /**
   * Delete an JmsEventRetryEntity.
   *
   * @param entity
   */
  void delete(RetryEntity entity);

  /**
   * Find and update by RetryTargetType And EventTargetStatus.
   *
   * @param applicationtype
   * @param runtimeStatus
   * @return List<JmsEventRetryEntity>
   */
  List<RetryEntity> findAndUpdateByRetryTargetTypeAndEventTargetStatus(
      RetryTargetType applicationtype, EventTargetStatus runtimeStatus);

  /**
   * Find and update by RetryTargetType And EventTargetStatus with considering the number of
   * retriesCount.
   *
   * @param applicationtype
   * @param runtimeStatus
   * @param retriesCount
   * @return List<JmsEventRetryEntity>
   */
  List<RetryEntity> findAndUpdateByRetryTargetTypeAndEventTargetStatus(
      RetryTargetType applicationtype, EventTargetStatus runtimeStatus, Long retriesCount);

  /**
   * Find by id.
   *
   * @param id
   * @return
   */
  Optional<RetryEntity> findById(Long id);

  /**
   * Put for retries for JMS.
   *
   * @param queueName
   * @param messageTemplate
   * @return JmsEventRetryEntity
   */
  RetryEntity putForRetries(String queueName, ReceivingJMSEvent messageTemplate);

  /**
   * Put for retries for Rest.
   *
   * @param url
   * @param httpMethodType
   * @param httpHeaders
   * @param body
   * @param retryTargetFlow
   * @return JmsEventRetryEntity
   */
  RetryEntity putForRetries(
      String url,
      HttpMethod httpMethodType,
      HttpHeaders httpHeaders,
      String body,
      RetryTargetFlow retryTargetFlow);

  RetryEntity putForRetries(
      String url,
      HttpMethod httpMethodType,
      HttpHeaders httpHeaders,
      String body,
      RetryTargetFlow retryTargetFlow,
      EventTargetStatus eventTargetStatus);

  void findAndUpdateStaleEntries();

  void resetJmsRetryCount(
      int batchSize,
      long maxRetryCount,
      int applicationType,
      Date startDate,
      Date endDate,
      int runtimeStatus);

  void resetJmsRetryCount(int batchSize, Long maxRetryCount, int applicationType, List<Long> ids);
}
