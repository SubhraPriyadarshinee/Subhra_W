package com.walmart.move.nim.receiving.core.common.exception;

import java.util.HashMap;
import java.util.Map;

public class GdmErrorCode {

  private static final Map<String, GdmError> errorValueMap = new HashMap<>();

  static {
    errorValueMap.put("GDM_NETWORK_ERROR", GdmError.GDM_NETOWRK_ERROR);
    errorValueMap.put("ITEM_NOT_FOUND_ERROR", GdmError.ITEM_NOT_FOUND_ERROR);
    errorValueMap.put("MISSING_ITEM_INFO_ERROR", GdmError.MISSING_ITEM_INFO_ERROR);
    errorValueMap.put("PO_POL_NOT_FOUND_ERROR", GdmError.PO_POL_NOT_FOUND_ERROR);
    errorValueMap.put("ITEM_NOT_CONVEYABLE_ERROR", GdmError.ITEM_NOT_CONVEYABLE_ERROR);
    errorValueMap.put(
        "ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE",
        GdmError.ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE);
    errorValueMap.put("PO_FINALIZED_ERROR", GdmError.PO_FINALIZED_ERROR);
    errorValueMap.put("MISSING_FREIGHT_BILL_QTY", GdmError.MISSING_FREIGHT_BILL_QTY);
    errorValueMap.put("PO_LINE_REJECTION_ERROR", GdmError.PO_LINE_REJECTION_ERROR);
    errorValueMap.put("MISSING_DSDC_INFO_ERROR", GdmError.MISSING_DSDC_INFO_ERROR);
    errorValueMap.put("DSDC_PO_INFO_ERROR", GdmError.DSDC_PO_INFO_ERROR);
    errorValueMap.put(
        "VENDOR_COMPLAINT_ITEM_MISSING", GdmError.VENDOR_COMPLAINT_ITEM_NUMBER_MISSING);
    errorValueMap.put("PO_POL_CANCELLED_ERROR", GdmError.PO_OR_POL_CANCELLED_ERROR);
    errorValueMap.put("PO_LINE_CLOSED_ERROR", GdmError.PO_LINE_CLOSED_ERROR);
    errorValueMap.put(
        "PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE",
        GdmError.PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE);
  }

  public static GdmError getErrorValue(String errorCode) {
    return errorValueMap.get(errorCode);
  }
}
