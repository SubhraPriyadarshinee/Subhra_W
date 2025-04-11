package com.walmart.move.nim.receiving.reporting.model;

import com.walmart.move.nim.receiving.core.entity.BaseMTEntity;
import lombok.Data;

@Data
public class RxItemCatalogReportData extends BaseMTEntity {

  private String createUserId;
  private String createTs;
  private String deliveryNumber;
  private String itemNumber;
  private String oldItemUPC;
  private String newItemUPC;
  private String vendorNumber;
  private String vendorStockNumber;
  private String exemptItem;
  private String correlationId;
}
