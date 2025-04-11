package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.DeliveryPoLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * DeliveryPOLine Repository
 *
 * @author v0k00fe
 */
@Repository
public interface DeliveryPoLineRepository extends JpaRepository<DeliveryPoLine, Long> {

  /**
   * This method is to fetch Delivery PO Line details based on deliveryNumber
   *
   * @param deliveryNumber
   * @return
   */
  List<DeliveryPoLine> findByDeliveryNumberAndFacilityCountryCodeAndFacilityNum(
      Long deliveryNumber, String facilityCountryCode, int facilityNum);
}
