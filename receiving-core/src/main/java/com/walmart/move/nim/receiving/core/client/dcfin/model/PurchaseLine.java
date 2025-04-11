package com.walmart.move.nim.receiving.core.client.dcfin.model;

import com.walmart.move.nim.receiving.core.model.DistributionsItem;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public class PurchaseLine {

  private String documentLineNo;
  private Integer itemNumber;
  /**
   * For OneAtlas then check value is 'true' for itemConverted else false. For NON OneAtlas also it
   * will be false as default but should not be considered for validation.
   */
  private boolean isAtlasItem;

  private String sellerId;
  private String baseDivCode;
  private String financialReportGrpCode;
  private String inboundChannelMethod;
  private Integer primaryQty;
  private String lineQtyUOM;
  private Integer warehousePackEachQty;
  private Integer vendorPackEachQty;
  private String weightFormatType;
  private Float secondaryQty;
  private String secondaryQtyUOM;
  private String promoBuyInd;
  private String warehouseAreaCode;
  private String containerId;
  private Integer freightBillQty;
  private List<DistributionsItem> distributions;
}
