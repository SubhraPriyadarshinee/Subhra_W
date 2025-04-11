package com.walmart.move.nim.receiving.reporting.repositories;

import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import java.util.Date;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportingItemCatalogRepository extends JpaRepository<ItemCatalogUpdateLog, Long> {

  List<ItemCatalogUpdateLog> findAllByCreateTsBetween(Date fromDate, Date toDate);

  List<ItemCatalogUpdateLog> findAllByCreateTsBetweenAndFacilityNumNotIn(
      Date fromDate, Date toDate, List<Integer> facilityNum);

  List<ItemCatalogUpdateLog> findAllByCreateTsBetweenAndFacilityNumIn(
      Date fromDate, Date toDate, List<Integer> facilityNum);

  List<ItemCatalogUpdateLog> deleteAllByDeliveryNumber(Long deliveryNumber);

  List<ItemCatalogUpdateLog> findByDeliveryNumber(Long deliveryNumber);

  List<ItemCatalogUpdateLog> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);
}
