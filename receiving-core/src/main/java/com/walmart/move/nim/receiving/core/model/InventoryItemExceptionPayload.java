package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InventoryItemExceptionPayload {
  private Long itemNumber;
  private String locationName;
  private String reasonDesc;
  private Integer purchaseCompanyId;
  private String baseDivisionCode;
  private Integer currentQty;
  private Integer adjustBy;
  private String adjustedQuantityUOM;
  private String financialReportingGroup;
  private String currentQuantityUOM;
  private String itemUpc;
  private Boolean isResolveRaisedExceptions;
  private String comment;
  private Integer reasonCode;
  private String orgUnitId;
  private String trackingId;
  private String createUserid;

  @Override
  public String toString() {
    return "InventoryItemExceptionRequestItem{"
        + "itemNumber = '"
        + itemNumber
        + '\''
        + ",locationName = '"
        + locationName
        + '\''
        + ",reasonDesc = '"
        + reasonDesc
        + '\''
        + ",purchaseCompanyId = '"
        + purchaseCompanyId
        + '\''
        + ",baseDivisionCode = '"
        + baseDivisionCode
        + '\''
        + ",currentQty = '"
        + currentQty
        + '\''
        + ",adjustBy = '"
        + adjustBy
        + '\''
        + ",adjustedQuantityUOM = '"
        + adjustedQuantityUOM
        + '\''
        + ",financialReportingGroup = '"
        + financialReportingGroup
        + '\''
        + ",currentQuantityUOM = '"
        + currentQuantityUOM
        + '\''
        + ",itemUpc = '"
        + itemUpc
        + '\''
        + ",isResolveRaisedExceptions = '"
        + isResolveRaisedExceptions
        + '\''
        + ",comment = '"
        + comment
        + '\''
        + ",reasonCode = '"
        + reasonCode
        + '\''
        + ",orgUnitId = '"
        + orgUnitId
        + '\''
        + ",createUserid = '"
        + createUserid
        + '\''
        + ",trackingId = '"
        + trackingId
        + '\''
        + "}";
  }
}
