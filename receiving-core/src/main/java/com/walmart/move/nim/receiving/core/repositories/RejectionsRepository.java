/** */
package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.Rejections;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** @author a0b02ft */
@Repository
public interface RejectionsRepository extends JpaRepository<Rejections, Long> {

  Rejections findTopByDeliveryNumberAndFacilityNumAndFacilityCountryCodeOrderByLastChangedTsDesc(
      Long deliveryNumber, Integer facilityNum, String countryCode);
}
