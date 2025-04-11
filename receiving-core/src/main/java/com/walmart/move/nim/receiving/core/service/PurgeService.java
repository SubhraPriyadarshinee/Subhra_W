package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.config.PurgeConfig;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.repositories.PurgeDataRepository;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.PurgeEntityType;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/** @author r0s01us */
@Service
public class PurgeService {
  private static final Logger LOGGER = LoggerFactory.getLogger(PurgeService.class);
  @Autowired private PurgeDataRepository purgeDataRepository;
  @ManagedConfiguration private PurgeConfig purgeConfig;
  @Autowired private ApplicationContext applicationContext;

  public List<PurgeData> createEntities(List<PurgeEntityType> entities) {
    List<PurgeData> purgeDataList = new ArrayList<>();
    for (PurgeEntityType entity : entities) {
      PurgeData purgeData =
          PurgeData.builder()
              .purgeEntityType(entity)
              .lastDeleteId(0L)
              .eventTargetStatus(EventTargetStatus.PENDING)
              .build();
      purgeDataList.add(purgeData);
    }
    return purgeDataRepository.saveAll(purgeDataList);
  }

  public void purge() {
    List<PurgeData> purgeDataList = purgeDataRepository.findAll();

    if (CollectionUtils.isEmpty(purgeDataList)) {
      LOGGER.info("PurgeService::purge::Nothing to purge.");
      return;
    }

    // filter out disabled entities
    if (!CollectionUtils.isEmpty(purgeConfig.getDisabledEntities())) {
      purgeDataList =
          purgeDataList
              .stream()
              .filter(
                  purgeData ->
                      !purgeConfig.getDisabledEntities().contains(purgeData.getPurgeEntityType()))
              .collect(Collectors.toList());
    }

    checkAndMarkAllPending(purgeDataList);

    // find first PENDING entity order by ID
    Optional<PurgeData> optionalPurgeData =
        purgeDataList
            .stream()
            .filter(purgeData -> purgeData.getEventTargetStatus().equals(EventTargetStatus.PENDING))
            .min(Comparator.comparing(PurgeData::getId));

    if (!optionalPurgeData.isPresent()) {
      LOGGER.info("PurgeService::purge::Nothing to purge.");
      return;
    }
    purgeEntity(optionalPurgeData.get());
  }

  private void purgeEntity(PurgeData purgeEntity) {
    if (Objects.isNull(purgeEntity)) {
      LOGGER.info("PurgeService::purgeEntity::Null: Nothing to purge.");
      return;
    }
    PageRequest pageReq = PageRequest.of(0, getBatchSize(purgeEntity.getPurgeEntityType()));
    // get bean for entity and call delete
    Purge instance = getBean(purgeEntity.getPurgeEntityType());
    if (Objects.nonNull(instance)) {
      long lastDeletedId =
          instance.purge(
              purgeEntity, pageReq, getRetentionPolicy(purgeEntity.getPurgeEntityType()));
      if (purgeEntity.getLastDeleteId() < lastDeletedId) {
        purgeEntity.setLastDeleteId(lastDeletedId);
      }
      purgeEntity.setEventTargetStatus(EventTargetStatus.DELETE);
      purgeDataRepository.save(purgeEntity);
    }
  }

  private void checkAndMarkAllPending(List<PurgeData> purgeDataList) {
    if (purgeDataList
            .stream()
            .filter(purgeData -> purgeData.getEventTargetStatus().equals(EventTargetStatus.DELETE))
            .count()
        == purgeDataList.size()) {
      purgeDataList
          .stream()
          .forEach(purgeData -> purgeData.setEventTargetStatus(EventTargetStatus.PENDING));
      purgeDataRepository.saveAll(purgeDataList);
    }
  }

  private Purge getBean(PurgeEntityType entityType) {
    String beanName = entityType.getBeanName();
    try {
      return (Purge) this.applicationContext.getBean(beanName);
    } catch (BeansException beansException) {
      LOGGER.error(
          "PurgeService::getBean::No bean specified with beanName={} for entity={} ",
          beanName,
          entityType.name());
      return null;
    }
  }

  private int getBatchSize(PurgeEntityType purgeEntityType) {
    Map<String, Integer> batchSizeMap = purgeConfig.getBatchSizeMap();
    return batchSizeMap.get(purgeEntityType.name()) == null
        ? batchSizeMap.get("default")
        : batchSizeMap.get(purgeEntityType.name());
  }

  private int getRetentionPolicy(PurgeEntityType purgeEntityType) {
    Map<String, Integer> retentionPolicyMap = purgeConfig.getRetentionPolicyMap();
    return retentionPolicyMap.get(purgeEntityType.name()) == null
        ? retentionPolicyMap.get("default")
        : retentionPolicyMap.get(purgeEntityType.name());
  }
}
