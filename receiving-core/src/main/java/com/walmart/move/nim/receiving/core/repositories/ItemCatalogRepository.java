package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.ItemCatalogUpdateLog;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemCatalogRepository extends JpaRepository<ItemCatalogUpdateLog, Long> {

  List<ItemCatalogUpdateLog> deleteAllByDeliveryNumber(Long deliveryNumber);

  List<ItemCatalogUpdateLog> findByDeliveryNumber(Long deliveryNumber);

  List<ItemCatalogUpdateLog> findByIdGreaterThanEqual(Long lastDeleteId, Pageable pageable);

  List<ItemCatalogUpdateLog> findByDeliveryNumberAndNewItemUPC(Long deliveryNumber, String itemUpc);

  void deleteByDeliveryNumberAndNewItemUPC(
      @NotNull Long deliveryNumber, @NotBlank String newItemUPC);
}
