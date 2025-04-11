package com.walmart.move.nim.receiving.rdc.constants;

import com.walmart.move.nim.receiving.core.common.InventoryLabelType;
import com.walmart.move.nim.receiving.core.model.labeldownload.RejectReason;
import com.walmart.move.nim.receiving.rdc.label.LabelFormat;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import java.util.*;

public class RdcConstants {

  private RdcConstants() {}

  public static final String RDC_INSTRUCTION_SERVICE = "rdcInstructionService";
  public static final String RDC_DOCKTAG_SERVICE = "rdcDockTagService";
  public static final String RDC_RECEIVE_INSTRUCTION_HANDLER = "rdcReceiveInstructionHandler";
  public static final String RDC_RECEIVE_EXCEPTION_HANDLER = "rdcReceiveExceptionHandler";
  public static final String RDC_REFRESH_INSTRUCTION_HANDLER = "rdcRefreshInstructionHandler";
  public static final String RDC_ITEM_CATALOG_SERVICE = "rdcItemCatalogService";
  public static final String RDC_ITEM_SERVICE_HANDLER = "rdcItemServiceHandler";
  public static final String RDC_COMPLETE_DELIVERY_PROCESSOR = "rdcCompleteDeliveryProcessor";
  public static final String RDC_OSDR_SERVICE = "rdcOsdrService";
  public static final String RDC_OSDR_PROCESSOR = "rdcOsdrProcessor";
  public static final String RDC_DELIVERY_METADATA_SERVICE = "rdcDeliveryMetaDataService";
  public static final String RDC_DELIVERY_SERVICE = "rdcDeliveryService";
  public static final String RDC_MESSAGE_PUBLISHER = "rdcMessagePublisher";
  public static final String RDC_CANCEL_CONTAINER_PROCESSOR = "rdcCancelContainerProcessor";
  public static final String RDC_LPN_CACHE_SERVICE = "rdcLpnCacheService";
  public static final String RDC_LPN_SERVICE = "rdcLpnService";
  public static final String RDC_UPDATE_CONTAINER_QUANTITY_HANDLER =
      "rdcUpdateContainerQuantityHandler";
  public static final String RDC_RECEIPT_SUMMARY_PROCESSOR = "rdcReceiptSummaryProcessor";
  public static final String RDC_UPDATE_INSTRUCTION_HANDLER = "rdcUpdateInstructionHandler";
  public static final String RDC_KAFKA_INVENTORY_ADJ_EVENT_PROCESSOR =
      "rdcKafkaInventoryEventProcessor";
  public static final String RDC_FIXIT_PROBLEM_SERVICE = "rdcFixitProblemService";
  public static final String RDC_FIT_PROBLEM_SERVICE = "rdcFitProblemService";
  public static final String VENDOR_DELIVERY_DOCUMENT_SEARCH_HANDLER =
      "vendorBasedDeliveryDocumentsSearchHandler";

  public static final int LBL_TTL = 100;
  public static final int DA_LBL_TTL = 24;
  public static final int MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT = 72;

  public static final String WFT_LOCATION_ID = "WMT-User-Location";
  public static final String WFT_LOCATION_TYPE = "WMT-User-Location-Type";
  public static final String WFT_SCC_CODE = "WMT-User-Location-SCC";
  public static final String WFT_LOCATION_NAME = "WMT-User-Location-Name";
  public static final String DA_RECEIVING_CAPABILITY = "receivingDA";

  public static final String PROVIDER_ID = "RDC-RCV";
  public static final String OUTBOUND_CHANNEL_METHOD_SSTKU = "SSTKU";
  public static final String OUTBOUND_CHANNEL_METHOD_CROSSDOCK = "CROSSDOCK";
  public static final String PO_LINE_IS_CANCELLED_IN_RDS = "NIMRDS-025";
  public static final String QUANTITY_RECEIVED_ERROR_FROM_RDS = "Received %s response from RDS";
  public static final int QTY_TO_RECEIVE = 1;
  public static final String UNDERSCORE = "_";
  public static int EIGHTEEN_LENGTH = 18;
  public static final String RDC_OVERAGE_EXCEED_ERROR_MESSAGE =
      "Reached maximum receivable quantity threshold for this PO %s and POLine %s combination, Please cancel/Finish this pallet.";
  public static final String PTAG_RESOLVED_BUT_LINE_REJECTED_OR_CANCELLED =
      "PO line given in the problem resolution is already in cancelled or rejected. Please see your supervisor for assistance.";
  public static final String RDS_SLOTTING_TYPE_MANUAL = "MANUAL";
  public static final String RDS_SLOTTING_TYPE_SPLIT = "SPLIT";
  public static final String BREAK_PACK_CONVEY_PICKS_MESSAGE =
      "%s is a breakpack item with conveyable boxes inside. Please open this case and label "
          + "the inner cases.";
  public static final String MASTER_BREAK_PACK_MESSAGE =
      "%s is a masterpack item. The inner cases will need to be labeled separately.";
  public static final String RTS_PUT_ITEM_MESSAGE = "Non Con RTS PUT for RDC";
  public static final String[] X_BLOCK_ITEM_HANDLING_CODES = {"X", "R"};

  public static final String DSDC_CHANNEL_METHODS_FOR_RDC = "DSDC";
  public static final String SPLIT_PALLET_RECEIVING_NOT_SUPPORTED_FOR_DA_FREIGHT =
      "Split Pallet not supported for DA freight";

  // -------- Error Messages --------
  public static final String DTID_DELIVERY_CANNOT_BE_EMPTY =
      "Both DockTagID and Delivery can not be empty, at least one is required.";
  public static final String DA_PURCHASE_REF_TYPE_MSG =
      "It's a DA freight. Please use NGR application to receive.";
  public static final String UNSUPPORTED_PURCHASE_REF_TYPE_MSG_FOR_RDC =
      "Received delivery document with invalid channel method for delivery: %s and UPC: %s";
  public static final String RECEIVED_QTY_BAD_RESPONSE_ERROR_MSG =
      "Client exception from RDS. HttpResponseStatus= %s ResponseBody = %s";
  public static final String X_BLOCK_ITEM_ERROR_MSG =
      "Item %s is showing as blocked and cannot be received. Please contact the QA team on how to proceed.";
  public static final int HAZMAT_ITEM = 1;
  public static final String SSCC_SCAN_RECEIVING_NOT_ALLOWED =
      "Receiving freight by SSCC is not supported.";
  public static final String INVALID_RDC_RECEIVING_FEATURE_TYPES = "Invalid Receiving feature type";

  // Print Label attributes
  public static final String PRINT_LABEL_NUM = "1";
  public static final String PRINT_LABEL_CNT = "1";
  public static final String TIMESTAMP_LABEL_TIME_CODE_1 = "1";
  public static final String TIMESTAMP_LABEL_TIME_CODE_2 = "2";
  public static final String TIMESTAMP_LABEL_TIME_CODE_3 = "3";
  public static final String TIMESTAMP_LABEL_TIME_CODE_4 = "4";
  public static final String TIMESTAMP_LABEL_TIME_CODE_5 = "5";
  public static final String TIMESTAMP_LABEL_TIME_CODE_6 = "6";
  public static final String TIMESTAMP_LABEL_TIME_CODE_7 = "7";
  public static final String TIMESTAMP_LABEL_TIME_CODE_8 = "8";

  // NGR urls
  public static final String ITEM_CATALOG_REQUEST_URL =
      "%s/receiving-load/us/%s/items/createVCOEItem";
  public static final String HAZMAT_VERIFICATION_TS_UPDATE_URL =
      "/receiving-item-cache/us/%s/items/hazmatVerified";
  public static final String ITEM_UPDATE_URL_FOR_ITEM_CACHE =
      "%s/receiving-item-cache/us/%s/items/saveTempItemProfChange";
  public static final String DELIVERY_RECEIPTS = "%s/receiving-load/us/%s/load/receipts/%s";

  public static final String NGR_LOAD = "NGR_RECEIVING_LOAD";
  public static final String INVALID_LPN =
      "Unable to receive item due to Invalid LPN generated from LPN Service.";

  public static final String NOT_APPLICABLE = "NA";

  public static final String BREAK_PACK_TYPE_CODE = "B";
  public static final String CASE_PACK_TYPE_CODE = "C";
  public static final String CONVEYABLE_HANDLING_CODE = "C";
  public static final String MASTER_BREAK_PACK_TYPE_CODE = "P";

  public static final String RDC_CANCEL_MULTIPLE_INSTRUCTION_REQUEST_HANDLER =
      "RdcCancelMultipleInstructionRequestHandler";

  // slotting
  public static final String SLOTTING_SSTK_RECEIVING_METHOD = "SSTK";
  public static final String SLOTTING_DA_RECEIVING_METHOD = "DA";
  public static final String SPLIT_PALLET_SLOTTING_FEATURE_TYPE = "splitPallet";
  public static final String GET_RCVD_QTY_ERROR_FROM_RDS = "getReceivedQtyFromRDS";
  public static final String ITEM_CONFIG_SERVICE_ENABLED_FOR_SSTK =
      "item.config.service.enabled.for.sstk";
  public static final String ITEM_CONFIG_SERVICE_ENABLED_FOR_DA_WORK_STATION_AND_SCAN_TO_PRINT =
      "da.item.config.service.enabled";
  public static final String ATLAS_ITEMS_ONBOARDED = "atlas.items.onboarded";
  public static final String IS_CALCULATE_PUTAWAY_QTY_BY_CONTAINERS_ENABLED =
      "is.calculate.putaway.qty.by.containers.enabled";
  public static final String IS_ATLAS_DSDC_AUDIT_ENABLED = "is.atlas.dsdc.audit.enabled";
  public static final String IS_RDS_INTEGRATION_DISABLED = "is.rds.integration.disabled";
  public static final String IS_ATLAS_CONVERSION_ENABLED_FOR_ALL_SSTK_ITEMS =
      "is.atlas.conversion.enabled.for.all.sstk.items";
  public static final String IS_NGR_SERVICES_DISABLED = "is.ngr.services.disabled";
  public static final String SMART_SLOTTING_INTEGRATION_ENABLED =
      "smart.slotting.integration.enabled";
  public static final String IS_INVENTORY_INTEGRATION_ENABLED = "is.inventory.integration.enabled";
  public static final String IS_DCFIN_INTEGRATION_ENABLED = "dcfin.integration.enabled";
  public static final String IS_DEFAULT_PRO_DATE_ENABLED_FOR_SSTK =
      "is.default.pro.date.enabled.for.sstk";
  public static final String IS_MOVE_PUBLISH_ENABLED = "move.publish.enabled";
  public static final String IS_DOCKTAG_MOVE_PUBLISH_ENABLED = "is.docktag.move.publish.enabled";
  public static final String IS_FREIGHT_IDENTIFICATION_FEATURE_ENABLED =
      "freight.identification.feature.enabled";
  public static final String IS_ASN_MULTI_SKU_ENABLED = "asnMultiSkuEnabled";
  public static final String IS_AUTO_POPULATE_RECEIVE_QTY_ENABLED = "autoPopulateReceiveQtyEnabled";

  public static final String RDC_VENDOR_VALIDATOR = "rdcVendorValidator";

  public static final String THREE_SCAN_DOCKTAG_INSTRUCTION_CODE = "3ScanDocktag";
  public static final String THREE_SCAN_DOCKTAG_INSTRUCTION_MESSAGE = "3 scan Docktag";
  public static final String THREE_SCAN_DOCKTAG_FEATURE_TYPE = "3SCAN_DOCKTAG";
  public static final int DOCKTAG_LABEL_INDEX_START_POSITION = 0;
  public static final int DOCKTAG_LABEL_INDEX_END_POSITION = 6;
  public static final String IS_NEW_DOCKTAG_LABEL_FORMAT_ENABLED =
      "is.new.docktag.label.format.enabled";
  public static final int DELIVERY_TYPE_CODE_LENGTH = 4;
  public static final int FREIGHT_TYPE_CODE_LENGTH = 5;
  public static final String NIM_RDS_DA_BACK_OUT_LABEL_RESPONSE_SUCCESS_CODE = "0";

  // DA
  public static final String[] DA_WORK_STATION_AND_SCAN_TO_PRINT_FEATURE_TYPES = {
    "WORK_STATION", "SCAN_TO_PRINT"
  };
  public static final String DA_WORK_STATION_FEATURE_TYPE = "WORK_STATION";
  public static final String DA_SCAN_TO_PRINT_FEATURE_TYPE = "SCAN_TO_PRINT";
  public static final String[] RDC_RECEIVING_FEATURE_TYPES = {
    "WORK_STATION", "SCAN_TO_PRINT", "3SCAN_DOCKTAG"
  };
  public static final String NON_DA_PURCHASE_REF_TYPE_MSG =
      "SSTK Freight type is not workstation eligible";
  public static final String IS_DA_QTY_RECEIVING_ENABLED = "is.da.qty.receiving.enabled";
  public static final String IS_RTS_PUT_RECEIVING_ENABLED = "is.non.con.rts.put.receiving.enabled";
  public static final String DSDC_PURCHASE_REF_TYPE = "Scan pack number to receive DSDC freight";

  public static final String DA_R8000_SLOT = "R8000";
  public static final String DA_R8001_SLOT = "R8001";
  public static final String DA_R8002_SLOT = "R8002";
  public static final String DA_R8005_SLOT = "R8005";
  public static final String DA_P1001_SLOT = "P1001";
  public static final String DA_P0900_SLOT = "P0900";
  public static final String DA_P2001_SLOT = "P2001";
  public static final String DA_V0050_SLOT = "V0050";
  public static final String DA_V0051_SLOT = "V0051";
  public static final String DSDC_AUDIT_SLOT = "AUDIT";
  public static final Map<String, LabelFormat> DA_LABEL_FORMAT_MAP;
  public static final Map<String, LabelFormat> ATLAS_DA_LABEL_FORMAT_MAP;

  static {
    Map<String, LabelFormat> daLabelFormatMap = new HashMap<>();
    daLabelFormatMap.put(DA_R8000_SLOT, LabelFormat.DA_STORE_FRIENDLY);
    daLabelFormatMap.put(DA_R8001_SLOT, LabelFormat.DA_STORE_FRIENDLY);
    daLabelFormatMap.put(DA_R8002_SLOT, LabelFormat.DSDC);
    daLabelFormatMap.put(DA_R8005_SLOT, LabelFormat.DOTCOM);
    daLabelFormatMap.put(DA_P1001_SLOT, LabelFormat.DA_CONVEYABLE_INDUCT_PUT);
    daLabelFormatMap.put(DA_P0900_SLOT, LabelFormat.DA_CONVEYABLE_INDUCT_PUT);
    daLabelFormatMap.put(DA_P2001_SLOT, LabelFormat.DA_CONVEYABLE_INDUCT_PUT);
    daLabelFormatMap.put(DA_V0050_SLOT, LabelFormat.DA_NON_CONVEYABLE_VOICE_PUT);
    daLabelFormatMap.put(DA_V0051_SLOT, LabelFormat.DA_NON_CONVEYABLE_VOICE_PUT);
    daLabelFormatMap.put(DSDC_AUDIT_SLOT, LabelFormat.DSDC_AUDIT);
    DA_LABEL_FORMAT_MAP = Collections.unmodifiableMap(daLabelFormatMap);
  }

  static {
    Map<String, LabelFormat> atlasDaLabelFormatMap = new HashMap<>();
    atlasDaLabelFormatMap.put(
        InventoryLabelType.R8000_DA_FULL_CASE.getType(), LabelFormat.ATLAS_DA_STORE_FRIENDLY);
    atlasDaLabelFormatMap.put(
        InventoryLabelType.DA_BREAK_PACK_PUT_INDUCT.getType(), LabelFormat.ATLAS_DA_CONVEYABLE_PUT);
    ATLAS_DA_LABEL_FORMAT_MAP = Collections.unmodifiableMap(atlasDaLabelFormatMap);
  }

  // Item Cache Hazmat Verification
  public static final String ITEM_CACHE_API_INTERNAL_SERVER_ERROR =
      "GLS-RCV-ITEM-CACHE-SERVER-ERROR-500";
  public static final String INVALID_ITEM_CACHE_REQUEST = "GLS-RCV-INVALID-ITEM-CACHE-REQ-400";

  public static final Map<String, String> DA_CASE_PACK_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_BREAK_PACK_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_MASTER_CASEPACK_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_MASTER_BREAKPACK_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_VALID_PACKTYPE_HANDLING_METHODS_MAP;

  public static final Map<String, String> DA_CASEPACK_VOICE_PUT_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_BREAKPACK_VOICE_PUT_HANDLING_METHODS_MAP;
  public static final Map<String, String> DA_NON_CON_HANDLING_CODES_MAP;
  public static final Map<String, String> DA_LABEL_INSTRUCTION_PACK_TYPE_MAP;

  static {
    Map<String, String> caseHandlingMethodsMap = new HashMap<>();
    caseHandlingMethodsMap.put("CC", "Casepack Conveyable");
    caseHandlingMethodsMap.put("CB", "Casepack Non-Conveyable To RTS PUT");
    caseHandlingMethodsMap.put("CN", "Casepack Non-Conveyable Slotting");
    caseHandlingMethodsMap.put("CP", "Casepack Voice PUT");
    caseHandlingMethodsMap.put("CL", "Casepack Non-Conveyable To Shipping");
    caseHandlingMethodsMap.put("CV", "Casepack Non-Con Voice Pick");
    caseHandlingMethodsMap.put("CD", "Casepack To Depal");
    caseHandlingMethodsMap.put("CS", "Casepack Conveyable Storage");
    caseHandlingMethodsMap.put("CT", "Casepack Non-Conveyable Storage");
    caseHandlingMethodsMap.put("CR", "Casepack Restricted");
    caseHandlingMethodsMap.put("CI", "Casepack Automatic Inbound");
    caseHandlingMethodsMap.put("CJ", "Casepack Manual Inbound");
    caseHandlingMethodsMap.put("CE", "Item Ineligible for Symbotic");
    DA_CASE_PACK_HANDLING_METHODS_MAP = Collections.unmodifiableMap(caseHandlingMethodsMap);
  }

  static {
    Map<String, String> breakHandlingMethodsMap = new HashMap<>();
    breakHandlingMethodsMap.put("BC", "Breakpack Conveyable");
    breakHandlingMethodsMap.put("BN", "Breakpack Non-Conveyable Slotting");
    breakHandlingMethodsMap.put("BY", "Breakpack Breakcrane");
    breakHandlingMethodsMap.put("BM", "Breakpack Conveyable Picks");
    breakHandlingMethodsMap.put("BP", "Breakpack Voice PUT");
    breakHandlingMethodsMap.put("BV", "Breakpack Non-Con Voice Pick");
    breakHandlingMethodsMap.put("BR", "Breakpack Restricted");
    breakHandlingMethodsMap.put("BE", "Item Ineligible for Symbotic Breakpack Cases");
    breakHandlingMethodsMap.put("BB", "Breakpack Non-Conveyable To RTS PUT");
    breakHandlingMethodsMap.put("BJ", "Breakpack Symbotic Manual");
    breakHandlingMethodsMap.put("BI", "Breakpack Symbotic Auto");
    DA_BREAK_PACK_HANDLING_METHODS_MAP = Collections.unmodifiableMap(breakHandlingMethodsMap);
  }

  static {
    Map<String, String> masterHandlingMethodsMap = new HashMap<>();
    masterHandlingMethodsMap.put("MC", "Casepack Master Carton Conveyable");
    masterHandlingMethodsMap.put("MN", "Casepack Master Carton Non-Conveyable");
    masterHandlingMethodsMap.put("MP", "Casepack Master Carton Voice PUT");
    masterHandlingMethodsMap.put("MV", "Casepack Master Carton Non-Con Voice Pick");
    masterHandlingMethodsMap.put("MR", "Casepack Master Carton Restricted");
    DA_MASTER_CASEPACK_HANDLING_METHODS_MAP = Collections.unmodifiableMap(masterHandlingMethodsMap);
  }

  static {
    Map<String, String> masterBreakHandlingMethodsMap = new HashMap<>();
    masterBreakHandlingMethodsMap.put("PC", "Breakpack Master Carton Conveyable");
    masterBreakHandlingMethodsMap.put("PN", "Breakpack Master Carton Non-Conveyable");
    masterBreakHandlingMethodsMap.put("PP", "Breakpack Master Carton Voice PUT");
    masterBreakHandlingMethodsMap.put("PV", "Breakpack Master Carton Non-Con Voice Pick");
    masterBreakHandlingMethodsMap.put("PR", "Breakpack Master Carton Restricted");
    DA_MASTER_BREAKPACK_HANDLING_METHODS_MAP =
        Collections.unmodifiableMap(masterBreakHandlingMethodsMap);
  }

  static {
    Map<String, String> daValidPackTypeHandlingMethodMap = new HashMap<>();
    daValidPackTypeHandlingMethodMap.putAll(DA_CASE_PACK_HANDLING_METHODS_MAP);
    daValidPackTypeHandlingMethodMap.putAll(DA_BREAK_PACK_HANDLING_METHODS_MAP);
    daValidPackTypeHandlingMethodMap.putAll(DA_MASTER_CASEPACK_HANDLING_METHODS_MAP);
    daValidPackTypeHandlingMethodMap.putAll(DA_MASTER_BREAKPACK_HANDLING_METHODS_MAP);
    DA_VALID_PACKTYPE_HANDLING_METHODS_MAP =
        Collections.unmodifiableMap(daValidPackTypeHandlingMethodMap);
  }

  static {
    Map<String, String> casePackVoiceHandlingMethodMap = new HashMap<>();
    casePackVoiceHandlingMethodMap.put("CP", "Casepack Voice PUT");
    casePackVoiceHandlingMethodMap.put("MP", "Casepack Master Carton Voice PUT");
    DA_CASEPACK_VOICE_PUT_HANDLING_METHODS_MAP =
        Collections.unmodifiableMap(casePackVoiceHandlingMethodMap);
  }

  static {
    Map<String, String> breakPackVoiceHandlingMethodMap = new HashMap<>();
    breakPackVoiceHandlingMethodMap.put("BP", "Breakpack Voice PUT");
    breakPackVoiceHandlingMethodMap.put("PP", "Breakpack Master Carton Voice PUT");
    DA_BREAKPACK_VOICE_PUT_HANDLING_METHODS_MAP =
        Collections.unmodifiableMap(breakPackVoiceHandlingMethodMap);
  }

  static {
    Map<String, String> nonConHandlingCodesMap = new HashMap<>();
    nonConHandlingCodesMap.put("P", "VOICEPUT");
    nonConHandlingCodesMap.put("N", "NONCON");
    DA_NON_CON_HANDLING_CODES_MAP = Collections.unmodifiableMap(nonConHandlingCodesMap);
  }

  static {
    Map<String, String> labelInstructionPackTypeCodeMap = new HashMap<>();
    labelInstructionPackTypeCodeMap.put("CP", "C");
    labelInstructionPackTypeCodeMap.put("BP", "B");
    DA_LABEL_INSTRUCTION_PACK_TYPE_MAP =
        Collections.unmodifiableMap(labelInstructionPackTypeCodeMap);
  }

  public static final String DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE = "CB";
  public static final String DA_BREAK_PACK_VOICE_PUT_ITEM_HANDLING_CODE = "BP";
  public static final String DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE = "BM";
  public static final String[] DA_VOICE_PUT_HANDLING_CODS = {"CP", "BP", "MP", "PP"};
  public static final String DA_CASEPACK_VOICE_PUT = "CP";
  public static final String DA_BREAKPACK_VOICE_PUT = "BP";
  public static final String[] NON_CON_ITEM_HANDLING_CODES = {"P", "N"};
  public static final String NON_CON_RTS_PUT_HANDLING_CODE = "B";
  public static final String PALLET_RECEIVING_HANDLING_CODE = "L";
  public static final String NON_CON_HANDLING_CODES_INFO_MESSAGE =
      "You are about to receive %s freight!";
  public static final int CONTAINER_COUNT_ONE = 1;
  public static final int DEFAULT_BREAK_PACK_RATIO = 1;
  public static final int RDC_DA_CASE_RECEIVE_QTY = 1;
  public static final int DSDC_SSCC_CASE_RECEIVE_QTY = 1;
  public static final int RDC_DSDC_AUDIT_CASE_RECEIVE_QTY = 0;

  public static final String ROUTING_LABEL_STORE_ZONE = "R";
  public static final String ROUTING_LABEL_AISLE = "L";

  public static final Integer[] AD_PO_TYPE_CODES = {23, 33, 73, 83, 93};
  public static final Integer[] WR_PO_TYPE_CODES = {20, 22, 40, 42, 50};
  public static final Integer[] WPM_PO_TYPE_CODES = {10, 11, 12, 14, 18};
  public static final String DEFAULT_PO_CODE = "GO";
  public static final String DSDC_AUDIT_FLAG = "Y";
  public static final Integer MAX_CONTAINER_CREATION_PER_TRANSACTION_IN_RDS = 10;
  public static final String DSDC_PACK_NOT_FOUND = "ASN information was not found";
  public static final String DSDC_PACK_ERROR_MSG_QUOTES_REPLACE = "\"";
  public static final String DSDC_PACK_ERROR_MSG_REGEX = "Error:";
  public static final String DA_DESTINATION_STORE_NUMBER = "storeNumber";
  public static final String IS_ATLAS_DSDC_RECEIVING_ENABLED = "is.atlas.dsdc.receiving.enabled";
  public static final String SLOTTING_DA = "DA";
  public static final String SLOTTING_MANUAL = "MANUAL";
  public static final String LBL_DIVISION_MAX_LENGTH = "%02d";
  public static final int LBL_ITEM_DESCRIPTION_MAX_LENGTH = 20;
  public static final int LBL_ITEM_UPC_MAX_LENGTH = 13;
  public static final int LBL_ITEM_SIZE_MAX_LENGTH = 6;
  public static final int LBL_VENDOR_STOCK_NUMBER_MAX_LENGTH = 12;
  public static final int STORE_NUMBER_MAX_LENGTH = 5;
  public static final String IS_ATLAS_DA_ITEM_CONVERSION_ENABLED =
      "is.atlas.da.item.conversion.enabled";
  public static final String IS_ITEM_CONFIG_ENABLED_ON_ATLAS_DA_ITEMS =
      "is.item.config.enabled.on.atlas.da.items";
  public static final String IS_ATLAS_AUTOMATION_RECEIVING_ENABLED =
      "is.atlas.automation.receiving.enabled";
  public static final String IS_HAWKEYE_INTEGRATION_ENABLED_FOR_MANUAL_RECEIVING =
      "is.hawkeye.integration.enabled.for.manual.receiving";
  public static final String IS_SORTER_ENABLED_ON_KAFKA = "is.sorter.enabled.on.kafka";
  public static final String SORTER_CONTRACT_VERSION = "sorter.contract.version";
  public static final String IS_DCFIN_ENABLED_ON_KAFKA = "is.dcfin.enabled.on.kafka";
  public static final String IS_SORTER_ENABLED_ON_JMS = "is.sorter.enabled.on.jms";
  public static final String IS_CUTOFF_ENABLED_FOR_ATLAS_DA_DELIVERIES =
      "is.cutoff.enabled.for.atlas.da.deliveries";
  public static final String IS_EI_INTEGRATION_ENABLED = "is.ei.integration.enabled";
  public static final String IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_EI_EVENTS =
      "is.inventory.update.listener.enabled.for.ei.events";
  public static final String IS_INVENTORY_UPDATE_LISTENER_ENABLED_FOR_DIVERT_INFO =
      "is.inventory.update.listener.enabled.for.divert.info";
  public static final String SYM_ROUTING_LABEL = "RL";
  public static final String IS_SMART_SLOTTING_ENABLED_ON_DA_ITEMS =
      "is.smart.slotting.enabled.on.da.items";
  public static final String DSDC_ASN_NOT_FOUND_IN_RDS = "ASN information was not found";

  public static final int DESTINATION_DC_NUMBER_START_INDEX = 0;
  public static final int PRE_LABEL_FREIGHT_DESTINATION_DC_NUMBER_END_INDEX = 4;
  public static final int PRE_LABEL_FREIGHT_SOURCE_DC_NUMBER_START_INDEX = 6;
  public static final int PRE_LABEL_FREIGHT_SOURCE_DC_NUMBER_END_INDEX = 10;
  public static final int RECEIVED_RDC_LABEL_DESTINATION_DC_NUMBER_END_INDEX = 5;
  public static final int RECEIVED_RDC_LABEL_SOURCE_DC_NUMBER_START_INDEX = 7;
  public static final int RECEIVED_RDC_LABEL_SOURCE_DC_NUMBER_END_INDEX = 12;
  public static final int PRE_LABEL_FREIGHT_LENGTH_16 = 16;
  public static final int RECEIVED_RDC_LABEL_LENGTH_18 = 18;
  public static final int RECEIVED_RDC_LABEL_LENGTH_25 = 25;

  public static final Map<String, RdcInstructionType> EXCEPTION_INSTRUCTION_TYPE_MAP;

  static {
    Map<String, RdcInstructionType> lpnBlockedExceptionsMap = new HashMap<>();
    lpnBlockedExceptionsMap.put("DSDC_AUDIT_LABEL", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("X_BLOCK", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("INVALID_REQUEST", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("NO_DATA_ASSOCIATED", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("SYSTEM_ERROR", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("SSTK_ATLAS_ITEM", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("SSTK", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("NONCON", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("RCV_ERROR", RdcInstructionType.LPN_BLOCKED);
    lpnBlockedExceptionsMap.put("LABEL_VALIDATED", RdcInstructionType.LABEL_VALIDATED);
    lpnBlockedExceptionsMap.put("MATCH_FOUND", RdcInstructionType.LABEL_VALIDATED);
    lpnBlockedExceptionsMap.put("ERROR_LPN_NOT_FOUND", RdcInstructionType.LABEL_VALIDATED);
    lpnBlockedExceptionsMap.put(
        "EXCEPTION_LPN_NOT_FOUND", RdcInstructionType.EXCEPTION_LPN_NOT_FOUND);
    lpnBlockedExceptionsMap.put("LPN_RECEIVED", RdcInstructionType.LPN_RECEIVED);
    lpnBlockedExceptionsMap.put("ERROR_LPN_BACKOUT", RdcInstructionType.ERROR_LPN_BACKOUT);
    lpnBlockedExceptionsMap.put("LPN_NOT_RECEIVED", RdcInstructionType.LPN_NOT_RECEIVED);
    lpnBlockedExceptionsMap.put("BREAKOUT", RdcInstructionType.BREAKOUT);
    lpnBlockedExceptionsMap.put("OVERAGE", RdcInstructionType.OVERAGE);
    lpnBlockedExceptionsMap.put("LPN_NOT_RECEIVED_SSTK", RdcInstructionType.LPN_NOT_RECEIVED_SSTK);
    lpnBlockedExceptionsMap.put("LPN_RECEIVED_SSTK", RdcInstructionType.LPN_RECEIVED_SSTK);
    lpnBlockedExceptionsMap.put(
        "EXCEPTION_LPN_RECEIVED", RdcInstructionType.EXCEPTION_LPN_RECEIVED);

    EXCEPTION_INSTRUCTION_TYPE_MAP = Collections.unmodifiableMap(lpnBlockedExceptionsMap);
  }

  public static final int EXCEPTION_RECEIVING_CASE_QUANTITY = 1;

  public static final List<String> SYM_ELIGIBLE_CON_ITEM_HANDLING_CODES =
      Arrays.asList("I", "J", "C", "M", "X");

  public static final List<String> OFFLINE_LABEL_TYPE = Arrays.asList("XDK1", "XDK2");

  public static final String OFFLINE_SLOT = "999";
  public static final String SYMCP_SLOT = "SYMCP";
  public static final String SYM_MANUAL_HANDLING_CODE = "J";
  public static final String MCPIB_LOCATION = "MCPIB";
  public static final String AIB_LOCATION = "AIB";
  public static final int RDC_AUTO_RECEIVE_QTY = 1;
  public static final String SSTK_A0002_SLOT = "A0002";
  public static final String IS_ATLAS_SENDING_UNIQUE_LPN_TO_HAWKEYE_ENABLED =
      "is.atlas.sending.unique.lpn.to.hawkeye.enabled";
  public static final String IS_ATLAS_EXCEPTION_DATA_MATRIX_DISABLED =
      "is.atlas.exception.data.matrix.disabled";
  public static final String IS_HAWKEYE_LOCATION_DETAILS_ENABLED =
      "is.hawkeye.location.details.enabled";
  public static final String HAZMAT_CODE_H = "H";
  public static final String SYM_LABEL_DATE_FORMAT = "MM/dd/yyyy";
  public static final String EXCEPTION_HANDLING = "EXCEPTION_HANDLING";

  public static final String MANUAL = "manual";
  public static final String DA_BREAK_PACK_NON_CON_ITEM_HANDLING_CODE = "BN";
  public static final String REPRINT_LABEL_TIMESTAMP_PATTERN = "MM/dd/yy HH:mm:ss";
  public static final String IS_AUTOMATION_DELIVERY_FILTER_ENABLED =
      "is.automation.delivery.filter.enabled";
  public static final String IS_ATLAS_DA_PILOT_DELIVERY_ENABLED =
      "is.atlas.da.pilot.delivery.enabled";

  public static final List<String> ATLAS_DA_NON_CON_HANDLING_CODES = Arrays.asList("L", "N", "V");
  public static final String DA_BREAK_PACK_CONVEYABLE_ITEM_HANDLING_CODE = "BC";
  public static final String IS_DA_SLOTTING_BLOCKED_FOR_ATLAS_CONVEYABLE_ITEMS =
      "is.da.slotting.blocked.for.atlas.conveyable.items";
  public static final String IS_DA_AUTOMATION_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM =
      "is.da.automation.slotting.enabled.for.atlas.conveyable.item";
  public static final String IS_DA_CONVENTIONAL_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEM =
      "is.da.conventional.slotting.enabled.for.atlas.conveyable.item";
  public static final String IS_COMPLETE_DELIVERY_FOR_LAST_AUDIT_TAG_ENABLED =
      "is.complete.delivery.for.last.audit.tag.enabled";
  public static final String IS_UPDATE_POSSIBLE_UPC_ENABLED = "is.update.possible.upc.enabled";
  public static final String RDC_ITEM_UPDATE_PROCESSOR = "rdcItemUpdateProcessor";
  public static final Map<String, RejectReason> HANDLING_CODE_REJECT_REASON_MAP;

  static {
    Map<String, RejectReason> handlingCodeRejectReasonMap = new HashMap<>();
    handlingCodeRejectReasonMap.put("N", RejectReason.RDC_NONCON);
    handlingCodeRejectReasonMap.put("E", RejectReason.RDC_NONCON);
    handlingCodeRejectReasonMap.put("X", RejectReason.X_BLOCK);
    handlingCodeRejectReasonMap.put("R", RejectReason.X_BLOCK);
    HANDLING_CODE_REJECT_REASON_MAP = Collections.unmodifiableMap(handlingCodeRejectReasonMap);
  }

  public static final int ITEM_UPDATE_HAWKEYE_DELIVERIES_DAY_LIMIT = 14;
  public static final String EMPTY_CATALOG_GTIN = "00000000000000";

  public static final String CHILD_CONTAINER_LIMIT_FOR_INVENTORY =
      "child.container.limit.for.inventory";

  public static final String MAX_ALLOWED_HOURS_FOR_CONTAINER_BACKOUT =
      "max.allowed.hours.for.container.backout";
  public static final String IS_CHILD_CONTAINER_SPLIT_ENABLED = "is.child.container.split.enabled";
  public static final Integer DEFAULT_CHILD_CONTAINER_LIMIT_FOR_INVENTORY = 15;

  public static final String IS_LEGACY_RCVD_QTY_CHECK_ENABLED_FOR_DA_ATLAS_ITEMS =
      "is.legacy.rcvd.qty.check.enabled.for.da.atlas.items";
  public static final String IS_CHILD_CONTAINER_SPLIT_ENABLED_FOR_DSDC =
      "is.child.container.split.enabled.for.dsdc";
  public static final String IS_ATLAS_DA_LABEL_COUNT_VALIDATION_ENABLED =
      "is.atlas.da.label.count.validation.enabled";

  public static final String IS_VOID_LABELS_FOR_CASE_PACK_SYM_INELIGIBLE_ENABLED =
      "is.void.labels.for.case.pack.sym.ineligible.enabled";

  public static final String INVALID_LABEL_FORMAT = "Invalid Label Format";
  public static final String INVALID_CONTAINER_INFO =
      "Invalid label format found for itemNumber %s for the given handling code";
  public static final String DA_CASE_PACK_NON_CON_ITEM_HANDLING_CODE = "CN";
  public static final String IS_LABEL_FORMAT_VALIDATION_ENABLED =
      "is.label.format.validation.enabled";
  public static final String IS_DA_ATLAS_NON_SUPPORTED_HANDLING_CODES_BLOCKED =
      "is.da.atlas.non.supported.handling.codes.blocked";
  public static final String STOCK_TYPE_CONVEYABLE = "C";
  public static final String SLOT_TYPE_AUTOMATION = "automation";
  public static final String SLOT_TYPE_CONVENTIONAL = "conventional";
  public static final String CONTAINER_TAG_DA_CON_AUTOMATION_PALLET_SLOTTING =
      "DA_CON_AUTOMATION_PALLET_SLOTTING";
  public static final String ATLAS_DA_NON_CONVEYABLE_HANDLING_CODE = "N";

  public static final String IS_DUPLICATE_SSCC_BLOCKED_FOR_MULTIPLE_DELIVERIES =
      "is.duplicate.sscc.blocked.for.multiple.deliveries";
  public static final String ORIGIN_FACILITY_NUM = "originFacilityNum";
  public static final String MABD_DATE_FORMAT_FOR_SSTK_LABEL_SEQUENCE_NBR = "yyyyMMdd";
  public static final String POL_FORMAT_SPECIFIER_FOR_SSTK_LABEL_SEQUENCE_NBR = "%4s";
  public static final String IS_DA_SLOTTING_ENABLED_FOR_ATLAS_CONVEYABLE_ITEMS =
      "is.da.slotting.enabled.for.atlas.conveyable.items";
  public static final String LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_ORDERED_SSTK = "2";
  public static final String LABEL_SEQUENCE_NUMBER_STARTING_DIGIT_OVERAGE_SSTK = "4";
  public static final String IS_SSTK_DCFIN_ENABLED_ON_KAFKA = "is.sstk.dcfin.enabled.on.kafka";
  public static final String SSTK_LABEL_STARTING_SERIAL_NBR = "00001";

  public static final String PRE_LABEL_GENERATION_SCHEDULER_ERROR =
      "Something went wrong while generating labels in the scheduler and the stack trace is {}";

  public static final String IS_PRE_LABEL_GENERATION_SCHEDULER_ENABLED =
      "is.pre.label.generation.scheduler.enabled";
  public static final String CONVEY_PICKS_HANDLING_CODE = "M";
  public static final String DA_BREAK_PACK_NON_CONVEYABLE_ITEM_HANDLING_CODE = "BN";

  public static final String DSDC_ACTIVITY_NAME = "DSDC";
  public static final String DA_CASE_PACK_PALLET_RECEIVING_ITEM_HANDLING_CODE = "CL";

  public static final String DA_CASE_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE = "CV";
  public static final String IS_ATLAS_ITEM_CONVERSION_ENABLED = "is.atlas.item.conversion.enabled";
  public static final String IS_ATLAS_SSTK_PILOT_DELIVERY_ENABLED =
      "is.atlas.sstk.pilot.delivery.enabled";

  public static final String CANCEL_AUDIT_PACK_SUCCESS_MESSAGE =
      "Successfully cancelled audit pack: %s";

  public static final List<String> DA_NON_CON_SLOTTING_HANDLING_CODES =
      Arrays.asList("CV", "CN", "BV", "BN");
  public static final String DA_BREAK_PACK_NON_CON_VOICE_PICK_ITEM_HANDLING_CODE = "BV";

  public static final List<String> DA_BREAK_PACK_NON_CONVEYABLE_ITEM_HANDLING_CODES =
      Arrays.asList("BV", "BN");
  public static final String IS_ATLAS_PARITY_EXCEPTION_RECEIVING_ENABLED =
      "is.atlas.parity.exception.receiving.enabled";

  public static final String IS_PUBLISH_KAFKA_TOPIC_RESTRICT_VALIDATION_ENABLED =
      "is.publish.kafka.topic.restrict.validation.enabled";

  public static final String IS_ATLAS_PARITY_MCPIB_DOCKTAG_PUBLISH_ENABLED =
      "is.atlas.parity.mcpib.docktag.publish.enabled";
}
