/** */
package com.walmart.move.nim.receiving.core.message.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.SecurityUtil;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.core.message.common.ReceivingJMSEvent;
import com.walmart.move.nim.receiving.core.model.RestRetryRequest;
import com.walmart.move.nim.receiving.core.repositories.RetryRepository;
import com.walmart.move.nim.receiving.core.service.Purge;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetFlow;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/**
 * Application Retry service class
 *
 * @author sitakant
 */
@Service(ReceivingConstants.RETRY_SERVICE)
public class RetryServiceImpl implements RetryService, Purge {
  private static final Logger LOG = LoggerFactory.getLogger(RetryServiceImpl.class);

  @Autowired private RetryRepository applicationRetriesRepository;

  @ManagedConfiguration private AppConfig appConfig;

  private Gson gson;

  @Value("${secrets.key}")
  private String secretKey;

  public RetryServiceImpl() {
    gson = new Gson();
  }

  @Override
  @Transactional
  public RetryEntity save(RetryEntity entity) {
    return applicationRetriesRepository.save(entity);
  }

  @Override
  @Transactional
  public void delete(RetryEntity entity) {
    applicationRetriesRepository.deleteById(entity.getId());
  }

  @Override
  @Transactional
  public List<RetryEntity> findAndUpdateByRetryTargetTypeAndEventTargetStatus(
      RetryTargetType retryTargetType, EventTargetStatus eventTargetStatus) {
    List<RetryEntity> applicationRetriesEntities =
        applicationRetriesRepository
            .findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThan(
                retryTargetType,
                eventTargetStatus,
                new Date(),
                PageRequest.of(0, appConfig.getJmsRetryPublishPageSize()));

    // Increase the retry count
    applicationRetriesEntities.forEach(
        applicationRetriesEntity -> {
          applicationRetriesEntity.setRetriesCount(applicationRetriesEntity.getRetriesCount() + 1);
          applicationRetriesEntity.setEventTargetStatus(EventTargetStatus.IN_RETRY);
        });
    applicationRetriesRepository.saveAll(applicationRetriesEntities);
    return applicationRetriesEntities;
  }

  @Override
  @Transactional
  public List<RetryEntity> findAndUpdateByRetryTargetTypeAndEventTargetStatus(
      RetryTargetType retryTargetType, EventTargetStatus eventTargetStatus, Long retriesCount) {
    List<RetryEntity> applicationRetriesEntities =
        applicationRetriesRepository
            .findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThanAndRetriesCountLessThan(
                retryTargetType,
                eventTargetStatus,
                new Date(),
                retriesCount,
                PageRequest.of(0, appConfig.getJmsRetryPublishPageSize()));

    // Increase the retry count
    applicationRetriesEntities.forEach(
        applicationRetriesEntity -> {
          applicationRetriesEntity.setRetriesCount(applicationRetriesEntity.getRetriesCount() + 1);
          applicationRetriesEntity.setEventTargetStatus(EventTargetStatus.IN_RETRY);
        });
    return applicationRetriesRepository.saveAll(applicationRetriesEntities);
  }

  @Override
  @Transactional
  public Optional<RetryEntity> findById(Long id) {
    return applicationRetriesRepository.findById(id);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public RetryEntity putForRetries(String queueName, ReceivingJMSEvent messageTemplate) {
    RetryEntity eventRetryEntity = new RetryEntity();
    eventRetryEntity.setRetriesCount(0L);
    eventRetryEntity.setJmsQueueName(queueName);
    eventRetryEntity.setRetryTargetType(RetryTargetType.JMS);
    eventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
    eventRetryEntity.setRetryTargetFlow(RetryTargetFlow.JMS_MESSAGE_PUBLISHING);
    eventRetryEntity.setPayload(gson.toJson(messageTemplate));
    return save(eventRetryEntity);
  }

  @Override
  @Transactional
  public RetryEntity putForRetries(
      String url,
      HttpMethod httpMethodType,
      HttpHeaders httpHeaders,
      String body,
      RetryTargetFlow retryTargetFlow) {
    String strHttpHeaders = null;

    try {
      strHttpHeaders = SecurityUtil.encryptValue(secretKey, gson.toJson(httpHeaders));
    } catch (Exception e) {
      strHttpHeaders = gson.toJson(httpHeaders);
      LOG.error(
          ReceivingConstants.ENCRYPTION_ERROR_MESSAGE,
          "http headers",
          httpHeaders,
          ExceptionUtils.getStackTrace(e));
    }

    RestRetryRequest restRetryRequest =
        new RestRetryRequest(url, httpMethodType, strHttpHeaders, body);
    RetryEntity eventRetryEntity = new RetryEntity();
    eventRetryEntity.setRetriesCount(0L);
    eventRetryEntity.setRetryTargetType(RetryTargetType.REST);
    eventRetryEntity.setEventTargetStatus(EventTargetStatus.PENDING);
    eventRetryEntity.setRetryTargetFlow(retryTargetFlow);
    eventRetryEntity.setPayload(gson.toJson(restRetryRequest));
    return save(eventRetryEntity);
  }

  @Override
  @Transactional
  public void findAndUpdateStaleEntries() {
    Calendar cal = Calendar.getInstance();
    cal.add(Calendar.MINUTE, -appConfig.getJmsRetryStaleTimeOut());
    Date cutoffTime = cal.getTime();
    List<RetryEntity> applicationRetriesEntities =
        applicationRetriesRepository.findByEventTargetStatusAndLastUpdatedDateLessThan(
            EventTargetStatus.IN_RETRY,
            cutoffTime,
            PageRequest.of(0, appConfig.getJmsRetryPublishPageSize()));

    if (CollectionUtils.isEmpty(applicationRetriesEntities)) {
      LOG.info("(JOB) No stale entries found.");
      return;
    }
    // Set the status to pending
    LOG.info("(JOB) marking {} stale entries as pending.", applicationRetriesEntities.size());
    applicationRetriesEntities.forEach(
        applicationRetriesEntity -> {
          applicationRetriesEntity.setRetriesCount(
              applicationRetriesEntity.getRetriesCount() > 0
                  ? applicationRetriesEntity.getRetriesCount() - 1
                  : 0);
          applicationRetriesEntity.setEventTargetStatus(EventTargetStatus.PENDING);
        });
    applicationRetriesRepository.saveAll(applicationRetriesEntities);
  }

  @Transactional
  public void resetJmsRetryCount(
      int batchSize,
      long maxRetryCount,
      int applicationType,
      Date startDate,
      Date endDate,
      int runtimeStatus) {
    applicationRetriesRepository.resetRetryCountByDateRange(
        batchSize, maxRetryCount, applicationType, startDate, endDate, runtimeStatus);
  }

  @Transactional
  public void resetJmsRetryCount(
      int batchSize, Long maxRetryCount, int applicationType, List<Long> ids) {
    applicationRetriesRepository.resetRetryCountById(
        batchSize, maxRetryCount, applicationType, ids);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<RetryEntity> retryEntityList =
        applicationRetriesRepository.findByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    retryEntityList =
        retryEntityList
            .stream()
            .filter(item -> item.getFuturePickupTime().before(deleteDate))
            .sorted(Comparator.comparing(RetryEntity::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(retryEntityList)) {
      LOG.info("Purge JMS_EVENT_RETRY: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = retryEntityList.get(retryEntityList.size() - 1).getId();

    LOG.info(
        "Purge JMS_EVENT_RETRY: {} records : ID {} to {} : START",
        retryEntityList.size(),
        retryEntityList.get(0).getId(),
        lastDeletedId);
    applicationRetriesRepository.deleteAll(retryEntityList);
    LOG.info("Purge JMS_EVENT_RETRY: END");
    return lastDeletedId;
  }

  @Override
  public RetryEntity putForRetries(
      String url,
      HttpMethod httpMethodType,
      HttpHeaders httpHeaders,
      String body,
      RetryTargetFlow retryTargetFlow,
      EventTargetStatus eventTargetStatus) {
    String strHttpHeaders;
    try {
      strHttpHeaders = SecurityUtil.encryptValue(secretKey, gson.toJson(httpHeaders));
    } catch (Exception e) {
      strHttpHeaders = gson.toJson(httpHeaders);
      LOG.error(
          ReceivingConstants.ENCRYPTION_ERROR_MESSAGE,
          "http headers",
          httpHeaders,
          ExceptionUtils.getStackTrace(e));
    }

    RestRetryRequest restRetryRequest =
        new RestRetryRequest(url, httpMethodType, strHttpHeaders, body);
    RetryEntity eventRetryEntity = new RetryEntity();
    eventRetryEntity.setRetriesCount(0L);
    eventRetryEntity.setRetryTargetType(RetryTargetType.REST);
    eventRetryEntity.setEventTargetStatus(eventTargetStatus);
    eventRetryEntity.setRetryTargetFlow(retryTargetFlow);
    eventRetryEntity.setPayload(gson.toJson(restRetryRequest));
    return save(eventRetryEntity);
  }
}
