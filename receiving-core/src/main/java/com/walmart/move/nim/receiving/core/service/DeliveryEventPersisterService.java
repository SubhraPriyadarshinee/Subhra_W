package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.core.entity.PurgeData;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.repositories.DeliveryEventRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** @author g0k0072 Class to handle deliver event relayed operations */
@Service(ReceivingConstants.DELIVERY_EVENT_PERSISTER_SERVICE)
public class DeliveryEventPersisterService implements Purge {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryEventPersisterService.class);

  @Autowired private DeliveryEventRepository deliveryEventRepository;

  /**
   * Marks all events passed with the status given and saves them in the database.
   *
   * @param deliveryEvents Events to be marked.
   * @param eventTargetStatus Event status to be appied to the delivery events
   */
  public void markAndSaveDeliveryEvents(
      List<DeliveryEvent> deliveryEvents, EventTargetStatus eventTargetStatus) {
    for (DeliveryEvent deliveryEvent : deliveryEvents) {
      deliveryEvent.setEventStatus(eventTargetStatus);
    }
    deliveryEventRepository.saveAll(deliveryEvents);
  }

  /**
   * Marks all events passed with the retry status and increments count
   *
   * @param deliveryEvents Events to be marked.
   */
  public void markAndSaveDeliveryEventsForScheduler(List<DeliveryEvent> deliveryEvents) {
    for (DeliveryEvent deliveryEvent : deliveryEvents) {
      deliveryEvent.setEventStatus(EventTargetStatus.IN_RETRY);
      deliveryEvent.setRetriesCount(deliveryEvent.getRetriesCount() + 1);
    }
    deliveryEventRepository.saveAll(deliveryEvents);
  }

  /**
   * Is the current event a DOOR_ASSIGNED event
   *
   * @param deliveryUpdateMessage delivery update message
   * @return true if current event is DOOR_ASSIGNED event else false
   */
  private boolean isCurrentMessageDoorAssignedEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    return deliveryUpdateMessage.getEventType().equals(ReceivingConstants.EVENT_DOOR_ASSIGNED);
  }

  /**
   * Check if any event is IN_PROGRESS or PENDING state
   *
   * @param deliveryEvents list of delivery events
   * @return true if any event is IN_PROGRESS or PENDING otherwise false
   */
  private boolean isAnyEventInProgressOrPendingOrRetry(List<DeliveryEvent> deliveryEvents) {
    boolean isAnyEventInProgressOrPending = false;
    for (DeliveryEvent deliveryEvent : deliveryEvents) {
      if (deliveryEvent.getEventStatus().equals(EventTargetStatus.IN_PROGRESS)
          || deliveryEvent.getEventStatus().equals(EventTargetStatus.PENDING)
          || deliveryEvent.getEventStatus().equals(EventTargetStatus.IN_RETRY)) {
        isAnyEventInProgressOrPending = true;
        break;
      }
    }
    return isAnyEventInProgressOrPending;
  }

  /**
   * Check if any event is EVENT_IN_PROGRESS or EVENT_PENDING state
   *
   * @param deliveryEvents list of delivery events
   * @return true if any event is EVENT_IN_PROGRESS or EVENT_PENDING otherwise false
   */
  private boolean isAnyRdcDeliveryEventInProgressOrPendingOrRetry(
      List<DeliveryEvent> deliveryEvents) {
    boolean isAnyRdcDeliveryEventInProgressOrPendingOrRetry = false;
    for (DeliveryEvent deliveryEvent : deliveryEvents) {
      if (deliveryEvent.getEventStatus().equals(EventTargetStatus.EVENT_IN_PROGRESS)
          || deliveryEvent.getEventStatus().equals(EventTargetStatus.EVENT_PENDING)
          || deliveryEvent.getEventStatus().equals(EventTargetStatus.IN_RETRY)) {
        isAnyRdcDeliveryEventInProgressOrPendingOrRetry = true;
        break;
      }
    }
    return isAnyRdcDeliveryEventInProgressOrPendingOrRetry;
  }

  /**
   * Build delivery event based on delivery update message and assign required status
   *
   * @param deliveryUpdateMessage delivery update message
   * @param eventTargetStatus events target status like IN_PROGRESS, PENDING etc
   * @return delivery event object
   */
  private DeliveryEvent buildDeliveryEvent(
      DeliveryUpdateMessage deliveryUpdateMessage, EventTargetStatus eventTargetStatus) {
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    return DeliveryEvent.builder()
        .deliveryNumber(deliveryNumber)
        .eventType(deliveryUpdateMessage.getEventType())
        .url(deliveryUpdateMessage.getUrl())
        .eventStatus(eventTargetStatus)
        .build();
  }

  /**
   * Save delivery event
   *
   * @param deliveryEvent
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public DeliveryEvent save(DeliveryEvent deliveryEvent) {
    return deliveryEventRepository.save(deliveryEvent);
  }

  @Transactional
  @InjectTenantFilter
  public void saveEventForScheduler(DeliveryEvent deliveryEvent) {
    deliveryEvent.setEventStatus(EventTargetStatus.IN_PROGRESS);
    save(deliveryEvent);
  }

  /**
   * Check and return if the delivery event is processable now
   *
   * @param deliveryUpdateMessage
   * @return delivery event to be processed. null if nothing to process
   */
  @Transactional
  @InjectTenantFilter
  public DeliveryEvent getDeliveryEventToProcess(DeliveryUpdateMessage deliveryUpdateMessage) {
    DeliveryEvent deliveryEvent = null;
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    String deliveryEventType = deliveryUpdateMessage.getEventType();
    List<DeliveryEvent> deliveryEvents =
        deliveryEventRepository.findByDeliveryNumber(deliveryNumber);
    if (ReceivingConstants.PRE_LABEL_GEN_FALLBACK.equals(deliveryEventType)) {
      // If fallback, will mark all existing events as completed and insert new event of fallback as
      // in progress
      LOGGER.info("FALLBACK DELIVERY: {} - Marking remaining events as DELETE", deliveryNumber);
      markAndSaveDeliveryEvents(deliveryEvents, EventTargetStatus.DELETE);
      LOGGER.info("FALLBACK DELIVERY: {} - Putting fallback event IN_PROGRESS", deliveryNumber);
      deliveryEvent = buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.IN_PROGRESS);
    } else if (ReceivingUtils.isDeliveryExistsOfType(
            deliveryEvents, ReceivingConstants.EVENT_DOOR_ASSIGNED)
        || ReceivingUtils.isDeliveryExistsOfType(
            deliveryEvents, ReceivingConstants.PRE_LABEL_GEN_FALLBACK)) {
      // DOOR_ASSIGNED or PLG event exists
      if (!isCurrentMessageDoorAssignedEvent(deliveryUpdateMessage)) {
        // current event is not a DOOR_ASSIGNED event
        if (isAnyEventInProgressOrPendingOrRetry(deliveryEvents)) {
          LOGGER.info(
              "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK exists and a delivery event for this delivery is already IN_PROGRESS/PENDING/IN_RETRY. So, putting current {} event to PENDING state",
              deliveryNumber,
              deliveryEventType);
          deliveryEventRepository.save(
              buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.PENDING));
        } else {
          LOGGER.info(
              "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK exists and no delivery event for the delivery is IN_PROGRESS/PENDING/IN_RETRY. So, putting current {} event to IN_PROGRESS state",
              deliveryNumber,
              deliveryEventType);
          deliveryEvent = buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.IN_PROGRESS);
        }
      } else {
        // current event is DOOR_ASSIGNED event
        LOGGER.info(
            "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK already exists for the delivery and current event has event {}. Hence ignoring for pre-label generation",
            deliveryNumber,
            deliveryEventType);
      }
    } else {
      // DOOR_ASSIGNED or PLG event doesn't exists
      if (isCurrentMessageDoorAssignedEvent(deliveryUpdateMessage)) {
        LOGGER.info(
            "PLG DELIVERY: {} - This is the first {} event for the delivery. So, putting the event to IN_PROGRESS state",
            deliveryNumber,
            deliveryEventType);
        deliveryEvent = buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.IN_PROGRESS);
      } else {
        LOGGER.info(
            "PLG DELIVERY: {} - DOOR_ASSIGNED has not come yet for the delivery and current event is {}. Hence ignoring for pre-label generation",
            deliveryNumber,
            deliveryEventType);
      }
    }
    if (deliveryEvent != null) {
      // Calling repository save method to propagate transaction
      deliveryEvent = deliveryEventRepository.save(deliveryEvent);
    }
    return deliveryEvent;
  }

  /**
   * Check and return if the delivery event is processable now
   *
   * @param deliveryUpdateMessage
   * @return delivery event to be processed. null if nothing to process
   */
  @Transactional
  @InjectTenantFilter
  public DeliveryEvent getRdcDeliveryEventToProcess(DeliveryUpdateMessage deliveryUpdateMessage) {
    DeliveryEvent deliveryEvent = null;
    Long deliveryNumber = Long.parseLong(deliveryUpdateMessage.getDeliveryNumber());
    String deliveryEventType = deliveryUpdateMessage.getEventType();
    List<DeliveryEvent> deliveryEvents =
        deliveryEventRepository.findByDeliveryNumber(deliveryNumber);
    if (ReceivingConstants.PRE_LABEL_GEN_FALLBACK.equals(deliveryEventType)) {
      // If fallback, will mark all existing events as completed and insert new event of fallback as
      // in progress
      LOGGER.info("FALLBACK DELIVERY: {} - Marking remaining events as DELETE", deliveryNumber);
      markAndSaveDeliveryEvents(deliveryEvents, EventTargetStatus.DELETE);
      LOGGER.info("FALLBACK DELIVERY: {} - Putting fallback event IN_PROGRESS", deliveryNumber);
      deliveryEvent =
          buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.EVENT_IN_PROGRESS);
    } else if (ReceivingUtils.isDeliveryExistsOfType(
            deliveryEvents, ReceivingConstants.EVENT_DOOR_ASSIGNED)
        || ReceivingUtils.isDeliveryExistsOfType(
            deliveryEvents, ReceivingConstants.PRE_LABEL_GEN_FALLBACK)) {
      // DOOR_ASSIGNED or PLG event exists
      if (!isCurrentMessageDoorAssignedEvent(deliveryUpdateMessage)) {
        // current event is not a DOOR_ASSIGNED event
        if (isAnyRdcDeliveryEventInProgressOrPendingOrRetry(deliveryEvents)) {
          LOGGER.info(
              "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK exists and a delivery event for this delivery is already EVENT_IN_PROGRESS/EVENT_PENDING/IN_RETRY. So, putting current {} event to EVENT_PENDING state",
              deliveryNumber,
              deliveryEventType);
          deliveryEventRepository.save(
              buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.EVENT_PENDING));
        } else {
          LOGGER.info(
              "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK exists and no delivery event for the delivery is EVENT_IN_PROGRESS/EVENT_PENDING/IN_RETRY. So, putting current {} event to EVENT_IN_PROGRESS state",
              deliveryNumber,
              deliveryEventType);
          deliveryEvent =
              buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.EVENT_IN_PROGRESS);
        }
      } else {
        // current event is DOOR_ASSIGNED event
        LOGGER.info(
            "PLG DELIVERY: {} - DOOR_ASSIGNED or FALLBACK already exists for the delivery and current event has event {}. Hence ignoring for pre-label generation",
            deliveryNumber,
            deliveryEventType);
      }
    } else {
      // DOOR_ASSIGNED or PLG event doesn't exists
      if (isCurrentMessageDoorAssignedEvent(deliveryUpdateMessage)) {
        LOGGER.info(
            "PLG DELIVERY: {} - This is the first {} event for the delivery. So, putting the event to EVENT_IN_PROGRESS state",
            deliveryNumber,
            deliveryEventType);
        deliveryEvent =
            buildDeliveryEvent(deliveryUpdateMessage, EventTargetStatus.EVENT_IN_PROGRESS);
      } else {
        LOGGER.info(
            "PLG DELIVERY: {} - DOOR_ASSIGNED has not come yet for the delivery and current event is {}. Hence ignoring for pre-label generation",
            deliveryNumber,
            deliveryEventType);
      }
    }
    if (deliveryEvent != null) {
      // Calling repository save method to propagate transaction
      deliveryEvent = deliveryEventRepository.save(deliveryEvent);
    }
    return deliveryEvent;
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(Long deliveryNumber) {
    deliveryEventRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumberAndEventType(Long deliveryNumber, String eventType) {
    deliveryEventRepository.deleteByDeliveryNumberAndEventType(deliveryNumber, eventType);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<DeliveryEvent> getDeliveryEventsByDeliveryNumber(Long deliveryNumber) {
    return deliveryEventRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public DeliveryEvent getDeliveryEventById(Long id) {
    return deliveryEventRepository.findById(id).get();
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Integer getCountOfInProgressAndPendingEvents(Long deliveryNumber) {
    return deliveryEventRepository.countByDeliveryNumberAndEventStatusIn(
        deliveryNumber, ReceivingUtils.getPendingAndInProgressEventStatuses());
  }

  @Transactional
  @InjectTenantFilter
  public List<DeliveryEvent> getDeliveryEventsForScheduler(
      Long deliveryNumber, List<EventTargetStatus> eventTargetStatuses, Integer retriesCount) {
    List<DeliveryEvent> deliveryEventsForScheduler =
        deliveryEventRepository
            .findByDeliveryNumberAndEventStatusInAndRetriesCountIsLessThanOrderByCreateTs(
                deliveryNumber, eventTargetStatuses, retriesCount);
    markAndSaveDeliveryEventsForScheduler(deliveryEventsForScheduler);
    return deliveryEventsForScheduler;
  }

  @Transactional(readOnly = true)
  public List<DeliveryEvent> getStaleDeliveryEvents(Date cutoffTime) {
    return deliveryEventRepository.findByEventStatusAndLastChangeTsBefore(
        EventTargetStatus.IN_PROGRESS, cutoffTime);
  }

  @Transactional(readOnly = true)
  public DeliveryEvent getDeliveryForScheduler(Integer retriesCount) {
    List<Integer> listOfDeliveriesWithInProgressEvent =
        deliveryEventRepository.findDeliveriesWithInProgressEvent(retriesCount);

    if (CollectionUtils.isEmpty(listOfDeliveriesWithInProgressEvent)) {
      return deliveryEventRepository
          .findFirstByEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
              ReceivingUtils.getEventStatusesForScheduler(), retriesCount);
    }
    return deliveryEventRepository
        .findFirstByEventStatusInAndDeliveryNumberNotInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
            ReceivingUtils.getEventStatusesForScheduler(),
            listOfDeliveriesWithInProgressEvent,
            retriesCount);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public DeliveryEvent getDeliveryForRdcScheduler(Integer retriesCount) {
    List<Long> listOfDeliveriesWithInProgressEvent =
        deliveryEventRepository.findDeliveriesWithEventInProgress(
            TenantContext.getFacilityNum(), TenantContext.getFacilityCountryCode());

    if (CollectionUtils.isEmpty(listOfDeliveriesWithInProgressEvent)) {
      return deliveryEventRepository
          .findFirstByEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
              Collections.singletonList(EventTargetStatus.EVENT_PENDING), retriesCount);
    }
    return deliveryEventRepository
        .findFirstByDeliveryNumberNotInAndEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
            listOfDeliveriesWithInProgressEvent,
            Collections.singletonList(EventTargetStatus.EVENT_PENDING),
            retriesCount);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<DeliveryEvent> deliveryEventList =
        deliveryEventRepository.findByIdGreaterThanEqual(
            purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    deliveryEventList =
        deliveryEventList
            .stream()
            .filter(item -> item.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(DeliveryEvent::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(deliveryEventList)) {
      LOGGER.info("Purge DELIVERY_EVENT: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = deliveryEventList.get(deliveryEventList.size() - 1).getId();

    LOGGER.info(
        "Purge DELIVERY_EVENT: {} records : ID {} to {} : START",
        deliveryEventList.size(),
        deliveryEventList.get(0).getId(),
        lastDeletedId);
    deliveryEventRepository.deleteAll(deliveryEventList);
    LOGGER.info("Purge DELIVERY_EVENT: END");
    return lastDeletedId;
  }
}
