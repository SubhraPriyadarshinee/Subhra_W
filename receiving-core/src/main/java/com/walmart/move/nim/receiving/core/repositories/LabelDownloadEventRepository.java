package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelDownloadEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelDownloadEventRepository extends JpaRepository<LabelDownloadEvent, Long> {
  List<LabelDownloadEvent> findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Long itemNumber);

  List<LabelDownloadEvent> findByDeliveryNumberAndItemNumber(Long deliveryNumber, Long itemNumber);

  List<LabelDownloadEvent> findByItemNumber(Long itemNumber);

  List<LabelDownloadEvent> findByPurchaseReferenceNumberAndItemNumber(
      String purchaseReferenceNumber, Long itemNumber);

  List<LabelDownloadEvent> findByDeliveryNumberAndPurchaseReferenceNumberAndItemNumberAndStatus(
      Long deliveryNumber, String purchaseReferenceNumber, Long itemNumber, String status);

  List<LabelDownloadEvent> findByItemNumberAndDeliveryNumberIn(
      Long itemNumber, List<Long> deliveryNumbers);
}
