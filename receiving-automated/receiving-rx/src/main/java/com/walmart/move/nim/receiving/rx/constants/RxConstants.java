package com.walmart.move.nim.receiving.rx.constants;

public class RxConstants {

  private RxConstants() {}

  public static final String RX_INSTRUCTION_SERVICE = "RxInstructionService";
  public static final String RX_LEGACY_INSTRUCTION_SERVICE = "RxLegacyInstructionService";

  public static final String RX_DELIVERY_SERVICE = "rxDeliveryServiceImpl";

  public static final String RX_UPDATE_INSTRUCTION_HANDLER = "RxUpdateInstructionHandler";
  public static final String RX_LEGACY_UPDATE_INSTRUCTION_HANDLER =
      "RxLegacyUpdateInstructionHandler";

  public static final String RX_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER =
      "RxUpdateContainerQuantityRequestHandler";

  public static final String RX_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER =
      "RxCancelMultipleInstructionRequestHandler";
  public static final String RX_RECEIPT_SUMMARY_PROCESSOR = "rxReceiptSummaryProcessor";
  public static final String RX_INSTRUCTION_SEARCH_HANDLER = "rxInstructionSearchRequestHandler";
  public static final String RX_PROBLEM_FIXIT_SERVICE = "rxFixitProblemService";
  public static final String TWOD_BARCODE_DELIVERY_DOCUMENTS_SEARCH_HANDLER =
      "twoDBarcodeScanTypeDocumentsSearchHandler";

  // Rx Label constants
  public static final String CLIENT_ID = "receiving";
  public static final String LABEL_FORMAT_NAME = "pallet_lpn_rx";
  public static final String DELIVERY_LABEL_FORMAT_NAME = "delivery_label_rx";

  public static final String LPN_LABEL_NAME = "LPN";
  public static final String SLOT = "SLOT";
  public static final String PO_LABEL_NAME = "PO";
  public static final String ITEM_DESC_LABEL_NAME = "itemDesc";
  public static final String ITEM_NUM_LABEL_NAME = "ITEM";
  public static final String TI = "TI";
  public static final String HI = "HI";
  public static final String DEFAULT_TI_HI = "100";
  public static final String VNPK = "VNPK";
  public static final String WHPK = "WHPK";
  public static final String UPC = "UPC";
  public static final String UOM = "UOM";
  public static final String PARTIAL = "PARTIAL";
  public static final String OPEN = "OPEN";
  public static final String LBL_SPLIT_PALLET = "SPLIT PALLET";
  public static final String VDR_SK = "VDR SK";
  public static final String PTAG = "PTAG";
  public static final String RCVR = "RCVR";
  public static final String LN = "LN";
  public static final String QTY = "QTY";
  public static final String PRIME_SLOT_ID = "PRIME_SLOT";
  public static final String LABEL_QTY = "LABEL_QTY";
  public static final String TOTAL_QTY = "TOTAL_QTY";
  public static final String DATE = "DATE";
  public static final String LABEL_TIMESTAMP = "LABELTIMESTAMP";
  public static final String USER = "FULLUSERID";
  public static final String DEFAULT_RDS_LOT = "NO PEDIGREE";
  public static final String BARCODE_SCAN = "BARCODE_SCAN";
  public static final String DELIVERY_NUMBER = "DELIVERY_NUMBER";
  public static final String VENDOR_UPC = "vendorUpc";
  public static final String SHIPMENT_DOCUMENT_ID = "shipmentDocumentId";
  public static final String INSTRUCTION_CODE = "instructionCode";
  public static final String SCANNED_GTIN_DOES_NOT_MATCH_INSTRUCTION_GTIN = "Scanned GTIN doesn't match with the Instruction. Please scan the valid barcode.";
  // End of Rx Label constants

  public static final String SLOTTING_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  public static final String SLOTTING_RESOURCE_RESPONSE_ERROR_MSG =
      "Resource exception from Slotting. Error MSG = %s";

  public static final String SLOTTING_QTY_CORRECTION_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  public static final String SLOTTING_QTY_CORRECTION_RESOURCE_RESPONSE_ERROR_MSG =
      "Resource exception from Slotting. Error MSG = %s";

  public static final String ENABLE_EPCIS_SERVICES_FEATURE_FLAG = "enableEpcisServicesFeature";
  public static final String ENABLE_EPCIS_VERIFICATION_FEATURE_FLAG =
      "enableEpcisVerificationFeature";
  public static final String ENABLE_LABEL_PARTIAL_TAG = "enableLabelPartialTag";
  public static final String ENABLE_ASN_SEARCH_GTIN_ONLY = "enableAsnSearchWithGtin";
  public static final String ENABLE_OUTBOX_INVENTORY_INTEGRATION =
      "enableOutboxInventoryIntegration";
  public static final String ENABLE_INV_LABEL_BACKOUT = "enableInvLabelBackout";
  public static final String IS_BLOCK_EPCIS_SPLIT_PALLET = "isBlockEpcisSplitPallet";
  public static final String IS_EPCIS_PROBLEM_FALLBACK_TO_ASN = "isEpcisProblemFallbackToASN";

  public static final String INVALID_SCANNED_DATA =
      "Ineligible for receiving. Mandatory ScannedData field is missing.";
  public static final String INVALID_SCANNED_DATA_GTIN =
      "Ineligible for receiving. Mandatory ScannedData field GTIN is missing.";
  public static final String INVALID_SCANNED_DATA_LOT =
      "Ineligible for receiving. Mandatory ScannedData field Lot Number is invalid.";
  public static final String INVALID_SCANNED_DATA_SERIAL =
      "Ineligible for receiving. Mandatory ScannedData field Serial is missing.";
  public static final String INVALID_SCANNED_DATA_EXPIRY_DATE =
      "Ineligible for receiving. Mandatory ScannedData field Expiry Date is missing.";
  public static final String INVALID_GDM_EXPIRY_DATE =
          "Setup error. Invalid GDM Expiry date.";
  public static final String CLOSE_DATED_ITEM =
      "Expiration date is less than allowed range or already expired. Please submit a problem ticket.";
  public static final String EXPIRED_ITEM =
      "Expiration date is less than allowed range or already expired. Please submit a problem ticket.";
  public static final String UPDATE_INSTRUCTION_EXCEEDS_QUANTITY = "Update exceeds quantity needed";
  public static final String UPDATE_INSTRUCTION_EXCEEDS_PALLET_QUANTITY =
      "All cases are received by one or more instructions, please receive Pallet.";
  public static final String SHIPMENT_DETAILS_UNAVAILABLE =
      "Shipment Details unavailable while publishing to EPCIS";
  public static final String DEPT_TYPE_UNAVAILABLE =
      "Dept Type is not available in ASN, please report problem.";
  public static final String AUTO_SELECT_PO_NO_OPEN_QTY =
      "Allowed PO Line quantity has been received.";
  public static final String SCANNED_DETAILS_DO_NOT_MATCH =
      "Scanned Lot does not match with Shipment Details. Please quarantine this freight and submit a problem ticket.";
  public static final String COMPLETE_EXISTING_INSTRUCTION =
      "Please complete all Open Instructions for this item before receiving Partial case.";

  public static final String INVALID_SCANNED_DATA_SSCC_NOT_AVAILABLE =
      "Ineligible for receiving. Mandatory ScannedData field SSCC is missing.";
  public static final String INVALID_NO_SSCC_ERROR =
      "Ineligible for receiving. Mandatory SSCC field is missing.";
  public static final String INVALID_SERIALIZED_DATA_FIELDS_MISSING =
      "Ineligibile for receiving. Mandatory serialized data is missing for validation with EPCIS.";
  public static final String INVALID_ALLOWED_CASES_RECEIVED =
      "Number of Cases related to SSCC are already recieved or being received. New instruction cannot be created.";

  public static final String EPCIS_VALIDATION_UNAVAILABLE =
      "EPICS validation is unavailable for this barcode. Scan the case or unit directly to receive. Otherwise, quarantine the freight. ";

  public static final String NO_OPEN_QTY =
      "All quantities related to scanned barcode are already recieved or being received. New instruction cannot be created.";

  public static final String GDM_SEARCH_SHIPMENT_FAILED = "Unable to search Shipment details.";
  public static final String GDM_SEARCH_FOR_DELIVERY_DOC_BY_GTIN_AND_LOT_FAILURE =
      "Error while fetching delivery documents from GDM for the given delivery: %s, GTIN: %s and LOT Number: %s";
  public static final String GDM_SHIPMENT_NOT_FOUND_FOR_DELIVERY_GTIN_AND_LOT =
      "Shipment information is not available for the delivery:%s, gtin:%s and lotNumber:%s. Please quarantine this freight and submit a problem ticket.";
  public static final String GDM_SEARCH_SHIPMENT_FAILURE_AFTER_UPC_CATALOG =
      "Shipment information is not available in GDM after cataloging UPC: %s on delivery: %s";

  public static final String ERROR_IN_PATCHING_INSTRUCTION =
      "Given instruction id: %s is either INVALID or already in COMPLETED status, please verify";

  public static final String INVALID_GDM_DSDA_IND_VALUE =
      "DSDA Ind flag missing in the GDM Response";

  public static final String SERIALIZED_PRODUCT_UPC_NOT_SUPPORTED =
      "UPC/GTIN scan receiving is not supported for this Item. Receive by scanning SSCC.";

  public static final String SERIALIZED_PRODUCT_UPC_RCV_NOT_SUPPORTED =
      "D40 Item cannot be received against ASN";
  public static final String SCANNED_SSCC_NOT_VALID =
      "Scanned SSCC: %s is Pallet SSCC. Please scan a case SSCC to continue receiving";
  public static final String CONTAINER_ALREADY_EXISTS = "Container already exists for %s";
  public static final String INVALID_CREATE_INSTRUCTION_REQUEST =
      "Invalid request to create an instruction";

  // EPCIS constants
  public static final String EPCIS_ACTION_ADD = "ADD";
  public static final String EPCIS_ACTION_OBSERVE = "OBSERVE";
  public static final String EPCIS_ACTION_DELETE = "DELETE";
  public static final String EPCIS_RETUNR_TYPE_PREREC = "PREREC";
  public static final String URN_SGLN = "urn:epc:id:sgln:";
  public static final String URN_BIZSTEP_RECEIVING = "urn:epcglobal:cbv:bizstep:receiving";
  public static final String URN_BIZSTEP_ARRIVING = "urn:epcglobal:cbv:bizstep:arriving";
  public static final String URN_BIZSTEP_UNPACKING_CASE =
      "urn:epcglobal:cbv:bizstep:unpacking_case";
  public static final String URN_BIZSTEP_UNPACKING_ALL = "urn:epcglobal:cbv:bizstep:unpacking_all";
  public static final String URN_BIZSTEP_SHIPPING_RETURN =
      "urn:epcglobal:cbv:bizstep:shipping_return";
  public static final String URN_BIZSTEP_DECOMMISSION = "urn:epcglobal:cbv:bizstep:decommissioning";
  public static final String CLUBBED_ARRIVING = "arriving";
  public static final String CLUBBED_RECEIVING = "receiving";
  public static final String CLUBBED_UNPACKING_CASE = "unpacking_case";
  public static final String CLUBBED_UNPACKING_ALL = "unpacking_all";
  public static final String URN_DISP_ACTIVE = "urn:epcglobal:cbv:disp:active";
  public static final String URN_DISP_IN_PROGRESS = "urn:epcglobal:cbv:disp:in_progress";
  public static final String URN_DISP_INACTIVE = "urn:epcglobal:cbv:disp:inactive";
  public static final String URN_DISP_RETURNED = "urn:epcglobal:cbv:disp:returned";
  public static final String URN_BATCH_NUM = "urn:wm:ai:batchNumber";
  public static final String URN_DATAEX = "urn:wm:ai:dateex";
  public static final String URN_QTY = "urn:wm:ai:qty";
  public static final String URN_AI_ASN = "urn:epcglobal:cbv:btt:desadv";
  public static final String URN_PREPEND = "urn:epcglobal:cbv:bt:";
  public static final String URN_PO = "urn:epcglobal:cbv:btt:po";
  public static final String URN_GTIN = "urn:epcglobal:cbv:btt:gtin";
  public static final String URN_ASN = "urn:epcglobal:cbv:btt:asn";
  public static final String URN_OWNING_PARTY = "urn:epcglobal:cbv:sdt:owning_party";
  public static final String URN_OWNING_PARTY_ID = "urn:epcglobal:cbv:sdt:owning_party_id";
  public static final String URN_SGTIN = "urn:epc:id:sgtin:";
  public static final String URN_SSCC = "urn:epc:id:sscc:";
  public static final String URN_DESADV = "urn:epcglobal:cbv:btt:desadv";
  public static final String GMT_OFFSET = "+00:00";
  public static final String EXEMPTED_DEPT_TYPE = "40";
  public static final String VALID_ATTP_SERIALIZED_TRACKING_STATUS = "ValidationSuccessful";
  public static final String RIGHT_PARANTHESIS = ")";
  public static final String LEFT_PARANTHESIS = "(";
  public static final String COLON = ":";
  public static final String DEFAULT_SLOT = "Z9999";
  public static final String WM_CLIENT_DC = "DC";
  public static final String WM_WORKFLOW_DSD = "DSD";

  public static final String PARENT_ID = "parentID";
  public static final String BOTH_EXPIRY_DATE_AND_LOT = "BOTH-EXPIRY-DATE-AND-LOT";

  public static final String RDS_SLOTTING_TYPE_MANUAL = "MANUAL";

  public static final String RX_COMPLETE_DELIVERY_PROCESSOR = "RxCompleteDeliveryProcessor";

  public static final String RX_CANCEL_CONTAINER_PROCESSOR = "RxCancelContainerProcessor";
  public static final String RX_CONTAINER_ADJUSTMENT_VALIDATOR = "RxContainerAdjustmentValidator";
  public static final String ENABLE_DEPT_CHECK_FEATURE_FLAG = "deptCheckEnabled";
  public static final String SMART_SLOTING_RX_FEATURE_FLAG = "isRxSmartSlottingEnabled";
  public static final String RX_XBLOCK_FEATURE_FLAG = "isRxXblockEnabled";

  public static final String PARENT_CONTAINER_CANNOT_BE_DELETED =
      "Cannot Delete a Parent Container.";
  public static final String TRACKING_ID_CANNOT_BE_EMPTY = "Tracking Id's cannot be empty.";

  public static final String INSTRUCTION_CLOSED_CONTAINER_CANNOT_BE_DELETED =
      "Cannot delete a Container. Its corresponding Instruction is Closed.";
  public static final String INSTRUCTION_NOT_FOUND_FOR_CONTAINER =
      "Cannot find Instruction for the container";
  public static final String INVALID_HASH_VALUE_SENT = "Invalid Hash value";
  public static final String RX_DELETE_CONTAINERS_REQUEST_HANDLER =
      "RxDeleteContainersRequestHandler";
  public static final String TIMEZONE_UTC = "UTC";
  public static final String EPCIS_PUBLISH_DATE_FMT = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

  public static final String INVALID_PO_PO_LINE_STATUS =
      "Po/Poline is not receivable. Please quarantine this freight and submit a problem ticket";

  public static final String INVALID_LPN =
      "Unable to receive item due to Invalid LPN generated from LPN Service.";

  public static final String REQUEST_TRANSFTER_INSTR_ERROR_CODE = "GLS-RCV-MULTI-INST-400";

  public static final String PRINT_LABEL_PRINT_REQUESTS = "printRequests";

  public static final String INVALID_PO_PO_LINE_STATUS_ERROR_FORMAT_PREFIX = "PO %s Line %s";
  public static final String INVALID_PO_STATUS_ERROR_FORMAT_PREFIX = "PO %s";
  public static final String INVALID_PO_STATUS_ERROR_FORMAT = "%s is %s";

  public static final String INSTR_OPTIMISTIC_LOCK_ERROR =
      "There was an issue processing the last scan Serial:%s and Lot:%s. Please re-scan to try again.";

  public static final String INSTR_OPTIMISTIC_LOCK_GENERIC_ERROR =
      "There was an issue processing the last scan. Please re-scan to try again.";

  public static final String PARTIALS_NOT_ALLOWED_IN_SPLIT_PALLET =
      "Partial case receiving is not allowed in split pallet.Please receive scanned item through Partial receiving feature.";

  public static final String NO_SHIPMENT_AVAILABLE_WITH_OPEN_QTY =
      "There is no shipment available with Open Qty.";

  public static final String RX_GET_CONTAINER_REQUEST_HANDLER = "RxGetContainerRequestHandler";

  public static final String UPC_NOT_AVAILABLE =
      "Unknown request. Request doesn't contain UPC or ASN barcode info.";
  public static final String SSCC_NOT_AVAILABLE =
      "Ineligible for receiving. Mandatory SSCC field is missing.";

  public static final String RX_MUTLI_USER_ERROR = "RX_MUTLI_USER_ERROR";
  public static final String IS_EPCIS_ENABLED_VENDOR = "isEpcisEnabledVendor";
  public static final String AUTO = "AUTO";
  public static final String SCANNED_DETAILS_DO_NOT_MATCH_SERIAL =
      "Scanned serial does not match with Shipment Details. Please quarantine this freight and submit a problem ticket.";
  public static final String NO_MANUFACTURE_DETAILS_EPCIS =
      "Partial Response from GDM. No Manufacture Details in GDM Response.";
  public static final String DOCUMENT_ID = "documentId";
  public static final String DOCUMENT_PACK_ID = "documentPackId";
  public static final String SHIPMENT_NUMBER = "shipmentNumber";

  public static final String GDM_CONTAINER_ID = "gdmContainerId";
  public static final String GDM_PARENT_CONTAINER_ID = "gdmParentContainerId";
  public static final String TOP_LEVEL_CONTAINER_SSCC =  "topLevelContainerSscc";
  public static final String TOP_LEVEL_CONTAINER_ID = "topLevelContainerId";

  public static final String UNIT_COUNT = "unitCount";
  public static final String CHILD_COUNT = "childCount";
  public static final String HINTS = "hints";

  public static final String RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS = "Received";
  public static final String OPEN_ATTP_SERIALIZED_RECEIVING_STATUS = "Open";
  public static final String PARTIALLY_RECEIVED_ATTP_SERIALIZED_RECEIVING_STATUS =
      "PartiallyReceived";
  public static final String RTV_ATTP_SERIALIZED_RECEIVING_STATUS = "ReturnToVendor";
  public static final String DECOMISSIONED_ATTP_SERIALIZED_RECEIVING_STATUS = "Decommissioned";

  public static final String FIXIT = "fixit";
  public static final String DAMAGE = "damage";
  public static final String OVERAGE = "overage";
  public static final String DISPOSITION_DESTROY = "DESTROY";
  public static final String DISPOSITION_RETURN_TO_VENDOR = "RETURN_TO_VENDOR";

  public static final String RX_LPN_CACHE_SERVICE = "rxLpnCacheService";
  public static final String RX_LPN_SERVICE = "rxLpnService";
  public static final String BACKOUT_PUBLISHER_FLAG = "isBackoutPublisherEnabled";

  public static final String IS_EPCIS_SMART_RECV_ENABLED = "isEpcisSmartReceivingEnabled";
  public static final String BARCODE_ALREADY_SCANNED =
          "Barcode has already been scanned.";
  public static final String WRONG_PALLET =
          "Scanned case does not belong to this pallet.";
  public static final String WRONG_CASE =
          "Scanned unit does not belong to this case.";

  public interface GdmHints {
    // package related hints
    String SINGLE_SKU_PACKAGE = "SINGLE_SKU_PACKAGE";
    String MULTI_SKU_PACKAGE = "MULTI_SKU_PACKAGE";
    String SSCC_SGTIN_PACKAGE = "SSCC_SGTIN_PACKAGE";
    String HANDLE_AS_CASEPACK = "HANDLE_AS_CASEPACK";
    String FLOOR_LOADED_PACKAGE = "FLOOR_LOADED_PACKAGE";
    String CNTR_WITH_MULTI_LABEL_CHILD_CNTRS = "CNTR_WITH_MULTI_LABEL_CHILD_CNTRS";

    // item related hints
    String CASE_PACK_ITEM = "CASE_PACK_ITEM";
    String PARTIAL_PACK_ITEM = "PARTIAL_PACK_ITEM";
    String UNIT_ITEM = "UNIT_ITEM";
  }

  public interface ReceivingTypes {
    String FULL_PALLET = "FULL-PALLET";
    String CASE = "PLT-UNPACKED-AND-CASES-RCVD";
    String PALLET_FROM_MULTI_SKU = "MULTI-SKU-PLT-UNPACKED-AND-RCVD";
    String FLOOR_LOADED_CASE = "FLOOR-LOADED-CASE";
    String PARTIAL_CASE = "PARTIAL-CASE";
    String CASE_RECEIVED_WITH_UNIT_SCANS = "CASE-RECEIVED-WITH-UNIT-SCANS";
    String PALLET_RECEIVED_WITH_CASE_SCANS = "PALLET-RECEIVED-WITH-CASE-SCANS";
    String MULTI_SKU = "MULTI-SKU";
    String HNDL_AS_CSPK_FULL_PALLET = "HNDL-AS-CSPK-FULL-PALLET";
    String HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD = "HNDL-AS-CSPK-PLT-UNPACKED-AND-CASES-RCVD";
    String HNDL_AS_CSPK_FLOOR_LOADED_CASE = "HNDL-AS-CSPK-FLOOR-LOADED-CASE";
    String HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU = "HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD";
    String PROBLEM_FULL_PALLET = "PROBLEM-FULL-PALLET";
    String PROBLEM_CASE = "PROBLEM-PLT-UNPACKED-AND-CASES-RCVD";
    String PROBLEM_PALLET_FROM_MULTI_SKU = "PROBLEM-MULTI-SKU-PLT-UNPACKED-AND-RCVD";
    String PROBLEM_FLOOR_LOADED_CASE = "PROBLEM-FLOOR-LOADED-CASE";
    String PROBLEM_PARTIAL_CASE = "PROBLEM-PARTIAL-CASE";
    String PROBLEM_CASE_RECEIVED_WITH_UNIT_SCANS = "PROBLEM-CASE-RECEIVED-WITH-UNIT-SCANS";
    String PROBLEM_PALLET_RECEIVED_WITH_CASE_SCANS = "PROBLEM-PALLET-RECEIVED-WITH-CASE-SCANS";
    String PROBLEM_HANDLE_AS_CASEPACK = "PROBLEM-HANDLE-AS-CASEPACK";
    String PROBLEM_HNDL_AS_CSPK_PLT_UNPACKED_AND_CASES_RCVD = "PROBLEM-HNDL-AS-CSPK-PLT-UNPACKED-AND-CASES-RCVD";
    String PROBLEM_HNDL_AS_CSPK_FLOOR_LOADED_CASE = "PROBLEM-HNDL-AS-CSPK-FLOOR-LOADED-CASE";
    String PROBLEM_HNDL_AS_CSPK_FULL_PALLET = "PROBLEM-HNDL-AS-CSPK-FULL-PALLET";
    String PROBLEM_HNDL_AS_CSPK_PALLET_FROM_MULTI_SKU = "PROBLEM-HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD";


  }

  public interface ReceivingContainerTypes {
    String FULL_PALLET = "FULL-PALLET";
    String PARTIAL_CASE = "PARTIAL-CASE";
    String CASE = "CASE";
  }

  public interface Headers {
    String SKIP_ATTP = "skipAttp";
    String SKIP_INVENTORY = "skipInventory";
  }
  public static final String EPCIS_SMART_RECV_CLIENT_ENABLED = "isEpcisSmartReceivingEnabledFromClient";
  public static final String TRUE_STRING = "true";
  public static final String PROBLEM = "PROBLEM";

  public static final String INVALID_PROBLEM_RECEIVING = "Problem label has been created with a different UOM. Please scan valid barcode.";
  public static final String ROTATE_DATE_FORMAT = "yyyy-MM-dd";
  public static final String INVALID_ROTATE_DATE =
          "Ineligible for receiving. Invalid Rotate Date";
}
