package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class GLSReceiveRequest {

  private Long deliveryNumber;
  private String poNumber;
  private Integer poLineNumber;
  private String doorNumber;
  private Long itemNumber;
  private Integer ti;
  private Integer hi;
  private String problemTagId;
  private Integer quantity;
  private String quantityUOM;
  private String receiveAsCorrection;
  private String rotateDate;
  private Integer subcenterId;
  private Integer orgUnitId;
  private String createUser;
  private String overrideInd;
  private String overrideUserId;
  private String vnpkWgtFmtCode;
  private Integer freightBillQty;
}
