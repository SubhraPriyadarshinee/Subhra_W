package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.UnloaderInfo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UnloaderInfoRepository extends JpaRepository<UnloaderInfo, Long> {

  /**
   * Method to fetch unloaderInfo based on delivery number
   *
   * @param deliveryNumber
   * @return List of unloaderInfo
   */
  List<UnloaderInfo> findByDeliveryNumberAndFacilityCountryCodeAndFacilityNum(
      Long deliveryNumber, String facilityCountryCode, int facilityNum);

  /**
   * Used for getting the unloaderInfo by po/po line, delivery
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return List of unloaderInfo by PO/PO line and delivery number
   */
  List<UnloaderInfo>
      findByDeliveryNumberAndFacilityCountryCodeAndFacilityNumAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
          Long deliveryNumber,
          String facilityCountryCode,
          int facilityNum,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber);
}
