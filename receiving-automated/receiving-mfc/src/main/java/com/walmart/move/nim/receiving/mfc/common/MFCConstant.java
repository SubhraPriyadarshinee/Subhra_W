package com.walmart.move.nim.receiving.mfc.common;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

public interface MFCConstant extends ReceivingConstants {

  // Flags
  String ENABLE_DECANT_RECEIPT = "enable.decant.receipt";
  String ENABLE_STORE_PALLET_OSDR = "enable.store.pallet.osdr";
  String ENABLE_STORE_PALLET_PUBLISH = "enable.store.pallet.publish";
  String ENABLE_SKIP_OVERAGE_STORE_PALLET_PUBLISH = "skip.overage.store.pallet.publish";
  String ENABLE_INVENTORY_CONTAINER_REMOVAL = "enable.inventory.container.removal";

  // Constants
  String MFC_DELIVERY_SERVICE = "mfcDeliveryService";
  String UOM_CF = "CF";
  String MFC_SHIPMENT_ID = "shipment_document_id";
  String UOM_LB = "LB";
  String WEIGHT = "weight";
  String CUBE = "cube";
  String DUMMY_INVOICE_SUFFIX = "999999999999";
  String DUMMY_PURCHASE_REF_NUMBER = "999999999";
  String MFC_DELIVERY_METADATA_SERVICE = "mfcDeliveryMetaDataService";
  String AUTO_MFC_PROCESSOR = "autoMFCProcessor";
  String MANUAL_MFC_PROCESSOR = "manualMFCProcessor";
  String MFC_INVENTORY_ADJUSTMENT_PROCESSOR = "mfcInventoryAdjustmentProcessor";
  String MFC_HAWKEYE_DECANT_ADJUSTMENT_LISTENER = "mfcHawkeyeDecantAdjustmentListener";
  String HAWKEYE_RECEIPT_TRANSFORMER = "hawkeyeReceiptTransformer";
  String INVENTORY_RECEIPT_TRANSFORMER = "inventoryReceiptTransformer";
  String NGR_SHIPMENT_TRANSFORMER = "ngrShipmentTransformer";
  String KAFKA_SHIPMENT_ARRIVAL_PUBLISHER = "KAFKA_SHIPMENT_ARRIVAL_PUBLISHER";
  String KAFKA_MANUAL_FINALIZATION_PUBLISHER = "KAFKA_MANUAL_FINALIZATION_PUBLISHER";

  String INVENTORY_ADJUSTMENT_HELPER = "inventoryAdjustmentHelper";
  String DAMAGE_INVENTORY_REPORT_LISTENER = "damageInventoryReportListener";
  String TRAILER_OPEN_INSTRUCTION_REQUEST_HANDLER = "trailerOpenInstructionRequestHandler";

  String STORE = "STORE";
  String MFC = "MFC";
  String MARKET_FULFILLMENT_CENTER = "MARKET_FULFILLMENT_CENTER";

  String RECEIVED = "received";
  String OVERAGE = "overage";
  String SHORTAGE = "shortage";
  String DAMAGE = "damage";
  String REJECT = "reject";
  String IN = "-in-";
  String MULTIPLE = "multiple";
  String NA = "NA";
  String YET_TO_PUBLISH = "YET_TO_PUBLISH";
  String MANUAL_MFC = "manualMFC";
  String AUTO_MFC = "autoMFC";
  String OPERATION_TYPE = "operationType";
  String PALLET_TYPE = "palletType";
  String INCLUDE_STORE_PALLETS = "storePallets";
  String RECEIVED_OVG_TYPE = "RECEIVED_OVG_TYPE";
  String REPLENISHMENT_CODE = "REPLENISHMENT_CODE";
  String UPDATE_REPLEN_CODE_EVENT = "UPDATE_REPLEN_CODE";
  String EVENT_TYPE = "eventType";
  String UOM_EA = "EA";
  String PACK_NUMBER = "packNumber";
  String ITEM_DESC = "itemDescription";
  String DELIVERY_COMPLETE_FLOW_EXECUTOR = "deliveryCompleteFlowExecutor";
  String MIXED_PALLET_PROCESSOR = "storeInboundMixedPalletProcessor";
  String UNDERSCORE = "_";
  String MIXED_PALLET_REJECT = "mixed_pallet_reject";
  String FLOW_TYPE = "flowType";
  String TIMEZONE_CODE = "timeZoneCode";
  String BANNER_CODE = "bannerCode";
  String BANNER_DESCRIPTION = "bannerDescription";
  String ORIGINAL_DELIVERY_NUMBER = "originalDeliveryNumber";
  String DEST_TRACKING_ID = "destTrackingId";
  String RESOLUTIONID = "resolutionId";
  String MFC_PROBLEM_SERVICE = "mfcProblemService";
  String PROBLEM_REGISTRATION_SERVICE = "problemRegistrationService";
  String FIXIT_PROBLEM_SERVICE = "fixitProblemService";
  String LOAD_QUALITY_PROBLEM_SERVICE = "loadQualityProblemService";
  String CONTAINER_DTO_EVENT_TRANSFORMER = "containerDTOEventTransformer";
  String CONTAINER_EVENT_TRANSFORMER = "containerEventTransformer";
  String STORE_DELIVERY_UNLOADING_PROCESSOR = "storeDeliveryUnloadingProcessor";
  String STORE_DELIVERY_UNLOADING_PROCESSOR_V2 = "storeDeliveryUnloadingProcessorV2";
  String STORE_CONTAINER_SHORTAGE_PROCESSOR = "storeContainerShortageProcessor";
  String STORE_CONTAINER_OVERAGE_EVENT_PROCESSOR = "storeContainerOverageEventProcessor";
  String SHORTAGE_PROCESSING_IN_ASYNC_MODE = "async.container.shortage.processing";
  String DELIVERY_UNLOAD_PROCESSING_IN_ASYNC_MODE = "async.delivery.unload.processing";
  String STORE_INBOUND_CONTAINER_CREATION_V2 = "v2StoreInboundContainerCreate";
  String STORE_INBOUND_CONTAINER_CREATION_V3 = "v3StoreInboundContainerCreate";

  String VERSION_1_0_0 = "1.0.0";
  String ORIGINATOR_ID = "originatorId";

  String CONTAINER_FILTER_TYPE = "containerFilterType";
  String CONTAINER_FILTER_TYPE_CASE = "containerFilterTypeCase";
  String CONTAINER_FILTER_TYPE_MFC_PALLET = "containerFilterTypeMFCPallet";
  String CORRECTION_CONTAINER_EVENT_PROCESSOR = "CorrectionContainerEventProcessor";
  String EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES = "EventRunAfterThresholdTimeMinutes";

  String ACTION_TYPE = "actionType";
  String CREATE_CORRECTION_EVENT = "createCorrectionEvent";
  String UPDATE_CORRECTION_EVENT = "updateCorrectionEvent";

  String STORE_PALLET_INCLUDED = "includeStorePallet";
  String MFC_PALLET_INCLUDED = "includeMFCPallet";

  String DELIVERY_STATUS_NOT_SUPPORTED = "Delivery Status %s not supported.";
  String ORIGIN_DC_NUM = "originDcNbr";
  String ORIGIN_COUNTRY_CODE = "originCountryCode";
  String REPLENISHMENT_GROUP_NUMBER = "replenishmentGroupNbr";
  String REPLENISHMENT_SUB_TYPE_CODE = "replenishSubTypeCode";
  String CID = "cid";
  String SHIPPER = "SHIPPER";
  String DEPT_CATEGORY_NBR = "deptCategoryNbr";
  String DEPT_SUB_CATEGORY_NBR = "deptSubcatgNbr";

  String NGR_FINALIZATION_EVENT_LISTENER = "ngrFinalizationEventListener";
  String NGR_FINALIZATION_EVENT_PROCESSOR = "ngrFinalizationEventProcessor";
  String DSD = "DSD";
  String SOURCE_TYPE = "sourceType";
  String CHANNEL_TYPE = "channelType";
  String RECEIVING_COMPLETE_TS = "receivingCompleteTs";
  String ARRIVAL_TS = "arrivalTs";
  String COST_CURRENCY = "COST_CURRENCY";
  String POST_DSD_CONTAINER_CREATE_PROCESSOR = "dsdCtnrCreatePostProcessor";
  String DSD_CONTAINER_CREATE_POST_PROCESSOR_IN_ASYNC_MODE =
      "async.dsd.container.create.post.processor";

  String NGR_ASN_TRANSFORMER = "ngrAsnTransformer";
  String REJECTED = "REJECTED";
  String PENDING = "PENDING";
  String UNKNOWN = "UNKNOWN";
  String REASON_DESC_NOT_MFC = "Not MFC assortment";
  String ATLAS_INVENTORY = "ATLAS_INVENTORY";
  String EVENT_DECANTING = "DECANTING";
}
