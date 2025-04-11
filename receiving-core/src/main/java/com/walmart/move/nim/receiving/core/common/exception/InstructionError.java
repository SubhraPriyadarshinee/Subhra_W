package com.walmart.move.nim.receiving.core.common.exception;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

public enum InstructionError {
  OF_SPECIFIC_ERROR(
      "createInstruction",
      "Error in OF",
      "Error encountered while grouping allocations. Please try again or report this to your supervisor for assistance."),
  INVALID_ALLOCATION(
      "createInstruction",
      "Invalid Allocations",
      "System is returning invalid allocations. Please report this to your supervisor for assistance."),
  NO_ALLOCATION(
      "createInstruction",
      "No Allocations",
      "No allocations. Please try again or report this to your supervisor if it continues."),
  CHANNEL_FLIP(
      "createInstruction",
      "Channel Flipped",
      "Channel has flipped, there are no allocations. Please try again or report this to your supervisor if it continues."),
  OF_OP_FETCHING_ERROR(
      "createInstruction",
      "Network Error",
      "System has encountered connectivity error between OF and OP while fetching allocation. Please report this to your supervisor for assistance."),
  OF_OP_BLOCKING_ERROR(
      "createInstruction",
      "Network Error",
      "System has encountered connectivity error between OF and OP while blocking allocation. Please report this to your supervisor for assistance."),
  OF_YMS_ERROR(
      "createInstruction",
      "Network Error",
      "System has encountered connectivity error between OF and YMS while fetching destination location for pallet. Please try again or report this to your supervisor for assistance."),
  OF_NETOWRK_ERROR(
      "createInstruction",
      "Network Error",
      "We’re having trouble reaching OF now. Please try again or report this to your supervisor if it continues."),
  OVERAGE_ERROR(
      "overage",
      "",
      "The allowed number of cases for this item have been received. Please report the remaining as overage."),
  MUTLI_USER_ERROR(
      "createInstruction",
      "Info",
      "A new pallet cannot be created until the pallets owned by other users for this item are completed. Please work on another item or request for pallet transfer."),
  RX_MUTLI_USER_ERROR(
      "createInstruction",
      "Info",
      "A new pallet cannot be created until the pallets owned by user %s for this item are completed. Please work on another item or request for pallet transfer."),
  MUTLI_USER_ERROR_SPLIT_PALLET(
      "createSplitPalletInstruction",
      "Info",
      "A new pallet cannot be created until open instructions for this item is completed. Please work on another item."),
  MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING(
      "createProblemInstruction",
      "Info",
      "A new pallet cannot be created until open instructions for this item is completed. Please work on another item."),
  NO_PURCHASE_REF_TYPE_ERROR(
      "createInstruction",
      "No PurchaseRef Type Found",
      "No purchase reference type found. Please try again or report this to your supervisor if it continues."),
  NO_MATCHING_CAPABALITY_ERROR(
      "createInstruction",
      "Error in Create Instruction",
      "No matching capability found for purchase reference type: %s. Please try again or report this to your supervisor if it continues."),
  NEW_ITEM_ERROR(
      "createInstruction",
      "Register Item",
      "Item number %1$s is new. Please register item at the nearest Cubiscan station."),
  NO_UPC_ERROR(
      "createInstruction",
      "No UPC/ASN Barcode Found",
      "Unknown request. Request doesn't contain UPC or ASN barcode info."),
  OF_GENERIC_ERROR(
      "createInstruction",
      "Error in OF",
      "Looks like the request couldn’t be completed right now. This could be caused by poor connectivity or system issues."),
  SLOTTING_GENERIC_ERROR(
      "createInstruction", "Error in Slotting Service", "Unable to determine slot"),
  INVALID_BOL_WEIGHT_ERROR(
      "InvalidBolWeightForItem",
      "Invalid BOL Weight",
      "Unable to receive item due to Invalid BOL weight for item in GDM."),
  MISSING_BOL_WEIGHT_ERROR(
      "InvalidBolWeightForItem",
      "Invalid BOL Weight",
      "Oops, CRO Task pending to key weight for this item to be received, contact CRO to proceed."),
  ITEM_NOT_ON_BOL_ERROR(
      "ItemNotOnBOL", "Item Not on BOL", "Unable to receive item, Please contact CRO."),
  INVALID_TI_HI(
      "InvalidTiHi",
      "Invalid Ti*Hi",
      "Unable to receive item due to Invalid Ti[%s]*Hi[%s] in GDM."),
  INVALID_LPN_ERROR(
      "InvalidLpnGenerated",
      "Invalid LPN Generated",
      "There was an issue retrieving some of the information. Please try scanning the item again."),
  INVALID_PO_ERROR("InvalidPO", "Invalid PO", "Unable to receive item due to Invalid PO"),
  DSDC_FEATURE_FLAGGED_ERROR(
      "createInstruction", "DSDC not available", "DSDC receiving is not available at this DC."),
  POCON_FEATURE_FLAGGED_ERROR(
      "createInstruction", "POCON not available", "POCON receiving is not available at this DC."),
  MANUAL_RCV_MANDATORY_FIELD_MISSING(
      "createInstruction",
      "Mandatory Field Missing",
      "Ineligible for manual receiving. Mandatory fields are missing."),
  NO_SSCC_ERROR(
      "createInstruction",
      "Mandatory Field Missing",
      "Ineligible for receiving. Mandatory SSCC field is missing."),
  MISSING_ITEM_DETAILS(
      "MissingItemDetails",
      "Missing item details",
      "Required information is missing for item %s. Contact support to help get this resolved before receiving."),
  HACCP_ITEM_ALERT(
      "HaccpItem",
      "HACCP item alert",
      "Item %s on PO %s is a HACCP item. QC approval is required to continue."),
  ITEM_DATA_MISSING(
      "InvalidItem",
      "Missing item details",
      "Item cannot be received. Required information is missing in the system. Contact support to help get this resolved."),
  WEIGHT_FORMAT_TYPE_CODE_MISSING(
      "InvalidItem", "Missing item details", "WeightFormatTypeCode is missing."),
  WEIGHT_FORMAT_TYPE_CODE_MISMATCH(
      "WeightFormatMismatch",
      "Weight format mismatch",
      "This item was previously received as %s weight but is now showing as %s weight. Please contact your supervisor to get this corrected."),
  PO_ITEM_PACK_ERROR(
      "InvalidPackSize",
      "Invalid PO and Item Pack",
      "Item and PO Pack do not match. Please contact support."),
  RCV_AS_CORRECTION_ERROR(
      "receiveAsCorrection",
      "Receiving Correction",
      "PO %1$s has already been confirmed. This will be received as a receiving correction, confirm to continue."),
  INVALID_SUBCENTER_ID(
      "InvalidSubcenterId",
      "Invalid SubcenterId",
      "Unable to receive item due to Invalid From SubcenterId in GDM."),
  PBYL_DOCKTAG_NOT_PRINTED(
      "PbylDockTagNotPrinted", "PByL DockTag", "Dock tag not printed for PByL");

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

  public String getErrorMessage() {
    return errorMessage;
  }

  public String getLocaliseErrorHeader() {
    try {
      return messageSource.getMessage(
          "INSTR_ERR_HDR_" + this.name(), null, LocaleContextHolder.getLocale());
    } catch (Exception exception) {
      return errorHeader;
    }
  }

  InstructionError(String errorCode, String errorHeader, String errorMessage) {
    this.errorCode = errorCode;
    this.errorHeader = errorHeader;
    this.errorMessage = errorMessage;
  }
}
