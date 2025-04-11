package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DeliveryMetaDataRepository extends JpaRepository<DeliveryMetaData, Long> {

  Optional<DeliveryMetaData> findByDeliveryNumber(String deliveryNumber);

  List<DeliveryMetaData> findAllByDeliveryNumberInOrderByCreatedDate(List<String> deliveryNumber);

  List<DeliveryMetaData> findDeliveryMetaDataByDeliveryNumber(String deliveryNumber);

  void deleteByDeliveryNumber(String deliveryNumber);

  /**
   * This method is responsible for fetching list of {@link DeliveryMetaData} where unloading
   * complete date is greater than the unloadingCompleteDate provided as method argument and osdr
   * last processed date is less than the osdrLastProcessedDate provided as method argument or osdr
   * last processed date is null.
   *
   * @param unloadingCompleteDate
   * @param osdrLastProcessedDate
   * @return
   */
  List<DeliveryMetaData>
      findByUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
          Date unloadingCompleteDate,
          Date osdrLastProcessedDate,
          Date unloadedDate,
          Pageable pageable);

  List<DeliveryMetaData>
      findByDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateBeforeOrDeliveryStatusAndUnloadingCompleteDateAfterAndOsdrLastProcessedDateIsNull(
          DeliveryStatus deliveryStatus,
          Date unloadingCompleteDate,
          Date osdrLastProcessedDate,
          DeliveryStatus status,
          Date unloadedDate,
          Pageable pageable);

  List<DeliveryMetaData> findAllByDeliveryStatusAndCreatedDateLessThan(
      DeliveryStatus deliveryStatus, Date createTs);

  List<DeliveryMetaData> findAllByDeliveryStatus(DeliveryStatus deliveryStatus);

  List<DeliveryMetaData> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  List<DeliveryMetaData> findByDeliveryStatus(DeliveryStatus deliveryStatus, Pageable pageable);

  Page<DeliveryMetaData> findByDeliveryStatusIn(
      List<DeliveryStatus> deliveryStatuses, Pageable pageable);

  @Query(
      "select d from DeliveryMetaData d "
          + "where d.facilityCountryCode = ?1 and d.facilityNum = ?2 and d.doorNumber = ?3 "
          + "order by d.createdDate DESC")
  List<DeliveryMetaData> findDeliveryDetailsByDoorNumberOrderByCreateDate(
      String facilityCountryCode, Integer facilityNum, String doorNumber);
}
