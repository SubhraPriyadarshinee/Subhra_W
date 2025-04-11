package com.walmart.move.nim.receiving.core.common.exception;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

public enum GdmError {
  ITEM_NOT_FOUND_ERROR("searchDocument", "Item Not Found", "No PO/POLine information found."),
  PO_POL_NOT_FOUND_ERROR("searchDocument", "Item Not Found", "No po/poline found in %s"),
  GDM_NETOWRK_ERROR(
      "createInstruction",
      "Network Error",
      "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues."),
  MISSING_ITEM_INFO_ERROR(
      "createInstruction",
      "Missing Item Info",
      "CubeQty, Weight and UOM informations are mandatory for POCON request. Please see a supervisor for assistance."),
  PO_LINE_REJECTION_ERROR(
      "createInstruction",
      "PO Line Rejected",
      "The PO Line %s for this item has been rejected. Please see a supervisor for assistance with this item."),
  PO_FINALIZED_ERROR(
      "createInstruction",
      "Unable to Receive",
      "PO %1$s for this item has already been confirmed. Submit a problem ticket to receive against a new PO."),
  MISSING_FREIGHT_BILL_QTY(
      "createInstruction",
      "Missing Freight Bill Qty",
      "Freight bill quantity information is missing for PO %s and line %s. Contact support to get this resolved."),
  MISSING_DSDC_INFO_ERROR(
      "createInstruction",
      "Missing Item Info",
      "CubeQty, weight and UOM information are mandatory for DSDC request.Please see a supervisor for assistance."),
  DSDC_PO_INFO_ERROR(
      "createInstruction",
      "DSDC PO Found",
      "This freight belongs to a DSDC PO. Please receive using Print DSDC Label option"),
  VENDOR_COMPLAINT_ITEM_NUMBER_MISSING(
      "createInstruction",
      "Missing Item number",
      "This item number is not valid. Please try again or report this to your supervisor if it continues."),
  PO_OR_POL_CANCELLED_ERROR(
      "createInstruction",
      "PO/POL Rejected",
      "The PO: %s or PO Line: %s for this item has been cancelled. Please see a supervisor for assistance with this item."),
  ITEM_NOT_CONVEYABLE_ERROR(
      "createInstruction",
      "Item Not Conveyable",
      "This item is not eligible for manual receiving. The item scanned needs to be DA conveyable. Please use this feature for DA conveyable items only."),
  ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE(
      "createInstruction",
      "Item Not Conveyable",
      "The item scanned needs to be DA conveyable. Please use this feature for DA conveyable items only."),
  PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE(
      "createInstruction",
      "PO CON not allowed",
      "The item scanned needs to be DA conveyable. Please use this feature for DA conveyable items only."),
  PO_LINE_CLOSED_ERROR(
      "createInstruction",
      "PO Line Closed",
      "The PO Line %s for this item has been closed. Please see a supervisor for assistance with this item.");

  private final String errorCode;
  private final String errorHeader;
  private final String errorMessage;

  private static ResourceBundleMessageSource messageSource;

  static {
    messageSource = new ResourceBundleMessageSource();
    messageSource.setBasenames("headerMessages");
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorHeader() {
    return errorHeader;
  }

  public String getLocalisedErrorHeader() {
    try {
      return messageSource.getMessage(
          "GDM_ERR_HDR_" + this.name(), null, LocaleContextHolder.getLocale());
    } catch (Exception exception) {
      return errorHeader;
    }
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  GdmError(String errorCode, String errorHeader, String errorMessage) {
    this.errorCode = errorCode;
    this.errorHeader = errorHeader;
    this.errorMessage = errorMessage;
  }
}
