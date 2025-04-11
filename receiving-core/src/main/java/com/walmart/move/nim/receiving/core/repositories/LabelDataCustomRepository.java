package com.walmart.move.nim.receiving.core.repositories;

import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.delivery.meta.PurchaseOrderInfo;
import com.walmart.move.nim.receiving.core.model.label.acl.LabelType;
import org.springframework.data.repository.query.Param;

public interface LabelDataCustomRepository {
  PurchaseOrderInfo findByDeliveryNumberAndContainsLPN(
      @Param("deliveryNumber") Long deliveryNumber, @Param("lpn") String lpn);

  LabelData findByDeliveryNumberAndUPCAndLabelType(
      @Param("deliveryNumber") Long deliveryNumber,
      @Param("upc") String upc,
      @Param("labelType") LabelType labelType);

  PurchaseOrderInfo findByDeliveryNumberAndLPNLike(
      @Param("deliveryNumber") Long deliveryNumber, @Param("lpn") String lpn);
}
