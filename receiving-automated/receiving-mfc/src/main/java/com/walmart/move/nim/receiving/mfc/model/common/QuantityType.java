package com.walmart.move.nim.receiving.mfc.model.common;

import java.util.Arrays;
import org.apache.commons.lang3.StringUtils;

public enum QuantityType {
  DECANTED("Decanted", "DCNT", 00),
  OVERAGE("Overage", "O55", 55),
  SHORTAGE("Shortage", "S54", 54),
  DAMAGE("Damage", "D74", 74),
  REJECTED("Rejected", "R78", 78),
  RECEIVED("Received", "RCV", 01),
  COLD_CHAIN_REJECT("Rejected", "R83", 83),
  NOTMFCASSORTMENT("Rejected", "R86", 86),
  FRESHNESSEXPIRATION("Rejected", "R87", 87),
  MFCOVERSIZE("Rejected", "R88", 88),
  MFC_TO_STORE_TRANSFER("Rejected", "R91", 91),
  WRONG_TEMP_ZONE("Rejected", "R98", 98),
  NGR_SHORTAGE("Shortage", "S153", 153),
  NGR_REJECT("Rejected", "R152", 152);

  private String type;
  private String reasonCode;
  private Integer inventoryErrorReason;

  QuantityType(String type, String reasonCode, Integer inventoryErrorReason) {
    this.type = type;
    this.reasonCode = reasonCode;
    this.inventoryErrorReason = inventoryErrorReason;
  }

  public String getType() {
    return type;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public Integer getInventoryErrorReason() {
    return inventoryErrorReason;
  }

  public static QuantityType getQuantityType(Integer reasonCode) {
    return Arrays.stream(QuantityType.values())
        .filter(quantityType -> quantityType.getInventoryErrorReason().equals(reasonCode))
        .findFirst()
        .orElse(QuantityType.DECANTED);
  }

  public static QuantityType getQuantityType(String quantityType) {
    return Arrays.stream(QuantityType.values())
        .filter(qty -> StringUtils.equalsIgnoreCase(qty.getType(), quantityType))
        .findFirst()
        .orElse(QuantityType.DECANTED);
  }
}
