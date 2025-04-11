package com.walmart.move.nim.receiving.core.common.exception;

public enum ConfirmPurchaseOrderError {
  ALREADY_CONFIRMED("alreadyConfirmed", "", "PO is already confirmed"),
  SINGLE_USER_OPEN_INSTRUCTIONS(
      "openInstructions",
      "openInstructions",
      "%s has open instruction(s) against this PO, finish or cancel receiving"),
  MULTI_USER_OPEN_INSTRUCTIONS(
      "openInstructions",
      "openInstructions",
      "%s + %s other(s) have open instruction(s) against this PO, finish or cancel receiving"),
  DATA_OUT_OF_SYNC(
      "problemAndDamageDataOutOfSync",
      "problemAndDamageDataOutOfSync",
      "Problem or damage data has changed, refresh the screen"),
  DCFIN_ERROR(
      "unableToReachDCFin",
      "unableToReachDCFin",
      "Couldn’t connect to DC Finance, retry or contact support"),
  GDM_ERROR(
      "unableToReachGDM", "unableToReachGDM", "Couldn’t connect to GDM, retry or contact support"),
  GDM_PO_FINALIZE_NOT_ALLOWED(
      "poFinalizeNotAllowedGDM",
      "PoAlreadyConfirmedOrRejected",
      "PO is already confirmed or rejected"),
  DEFAULT_ERROR(
      "unableToConfirm",
      "unableToConfirm",
      "Unable to confirm this PO. Please contact your supervisor or support"),

  GLS_QUANTITY_MISMATCH(
      "glsQuantityMismatch",
      "glsQuantityMismatch",
      "PO validation failed with GLS. Please contact support.");

  private final String errorCode;
  private final String errorHeader;
  private final String errorMessage;

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorHeader() {
    return errorHeader;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  ConfirmPurchaseOrderError(String errorCode, String errorHeader, String errorMessage) {
    this.errorCode = errorCode;
    this.errorHeader = errorHeader;
    this.errorMessage = errorMessage;
  }

  public static boolean containsCode(String code) {
    for (ConfirmPurchaseOrderError confirmPurchaseOrderError : values())
      if (confirmPurchaseOrderError.getErrorCode().equals(code)) return true;
    return false;
  }
}
