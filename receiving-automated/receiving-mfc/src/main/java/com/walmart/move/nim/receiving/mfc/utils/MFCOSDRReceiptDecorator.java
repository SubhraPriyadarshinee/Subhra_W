package com.walmart.move.nim.receiving.mfc.utils;

import static com.walmart.move.nim.receiving.mfc.utils.MFCUtils.returnZeroIfNull;

import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import java.util.Objects;

public class MFCOSDRReceiptDecorator {

  private Receipt receipt;

  public MFCOSDRReceiptDecorator(Receipt receipt) {
    this.receipt = receipt;
  }

  public Integer getQuantity() {
    return returnZeroIfNull(receipt.getQuantity());
  }

  public String getQuantityUom() {
    return receipt.getQuantityUom();
  }

  public Integer getDamagedQuantity() {
    return returnZeroIfNull(receipt.getFbDamagedQty());
  }

  public String getDamagedQuantityUom() {
    return receipt.getFbDamagedQtyUOM();
  }

  public OSDRCode getDamagedReasonCode() {
    return receipt.getFbDamagedReasonCode();
  }

  public Integer getRejectedQuantity() {
    return !MFCUtils.isAuditReportedShortage(this.receipt)
        ? returnZeroIfNull(receipt.getFbRejectedQty())
        : 0;
  }

  public String getRejectedQuantityUom() {
    return !MFCUtils.isAuditReportedShortage(this.receipt) ? receipt.getFbRejectedQtyUOM() : null;
  }

  public OSDRCode getRejectedReasonCode() {
    return !MFCUtils.isAuditReportedShortage(this.receipt)
        ? receipt.getFbRejectedReasonCode()
        : null;
  }

  public Integer getShortQuantity() {
    return !MFCUtils.isAuditReportedShortage(this.receipt)
        ? returnZeroIfNull(receipt.getFbShortQty())
        : 0;
  }

  public String getShortQuantityUom() {
    return !MFCUtils.isAuditReportedShortage(this.receipt) ? receipt.getFbShortQtyUOM() : null;
  }

  public OSDRCode getShortageReasoncode() {
    return !MFCUtils.isAuditReportedShortage(this.receipt) ? receipt.getFbShortReasonCode() : null;
  }

  public Integer getOverageQuantity() {
    return returnZeroIfNull(receipt.getFbOverQty());
  }

  public String getOverageQtyUom() {
    return receipt.getFbOverQtyUOM();
  }

  public OSDRCode getOverageReasoncode() {
    return receipt.getFbOverReasonCode();
  }

  public Integer getConcealedShortageQty() {
    if (Objects.isNull(receipt.getFbConcealedShortageQty())
        && MFCUtils.isAuditReportedShortage(this.receipt)) {
      return returnZeroIfNull(receipt.getFbRejectedQty());
    }
    return returnZeroIfNull(receipt.getFbConcealedShortageQty());
  }

  public OSDRCode getConcealedShortageReasonCode() {
    if (Objects.isNull(receipt.getFbConcealedShortageQty())
        && MFCUtils.isAuditReportedShortage(this.receipt)) {
      return receipt.getFbConcealedShortageReasonCode();
    }
    return receipt.getFbConcealedShortageReasonCode();
  }
}
