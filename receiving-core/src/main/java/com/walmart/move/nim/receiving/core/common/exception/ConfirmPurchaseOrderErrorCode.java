package com.walmart.move.nim.receiving.core.common.exception;

import java.util.HashMap;
import java.util.Map;

public class ConfirmPurchaseOrderErrorCode {

  private ConfirmPurchaseOrderErrorCode() {}

  public static final String ALREADY_CONFIRMED = "ALREADY_CONFIRMED";
  public static final String SINGLE_USER_OPEN_INSTRUCTIONS = "SINGLE_USER_OPEN_INSTRUCTIONS";
  public static final String MULTI_USER_OPEN_INSTRUCTIONS = "MULTI_USER_OPEN_INSTRUCTIONS";
  public static final String DATA_OUT_OF_SYNC = "DATA_OUT_OF_SYNC";
  public static final String DCFIN_ERROR = "DCFIN_ERROR";
  public static final String GDM_ERROR = "GDM_ERROR";
  public static final String GDM_ERROR_PO_FINALIZE_NOT_ALLOWED = "PO_FINALIZE_NOT_ALLOWED";
  public static final String DEFAULT_ERROR = "DEFAULT_ERROR";
  public static final String GLS_QUANTITY_MISMATCH = "GLS_QUANTITY_MISMATCH";

  private static final Map<String, ConfirmPurchaseOrderError> errorValueMap = new HashMap<>();

  static {
    errorValueMap.put(ALREADY_CONFIRMED, ConfirmPurchaseOrderError.ALREADY_CONFIRMED);
    errorValueMap.put(
        SINGLE_USER_OPEN_INSTRUCTIONS, ConfirmPurchaseOrderError.SINGLE_USER_OPEN_INSTRUCTIONS);
    errorValueMap.put(
        MULTI_USER_OPEN_INSTRUCTIONS, ConfirmPurchaseOrderError.MULTI_USER_OPEN_INSTRUCTIONS);
    errorValueMap.put(DATA_OUT_OF_SYNC, ConfirmPurchaseOrderError.DATA_OUT_OF_SYNC);
    errorValueMap.put(DCFIN_ERROR, ConfirmPurchaseOrderError.DCFIN_ERROR);
    errorValueMap.put(GDM_ERROR, ConfirmPurchaseOrderError.GDM_ERROR);
    errorValueMap.put(
        GDM_ERROR_PO_FINALIZE_NOT_ALLOWED, ConfirmPurchaseOrderError.GDM_PO_FINALIZE_NOT_ALLOWED);
    errorValueMap.put(DEFAULT_ERROR, ConfirmPurchaseOrderError.DEFAULT_ERROR);
    errorValueMap.put(GLS_QUANTITY_MISMATCH, ConfirmPurchaseOrderError.GLS_QUANTITY_MISMATCH);
  }

  public static ConfirmPurchaseOrderError getErrorValue(String errorCode) {
    return errorValueMap.get(errorCode);
  }
}
