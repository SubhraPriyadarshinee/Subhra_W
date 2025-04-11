package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.ItemTracker;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemTrackerRepository extends JpaRepository<ItemTracker, Long> {
  /**
   * This method is responsible for fetching all the {@link ItemTracker} by trackingId
   *
   * @param trackingId
   * @return
   */
  List<ItemTracker> findByTrackingId(String trackingId);

  /**
   * This method is responsible for fetching all the {@link ItemTracker} by parentTrackingId
   *
   * @param parentTrackingId
   * @return
   */
  List<ItemTracker> findByParentTrackingId(String parentTrackingId);

  /**
   * This method is responsible for fetching all the {@link ItemTracker} by gtin
   *
   * @param gtin
   * @return
   */
  List<ItemTracker> findByGtin(String gtin);

  /**
   * This method is responsible for deleting all {@link ItemTracker} by trackingId
   *
   * @param trackingId
   */
  void deleteByTrackingId(String trackingId);

  /**
   * This method is responsible for deleting all {@link ItemTracker} by parentTrackingId
   *
   * @param parentTrackingId
   */
  void deleteByParentTrackingId(String parentTrackingId);

  /**
   * This method is responsible for deleting all {@link ItemTracker} by gtin
   *
   * @param gtin
   */
  void deleteByGtin(String gtin);

  /**
   * This method is responsible for fetching all the {@link ItemTracker} those are having id greater
   * than @lastDeleteId
   *
   * @param lastDeleteId
   * @param pageable
   * @return
   */
  List<ItemTracker> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
}
