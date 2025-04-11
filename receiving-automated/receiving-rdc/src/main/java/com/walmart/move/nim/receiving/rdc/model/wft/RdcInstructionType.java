package com.walmart.move.nim.receiving.rdc.model.wft;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdcInstructionType {
  SSTK_UPC_RECEIVING("RdcBuildPallet", "Build Pallet for RDC"),
  FLIB_SSTK_CASE_RECEIVING("RdcFlibSstkCase", "Flib SSTK cases for RDC"),
  OVERAGE_ALERT_RECEIVING("RdcOveragePallet", "Overage Pallet for RDC"),
  DA_WORK_STATION_SCAN_TO_PRINT_RECEIVING("ScanToPrint", "Receive DA Vendor Case Pack"),
  DA_RECEIVING_PACK_TYPE_VALIDATION("DAPackTypeValidation", "Item properties validation"),
  NON_CON_RTS_PUT_RECEIVING("RDCDARtsPutReceiving", "Non Con RTS Put Receiving"),
  DSDC_RECEIVING("DSDCReceiving", "DSDC Receiving"),
  DSDC_AUDIT_REQUIRED("DSDCAuditRequired", "DSDC Audit Required"),
  DA_QTY_RECEIVING("RDCDAReceiving", "DA Quantity Receiving"),
  // Exception Receiving Instruction
  LABEL_VALIDATED("offlineLabelValidated", "offlineLabelValidated"),
  EXCEPTION_LPN_NOT_FOUND("lpnNotFound", "lpnNotFound"),
  LPN_BLOCKED("lpnBlocked", "lpnBlocked"),
  LPN_RECEIVED("labelReceived", "labelReceived"),
  EXCEPTION_LPN_RECEIVED("lpnReceived", "lpnReceived"),
  ERROR_LPN_BACKOUT("labelBackedOut", "labelBackedOut"),
  LPN_NOT_RECEIVED("exceptionLpnReceiving", "exceptionLpnReceiving"),
  BREAKOUT("lpnBreakout", "lpnBreakout"),
  OVERAGE("lpnOverage", "lpnOverage"),
  LPN_NOT_RECEIVED_SSTK("exceptionSSTKLpnReceiving", "exceptionSSTKLpnReceiving"),
  RDC_INBOUND_EXCEPTION_RCV("inboundExceptionHandling", "RDC Exception Receiving"),
  LPN_RECEIVED_SSTK("SSTKLpnReceived", "SSTKLpnReceived");

  private String instructionCode;
  private String instructionMsg;
}
