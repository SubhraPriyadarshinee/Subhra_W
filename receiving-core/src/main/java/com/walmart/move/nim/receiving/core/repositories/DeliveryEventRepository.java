package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.DeliveryEvent;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.List;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** @author g0k0072 JPA repository class for DeliveryEvent entity */
public interface DeliveryEventRepository extends JpaRepository<DeliveryEvent, Long> {
  /**
   * Will acquire a lock and return delivery events for the delivery
   *
   * @param deliveryNumber delivery number
   * @return
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<DeliveryEvent> findByDeliveryNumber(Long deliveryNumber);

  /**
   * Events to be picked up by the scheduler
   *
   * @param deliveryNumber
   * @param eventTargetStatuses
   * @param retriesCount
   * @return
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<DeliveryEvent> findByDeliveryNumberAndEventStatusInAndRetriesCountIsLessThanOrderByCreateTs(
      Long deliveryNumber, List<EventTargetStatus> eventTargetStatuses, Integer retriesCount);

  /**
   * Staleness check
   *
   * @param eventTargetStatus
   * @param cutoffTime
   * @return
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  List<DeliveryEvent> findByEventStatusAndLastChangeTsBefore(
      EventTargetStatus eventTargetStatus, Date cutoffTime);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(value = "SELECT DISTINCT D1.deliveryNumber FROM DeliveryEvent D1 WHERE D1.eventStatus = 3")
  List<Integer> findDeliveriesWithInProgressEvent(Integer retriesCount);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      value =
          "SELECT DISTINCT D1.deliveryNumber FROM DeliveryEvent D1 WHERE D1.facilityNum = :facilityNum AND D1.facilityCountryCode = :facilityCountryCode AND D1.eventStatus = 8")
  List<Long> findDeliveriesWithEventInProgress(Integer facilityNum, String facilityCountryCode);

  /**
   * Given query finds a delivery for the scheduler. Refer
   * https://collaboration.wal-mart.com/display/NGRCV/Scheduler+For+Pre-Label+Generation .
   *
   * @return
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  DeliveryEvent
      findFirstByEventStatusInAndDeliveryNumberNotInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
          List<EventTargetStatus> eventTargetStatuses,
          List<Integer> deliveryNumbers,
          Integer retriesCount);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  DeliveryEvent
      findFirstByDeliveryNumberNotInAndEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
          List<Long> deliveryNumbers,
          List<EventTargetStatus> eventTargetStatuses,
          Integer retriesCount);

  void deleteByDeliveryNumber(Long deliveryNumber);

  void deleteByDeliveryNumberAndEventType(Long deliveryNumber, String eventType);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Integer countByDeliveryNumberAndEventStatusIn(
      Long deliveryNumber, List<EventTargetStatus> eventTargetStatusList);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  DeliveryEvent
      findFirstByEventStatusInAndRetriesCountIsLessThanOrderByEventTypePriorityAscCreateTsAsc(
          List<EventTargetStatus> eventStatusesForScheduler, Integer retriesCount);

  List<DeliveryEvent> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
}
