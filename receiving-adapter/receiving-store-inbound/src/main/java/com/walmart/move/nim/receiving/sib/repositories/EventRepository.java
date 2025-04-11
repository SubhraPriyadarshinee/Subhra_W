package com.walmart.move.nim.receiving.sib.repositories;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EventRepository extends JpaRepository<Event, Long> {
  @Query(
      value =
          "SELECT TOP(:fetchSize) * FROM EVENT WHERE STATUS = :status AND PICKUP_TIME <= :beforeDate ORDER BY ID",
      nativeQuery = true)
  List<Event> findAllByStatusAndPickUpTimeLessThanEqualOrderByID(
      String status, Date beforeDate, Integer fetchSize);

  List<Event> findAllByDeliveryNumberAndPickUpTimeIsNull(Long deliveryNumber);

  Event findByKeyAndStatusAndEventType(String key, EventTargetStatus status, EventType event);

  Optional<List<Event>> findAllByDeliveryNumberAndFacilityNumAndFacilityCountryCode(
      Long deliveryNumber, Integer facilityNum, String countryCode);

  Event findByDeliveryNumberAndEventType(Long deliveryNumber, EventType event);

  @Query(
      value =
          "SELECT TOP(:fetchSize) * FROM EVENT WHERE STATUS = :status AND PICKUP_TIME <= :beforeDate AND EVENT_TYPE IN (:#{#eventTypes.![name()]}) ORDER BY ID",
      nativeQuery = true)
  List<Event> findAllByStatusAndPickUpTimeLessThanEqualAndEventTypeInOrderByID(
      String status, Date beforeDate, List<EventType> eventTypes, Integer fetchSize);

  List<Event> findAllByDeliveryNumberAndStatus(Long deliveryNumber, EventTargetStatus status);

  List<Event> findAllByDeliveryNumberAndEventType(Long deliveryNumber, EventType eventType);

  List<Event> findAllByDeliveryNumber(Long deliveryNumber);

  List<Event> findAllByDeliveryNumberAndEventTypeAndStatus(
      Long deliveryNumber, EventType eventType, EventTargetStatus status);
}
