package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EventStoreRepository extends JpaRepository<EventStore, Long> {

  @Modifying
  @Query(
      "UPDATE EventStore e SET e.status = ?1, e.lastUpdatedDate = ?2 WHERE e.facilityNum = ?3 AND e.eventStoreKey <> ?4 AND e.deliveryNumber = ?5 AND e.eventStoreType = ?6")
  int updateDeliveryStatusAndLastUpdatedDateByFacilityNumber(
      EventTargetStatus status,
      Date lastUpdatedDate,
      Integer facilityNum,
      String eventStoreKey,
      Long deliveryNumber,
      EventStoreType eventStoreType);

  Optional<EventStore> findByFacilityNumAndEventStoreKeyAndStatusAndEventStoreType(
      Integer facilityNum,
      String eventStoreKey,
      EventTargetStatus status,
      EventStoreType eventStoreType);

  @Modifying
  @Query(
      "UPDATE EventStore e SET e.status = ?1, e.lastUpdatedDate = ?2 WHERE e.facilityNum = ?3 AND (e.eventStoreKey = ?4 OR e.deliveryNumber = ?5) AND e.eventStoreType = ?6")
  int updateDeliveryStatusAndLastUpdatedDateByFacilityNumberAndKey(
      EventTargetStatus status,
      Date lastUpdatedDate,
      Integer facilityNum,
      String eventStoreKey,
      Long deliveryNumber,
      EventStoreType eventStoreType);
}
