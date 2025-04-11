package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverride;
import com.walmart.move.nim.receiving.core.entity.DeliveryItemOverrideId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeliveryItemOverrideRepository
    extends JpaRepository<DeliveryItemOverride, DeliveryItemOverrideId> {

  /**
   * @param deliveryNumber
   * @param itemNumber
   * @return DeliveryItemOverride
   */
  Optional<DeliveryItemOverride> findByDeliveryNumberAndItemNumber(
      Long deliveryNumber, Long itemNumber);

  /**
   * @param itemNumber
   * @return DeliveryItemOverride
   */
  Optional<DeliveryItemOverride> findByItemNumber(Long itemNumber);

  /**
   * @param itemNumber
   * @return DeliveryItemOverride
   */
  Optional<DeliveryItemOverride> findTopByItemNumberOrderByLastChangedTsDesc(Long itemNumber);

  /**
   * @param deliveryNumber
   * @param itemNumber
   * @return void
   */
  void deleteByDeliveryNumberAndItemNumber(Long deliveryNumber, Long itemNumber);
}
