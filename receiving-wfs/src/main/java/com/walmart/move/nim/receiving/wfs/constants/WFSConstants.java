package com.walmart.move.nim.receiving.wfs.constants;

public interface WFSConstants {
  String WFS_INSTRUCTION_SERVICE = "wfsInstructionService";
  String WFS_CONTAINER_SERVICE = "wfsContainerService";
  String WFS_INVENTORY_ADJUSTMENT_PROCESSOR = "wfsInventoryAdjustmentProcessor";
  String WFS_DOCK_TAG_SERVICE = "wfsDockTagService";
  String WFS_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER = "wfsUpdateContainerQuantityRequestHandler";
  String WFS_DELIVERY_METADATA_SERVICE = "wfsDeliveryMetaDataService";
  String WFS_CONTAINER_REQUEST_HANDLER = "wfsContainerRequestHandler";
  String WFS_RECEIVE_INSTRUCTION_HANDLER = "wfsReceiveInstructionHandler";
  String WFS_INSTRUCTION_UTILS = "wfsInstructionUtils";
  String WFS_INSTRUCTION_SEARCH_REQUEST_HANDLER = "wfsInstructionSearchRequestHandler";
  String WFS_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER =
      "wfsCancelMultipleInstructionRequestHandler";
  String WFS_RECEIPT_SUMMARY_PROCESSOR = "wfsReceiptSummaryProcessor";
  String WFS_LABEL_ID_PROCESSOR = "wfsLabelIdProcessor";

  // Feature flags for WFS
  String WFS_INSTRUCTION_FLOW_V2_ENABLED = "wfsInstructionFlowV2Enabled";
  String WFS_BLOCK_OVERAGE_RECEIVING = "wfsBlockOverageReceiving";
  String WFS_BLOCK_OVERAGE_RECEIVING_MOBILE = "wfsBlockOverageReceivingMobile";
  String TCL_FREE_ACCEPTABLE_DELIVERY_CODES = "tclFreeAcceptableDeliveryStatusCodes";

  // Instruction codes for WFS
  String WFS_DOCK_TAG_INSTRUCTION_CODE = "WFSDockTag";
  String WFS_WORKSTATION_INSTRUCTION_CODE = "Workstation";
  String WFS_QUANTITY_CAPTURE_INSTRUCTION_CODE = "WFS Quantity Capture";
  String OVERAGE_RECEIVING_INSTRUCTION_CODE = "WFSOverageReceiving";

  // Exception Messages
  String INVALID_CONTAINER_FOR_WFS_INV_STATUS_AND_CTR_TYPE_EXCEPTION_MSG =
      "Invalid container for WFS, inventory status {} and container type {}.";
  String INVALID_CONTAINER_FOR_WFS_CTR_STATUS_EXCEPTION_MSG =
      "Invalid container for WFS, container status {}.";
  String INVALID_CONTAINER_FOR_WFS_CTR_STATUS =
      "Please scan a valid LPN. Only casepack and induct LPNs supported.";
  String INVALID_CONTAINER_FOR_WFS_DELIVERY_STATUS_EXCEPTION_MSG =
      "Invalid container for WFS, delivery status {}";
  String INVALID_CONTAINER_FOR_WFS_RESTART_DCNT_CTR_STATUS_BACKOUT =
      "The label you scanned is in BACKOUT state and is not eligible for restarting decant.";

  // WFS Pallet Receiving
  String MAX_NUMBER_OF_WFS_PALLET_LABELS = "maxNumberOfWfsPalletLabels";
  int DEFAULT_MAX_NUMBER_OF_WFS_PALLET_LABELS = 4;

  // Label Related Constants
  String WFS_PALLET = "WFSPallet";
  String WFS_CASEPACK = "CASEPACK";
  String WFS_LABEL_TIMESTAMP_PATTERN = "MM/dd/yy HH:mm:ss";
  String VALIDATE_INSTRUCTION_WITH_SOURCE_MESSAGE_ID = "validateInstructionWithSourceMessageId";

  // Source related constants
  String SOURCE_MOBILE = "Mobile";
  String SOURCE_WEB = "Web";

  // WFS 2D BARCODE RELATED CONSTANTS
  String WFS_TWO_D_BARCODE_SCAN_TYPE_DOCUMENT_SEARCH_HANDLER =
      "wfsTwoDBarcodeScanTypeDocumentSearchHandler";
  String WFS_NOT_FOUND_ERROR_FROM_GDM =
      "Item UPC retrieved through scan not found for the delivery. Please scan item UPC to receive.";
  String WFS_ERROR_FROM_GDM =
      "System is unable to process QR code at the moment. Please scan item UPC to receive.";
  String WFS_TWO_D_BARCODE_PO_NOT_FOUND_ERROR_MSG =
      "PO information retrieved through scan not found for the delivery. Please scan item UPC to receive.";

  String WFS_INSTRUCTION_HELPER_SERVICE = "wfsInstructionHelperService";
  String IS_PACK_ID_BASED_RECEIVING_ENABLED = "isPackIdBasedReceivingEnabled";
  String GDM_PALLET_PACK_SCAN_API = "/api/deliveries/{deliveryNumber}/shipments/{identifier}";
  String AUDIT_REQUIRED = "auditRequired";
  String PACKS_KEY = "packs";
  String IDENTIFIER = "identifier";
  String AUDIT_DETAILS = "auditDetail";
  String APPLICATION_VND_DELIVERY_SHIPMENT_SCAN_RESPONSE3_JSON =
      "application/vnd.DeliveryShipmentScanResponse3+json";
  String IS_TCL_FREE_RECEIVING_ENABLED = "isTCLFreeReceivingEnabled";
  String IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED = "isReceivingContainerCheckEnabled";
  String SHELF_LPN = "shelfLPN";
}
