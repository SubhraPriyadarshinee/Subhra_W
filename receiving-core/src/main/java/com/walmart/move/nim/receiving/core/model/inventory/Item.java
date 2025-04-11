package com.walmart.move.nim.receiving.core.model.inventory;

import java.util.Date;
import java.util.Set;
import lombok.Data;

@Data
public class Item {
  public int quantity;
  public String itemNumber;
  public Date rotateDate;
  public String promoBuyInd;
  public String warehouseRotationTypeCode;
  public String baseDivisionCode;
  public String financialReportingGroup;
  public int purchaseCompanyId;
  public int vendorPkRatio;
  public int warehousePkRatio;
  public String channelType;
  public String inboundChannelType;
  public String upcNumber;
  public int availableToSellQty;
  public String qtyUOM;
  public double warehousePackSell;
  public String warehousePackSellUOM;
  public String description;
  public String secondaryDescription;
  public double vnpkWeightQty;
  public String vnpkWeightUOM;
  public String vnpkCubeQty;
  public String vnpkCubeUOM;
  public String weightFormatType;
  public int returnOrderLineNumber;
  public int saleOrderLineNumber;
  public Date receivedDate;
  public int poTypeCode;
  public int deptNumber;
  public int vendorNumber;
  public boolean isConveyable;
  public int totalQty;
  public Set<Reference> references;
  public String profiledWarehouseArea;
  public String warehouseAreaCode;
}
