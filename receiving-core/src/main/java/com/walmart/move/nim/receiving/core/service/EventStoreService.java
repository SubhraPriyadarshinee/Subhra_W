package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.repositories.EventStoreRepository;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventStoreService {

  @Autowired private EventStoreRepository eventStoreRepository;
  private static final Logger LOGGER = LoggerFactory.getLogger(EventStoreService.class);

  public Optional<EventStore> getEventStoreByKeyStatusAndEventType(
      String key, EventTargetStatus eventTargetStatus, EventStoreType eventStoreType) {
    LOGGER.info(
        "Get event store by key = {}, type = {} and status = {}",
        key,
        eventTargetStatus,
        eventStoreType);
    return eventStoreRepository.findByFacilityNumAndEventStoreKeyAndStatusAndEventStoreType(
        getFacilityNum(), key, eventTargetStatus, eventStoreType);
  }

  @Transactional
  @InjectTenantFilter
  public void saveEventStoreEntity(EventStore eventStore) {
    eventStoreRepository.save(eventStore);
  }

  @Transactional
  @InjectTenantFilter
  public int updateEventStoreEntityStatusAndLastUpdatedDateByKeyOrDeliveryNumber(
      String key,
      Long deliveryNumber,
      EventTargetStatus eventTargetStatus,
      EventStoreType eventStoreType,
      Date lastUpdatedDate) {
    LOGGER.info(
        "Update event store for key = {} or delivery {} with status = {}",
        key,
        deliveryNumber,
        eventTargetStatus);
    return eventStoreRepository.updateDeliveryStatusAndLastUpdatedDateByFacilityNumberAndKey(
        eventTargetStatus, lastUpdatedDate, getFacilityNum(), key, deliveryNumber, eventStoreType);
  }

  @Transactional
  @InjectTenantFilter
  public int updateEventStoreEntityStatusAndLastUpdatedDateByCriteria(
      String key,
      Long deliveryNumber,
      EventTargetStatus eventTargetStatus,
      EventStoreType eventStoreType,
      Date lastUpdatedDate) {
    LOGGER.info(
        "Update event store for delivery = {} with status = {}", deliveryNumber, eventTargetStatus);
    return eventStoreRepository.updateDeliveryStatusAndLastUpdatedDateByFacilityNumber(
        eventTargetStatus, lastUpdatedDate, getFacilityNum(), key, deliveryNumber, eventStoreType);
  }
}
