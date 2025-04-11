package com.walmart.move.nim.receiving.core.common;

import java.util.Arrays;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum BusinessTransactionType {
  PURCHASE_ORDER(33, "Purchase Order"),
  DCTODC_TRANSFER_PO_10(10, "DCtoDC Transfer PO"),
  DCTODC_TRANSFER_PO_11(11, "DCtoDC Transfer PO"),
  DCTODC_TRANSFER_PO(23, "DCtoDC Transfer PO");

  private int poTypeCode;
  private String transType;

  /**
   * Mapping business trans type
   *
   * @param poTypeCode
   * @return
   */
  public static BusinessTransactionType mapBusinessTransactionType(int poTypeCode) {
    return Arrays.stream(BusinessTransactionType.values())
        .filter(type -> type.getPoTypeCode() == poTypeCode)
        .findFirst()
        .orElse(BusinessTransactionType.PURCHASE_ORDER);
  }
}
