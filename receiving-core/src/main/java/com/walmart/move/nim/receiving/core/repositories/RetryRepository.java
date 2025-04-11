/** */
package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.RetryEntity;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.RetryTargetType;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for the event retry table
 *
 * @author sitakant
 */
public interface RetryRepository extends JpaRepository<RetryEntity, Long> {

  List<RetryEntity> findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThan(
      RetryTargetType applicationtype,
      EventTargetStatus runtimeStatus,
      Date curentTime,
      Pageable pageable);

  List<RetryEntity>
      findByRetryTargetTypeAndEventTargetStatusAndFuturePickupTimeLessThanAndRetriesCountLessThan(
          RetryTargetType applicationtype,
          EventTargetStatus runtimeStatus,
          Date curentTime,
          Long retriesCount,
          Pageable pageable);

  List<RetryEntity> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  List<RetryEntity> findByEventTargetStatusAndLastUpdatedDateLessThan(
      EventTargetStatus eventTargetStatus, Date currentTime, Pageable pageable);

  @Modifying
  @Query(
      value =
          "UPDATE top(:batchSize) JMS_EVENT_RETRY SET RETRIES_COUNT = 0 "
              + "WHERE RETRIES_COUNT = :maxRetryCount AND APPLICATION_TYPE = :applicationType "
              + "AND FUTURE_PICKUP_TIME BETWEEN :startDate AND :endDate AND RUNTIME_STATUS = :runtimeStatus",
      nativeQuery = true)
  void resetRetryCountByDateRange(
      @Param("batchSize") int batchSize,
      @Param("maxRetryCount") long maxRetryCount,
      @Param("applicationType") int applicationType,
      @Param("startDate") Date startDate,
      @Param("endDate") Date endDate,
      @Param("runtimeStatus") int runtimeStatus);

  @Modifying
  @Query(
      value =
          "UPDATE top(:batchSize) JMS_EVENT_RETRY SET RETRIES_COUNT = 0 "
              + "WHERE RETRIES_COUNT = :maxRetryCount AND APPLICATION_TYPE = :applicationType "
              + "AND id IN ( :ids )",
      nativeQuery = true)
  void resetRetryCountById(
      @Param("batchSize") int batchSize,
      @Param("maxRetryCount") long maxRetryCount,
      @Param("applicationType") int applicationType,
      @Param("ids") List<Long> ids);
}
