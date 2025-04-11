/** */
package com.walmart.move.nim.receiving.core.common.exception;

/** @author m0g028p */
public final class ExceptionDescriptionConstants {

  private ExceptionDescriptionConstants() {
    throw new UnsupportedOperationException("Constructor is not allowed");
  }

  public static final String VALID_DELIVERY_STATUS_MESSAGE = "Delivery status should be %s or %s";
  public static final String RECEIPTS_NOT_FOUND_ERROR_MSG =
      "No record found for this delivery number %s in receipt";
  public static final String RECEIPTS_NOT_FOUND_FOR_PO_ERROR_MSG =
      "No record found for this purchase reference number %s in receipt";
  public static final String OSDR_CONFIG_NOT_DONE_FOR_DC_ERROR_MSG =
      "OSDR is not configured for facilitynum: %s";
  public static final String OSDR_RECEIPTS_NOT_FOUND_ERROR_MSG =
      "No osdr details for this delivery number %s in receipt";
  public static final String CONTAINER_NOT_FOUND_ERROR_MSG =
      "Container not found for tracking id %s";
  public static final String INVALID_INVENTORY_ADJUSTMENT_ERROR_MSG =
      "Invalid inventory adjustment %s for tracking id %s";
  public static final String ITEM_MDM_ERROR_MSG =
      "Exception from item mdm. HttpResponseStatus= %s ResponseBody= %s";
  public static final String AUDIT_ERROR_MSG =
      "Exception from audit. HttpResponseStatus= %s ResponseBody= %s";
  public static final String ITEM_MDM_SERVICE_DOWN_ERROR_MSG =
      "Item mdm service is down. Error message = %s";
  public static final String AUDIT_SERVICE_DOWN_ERROR_MSG =
      "Audit service is down. Error message = %s";
  public static final String ITEM_CACHE_SERVICE_DOWN_ERROR_MSG =
      "Item cache service is down. Error message = %s";
  public static final String RECEIVING_LOAD_SERVICE_DOWN_ERROR_MSG =
      "Receiving load service is down. Error message = %s";
  public static final String ITEM_MDM_BAD_DATA_ERROR_MSG =
      "Item mdm service returned empty response for itemNumber=%s";
  public static final String AUDIT_BAD_DATA_ERROR_MSG =
      "Audit service returned empty response for request=%s";
  public static final String NOT_IMPLEMENTED_EXCEPTION = "Method not implemented.";
  public static final String GDM_ERROR_MSG =
      "Exception from GDM. HttpResponseStatus= %s ResponseBody= %s";
  public static final String GDM_BAD_DATA_ERROR_MSG = "GDM service returned empty response.";
  public static final String GDM_SERVICE_DOWN_ERROR_MSG = "GDM service is down. Error message = %s";
  public static final String FIT_BAD_DATA_ERROR_MSG =
      "FIT/FIXIT service returned error for invalid request";
  public static final String GENERIC_ERROR_RE_TRY_OR_REPORT =
      "Please try refreshing or report this to your supervisor if it continues.";
  public static final String CONTAINER_NOT_FOUND_BY_PO_POLINE_ERROR_MSG =
      "Container not found for Po=%s and PoLINE=%s";
  public static final String CONTAINER_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG =
      "Container not found for packageBarcodeValue=%s";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_DISPOSITION_TYPE =
      "Container cannot be created without disposition type.";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_WORKFLOW_CREATE_REASON =
      "Container with potential fraud disposition cannot be created without workflowCreateReason";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_WORKFLOW_ID =
      "Container with potential fraud disposition cannot be created without workflowId";
  public static final String INVALID_DELIVERY_LINE_LEVEL_FBQ_DESC =
      "Scanned delivery does not have FBQ for any PO line";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_SCANNED_LABEL =
      "Container with potential fraud disposition cannot be created without scannedLabel";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_ITEM_LABEL =
      "Container with potential fraud disposition cannot be created without scannedItemLabel";
  public static final String MISSING_DESTINATION_PARENT_CONTAINER_TYPE_CONFIG =
      "Missing destination parent container type in CCM config for dispositionType = %s";
  public static final String INVALID_CREATE_CONTAINER_REQUEST_CONTAINER_TAG =
      "Container cannot be created without container tag.";
  public static final String INVALID_UPDATE_CONTAINER_REQUEST_CONTAINER_TAG =
      "Container cannot be updated without container tag.";
  public static final String INVALID_DATA_DESC =
      "Invalid request. Please provide either of following : %s";
  public static final String WORKFLOW_CREATE_WITH_TRK_ID_NOT_SUPPORTED_ERROR_MSG =
      "Workflow creation with item tracking ID is not supported!";
  public static final String PUBLISH_EVENTS_FOR_NON_FRAUD_NOT_SUPPORTED_ERROR_MSG =
      "Publishing events for non fraud action is not supported!";
  public static final String INVALID_PACKAGE_TRACKER_REQUEST_REASON_CODE =
      "Package cannot be tracked without proper reason code.";
  public static final String INVALID_ITEM_TRACKER_REQUEST_REASON_CODE =
      "Item cannot be tracked without proper reason code.";
  public static final String PACKAGE_NOT_FOUND_BY_PACKAGE_BARCODE_VALUE_ERROR_MSG =
      "Package not found for packageBarcodeValue=%s";
  public static final String ITEM_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG =
      "Item not found for trackingId=%s";
  public static final String WORKFLOW_NOT_FOUND_FOR_ID_ERROR_MSG =
      "No receiving workflow found for ID=%s";
  public static final String PRODUCT_CATEGORY_GROUP_NOT_FOUND_FOR_PRODUCT_TYPE_ERROR_MSG =
      "No receiving product category group found for productType=%s";
  public static final String PRODUCT_CATEGORY_GROUP_UNABLE_TO_IMPORT_ERROR_MSG =
      "Unable to import product category group csv file";
  public static final String WORKFLOW_ALREADY_EXISTS_FOR_ID_ERROR_MSG =
      "A receiving workflow already exists for workflowId=%s";
  public static final String WORKFLOW_ITEM_NOT_FOUND_FOR_ID_ERROR_MSG =
      "No receiving workflow item found for ID=%s";
  public static final String ITEM_NOT_FOUND_FOR_GTIN_ERROR_MSG = "Item not found for gtin=%s";
  public static final String CONTAINER_NOT_FOUND_BY_GTIN_FOR_DISPOSITION_TYPE_ERROR_MSG =
      "Container not found by gtin=%s for disposition type=%s";
  public static final String CONTAINER_NOT_FOUND_BY_GTIN_ERROR_MSG =
      "Container not found by gtin=%s";
  public static final String CONTAINER_NOT_FOUND_FOR_TRACKING_ID_ERROR_MSG =
      "Container not found for Tracking Id=%s";
  public static final String CONTAINER_ALREADY_EXISTS_FOR_TRACKING_ID_ERROR_MSG =
      "Container already exists for Tracking Id=%s";
  public static final String CONTAINER_NOT_FOUND_BY_ITEM_ERROR_MSG =
      "Container not found by itemNumber=%s";
  public static final String EXCEPTION_ON_PROCESSING_INV_ADJUSTMENT_MSG =
      "Exception thrown while processing inventory adjustment message for trackingId: %s and error: %s";
  public static final String CONTAINER_ITEM_NOT_FOUND_BY_ITEM_AND_UPC_ERROR_MSG =
      "Container item data not found for itemNumber= %s and upc= %s";

  public static final String INVALID_ATTR_ERR_MSG = "Attribute %s value is empty or invalid";
  public static final String PALLET_CORRECTION_NOTALLOWED_EPCIS_VENDOR =
      "Label correction is not allowed for serialized freight";
  public static final String SPLIT_PALLET_NOT_ALLOWED_EPCIS_VENDOR =
      "Split Pallet is not allowed for serialized freight";
  public static final String MULTI_SKU_PALLET_NOT_ALLOWED_EPCIS_VENDOR =
      "Multi Sku Pallet is not enabled for serialized freight";
  public static final String CASE_2D_NOT_ALLOWED_PARTIAL_FLOW = "Scan Unit 2D barcode.";

  public static final String BARCODE_ALREADY_RECEIVED =
      "Scanned barcode has been already Received. Please scan a valid barcode.";

  public static final String SCANNED_2D_NOT_MATCHING_EPCIS =
      "Scanned 2D barcode is not matching the EPCIS data.";
  public static final String CASE_2D_NOT_ALLOWED_PARTIAL_CASE =
      "Receive through partial flow. Scan Unit 2D barcode.";
  public static final String BARCODE_NOT_IN_RECEIVABLE_STATUS =
      "Scanned barcode is not in receivable status %s. Please scan a valid barcode.";
  public static final String CONTAINER_NOT_FOUND_BY_SO_NUMBER_ERROR_MSG =
      "Container not found for salesOrderNumber=%s";

  public static final String ERROR_THROWN_BY_GDM = "Error thrown by gdm, Delivery: {} ";

  public static final String LABEL_DATA_NOT_FOUND_ERROR_MSG = "Label Data not found for lpn: %s";

  public static final String UPC_VALIDATION_FAILED_ERROR_MSG =
      "UPC validation failed for lpn: %s and upc: %s";
  public static final String OVERAGE_ERROR_MSG =
      "Received maximum allowable quantity for this item. lpn: %s";
  public static final String NO_ALLOCATION_ERROR_MSG = "No Allocation found for lpn: %s";
  public static final String CHANNEL_FLIP_ERROR_MSG = "Channel has flipped. lpn: %s";
  public static final String OF_GENERIC_ERROR_MSG = "Error occurred while calling FDE for lpn: %s ";
  public static final String ITEM_CHANGED_AFTER_PO_AUTO_SELECTION_ERROR_MSG =
      "Different item number found after PO Auto Selection. lpn: %s, existing item: %s, new item: %s";

  public static final String AUTO_SELECT_PO_POLINE_FAILED_ERROR_MSG =
      "No valid po line found in delivery: %s to receive this container.";
  public static final String UPDATE_INSTRUCTION_ENTER_VALID_QTY =
          "This item needs to be received as Full-Pallet, please enter valid quantity.";
  public static final String UPDATE_INSTRUCTION_SCAN_VALID_CASE =
          "This item needs to be received as Full-Pallet, please scan a valid case.";

}
