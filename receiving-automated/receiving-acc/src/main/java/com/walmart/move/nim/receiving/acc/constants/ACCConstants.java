package com.walmart.move.nim.receiving.acc.constants;

public final class ACCConstants {

  public static final String ENABLE_DUPLICATE_LPN_VERIFICATION = "enableDuplicateLpnVerification";

  private ACCConstants() {}

  public static final String ACC_DELIVERY_EVENT_PROCESSOR = "accDeliveryEventProcessor";
  public static final String QUEUE_ACL_NOTIFICATION = "QUEUE.RCV.ACL.NOTIFICATION";
  public static final String QUEUE_ACL_VERIFICATION = "QUEUE.RCV.ACL.VERIFICATION";
  public static final String ACL_SCAN_DOOR_MESSAGE_TOPIC = "TOPIC/RECEIVE/ACL/SCANDOOR";
  public static final String ACL_LABEL_DATA_TOPIC = "TOPIC/RECEIVE/ACL/LABELDATA";
  public static final String LPN_CACHE_SERVICE = "lpnCacheService";
  public static final String ACC_VERIFICATION_PROCESSOR = "accVerificationProcessor";
  public static final String ACC_NOTIFICATION_PROCESSOR = "accNotificationProcessor";
  public static final String USER_NOT_FOUND_MSG = "No user found at door %s.";
  public static final String RETRYABLE_FDE_SERVICE = "retryableFdeService";
  public static final String ACL_REJECT_ERROR_CODE = "GLS-RCV-BE-0001";
  public static final String GENERIC_LABEL_GEN_SERVICE = "genericLabelGeneratorService";
  public static final String ACC_INSTRUCTION_SERVICE = "accInstructionService";
  public static final String ACL_DELIVERY_LINK_SERVICE = "aclDeliveryLinkService";
  public static final String HAWK_EYE_DELIVERY_LINK_SERVICE = "hawkEyeDeliveryLinkService";
  public static final String HAWK_EYE_SERVICE = "hawkEyeService";
  public static final String RCV_EXCEPTION_LABEL_URI =
      "/label-gen/deliveries/{deliveryNumber}/upcs/{upcNumber}/exceptionLabels";
  public static final String DEFAULT_SECURITY_ID = "1";
  public static final String ACL_DOOR = "ACL-DOOR";
  public static final String FLR_LINE = "ACL-FLR-LINE";

  public static final String PO_POL_METADATA_NOT_FOUND_ERROR_MSG =
      "Unable to get deliveryMetaData for deliveryNumber= %s and lpn=%s in Receiving";
  public static final String PLG_SCHEDULER_ERROR =
      "Something went wrong while generating labels in the scheduler and the stack trace is {}";
  public static final String ACC_DOCK_TAG_SERVICE = "accDockTagService";
  public static final String ACC_REPORT_SERVICE = "accReportService";

  public static final String VENDOR_UPC_UPDATE_TO_ACL = "/label/item/update";
  public static final String ACL_CATALOG_BAD_REQUEST =
      "Invalid request for upc update, delivery = %s, item = %s";
  public static final String ACL_SERVICE_DOWN = "ACL service is down";
  public static final String SYSTEM_BUSY =
      "System is currently busy processing requests. Please try after some time";
  public static final String READINESS = "/readiness/";
  public static final String HAWK_EYE_DELIVERY_LINK = "/api/v1/hawkeye/acl/delivery-link";
  public static final String ACC_ASYNC_LABEL_PERSISTER_SERVICE = "asyncLabelPersisterService";
  public static final int MAX_DOCKTAGS_ALLOWED_TO_COMPLETE = 15;
  public static final String ACL_PAYLOAD_TYPE_HEADER_NAME = "payloadType";
  public static final String HAWK_EYE_DELIVERY_TYPE_HEADER_NAME = "deliveryType";
  public static final String HAWK_EYE_LABEL_DATA_EVENT_TYPE_HEADER_VALUE = "LABEL_DATA";
  public static final String HAWK_EYE_SORTER_EVENT_TYPE_HEADER_VALUE = "LPN_ALLOCATION";
  public static final String KAFKA_SORTER_PUBLISHER = "kafkaSorterPublisher";
  public static final String ACL_DELTA_PUBLISH_COMPLETE_DELIVERY = "DELIVERY_FULL";
  public static final String ACL_DELTA_PUBLISH_PARTIAL_DELIVERY = "DELIVERY_PARTIAL";
  public static final String ACC_FACILITY_MDM_SERVICE = "facilityMDMService";
  public static final String EQUIPMENT_TYPE = "equipmentType";
  public static final String ACL = "ACL";
  public static final String DOOR_LINE = "DOOR_LINE";
  public static final String FLOOR_LINE = "FLOOR_LINE";

  public static final String PREGEN_FALLBACK_ENABLED = "pregenFallbackEnabled";
  public static final String ENABLE_LABEL_DATA_COMPRESSION = "enableLabelDataCompression";
  public static final String ENABLE_REPUBLISH_FALLBACK_CHECK = "enableRepublishFallbackCheck";
  public static final String ENABLE_DELIVERY_LINE_LEVEL_FBQ_CHECK =
      "enableDeliveryLineLevelFBQCheck";
  public static final String ENABLE_FILTER_CANCELLED_PO_FOR_ACL = "enableFilterCancelledPoForAcl";

  public static final String EXCEPTION_LPN_THRESHOLD = "exceptionLPNThreshold";
  public static final String DELIVERY_LINK = "DELIVERY_LINK";
  public static final String DELIVERY_LINK_VERSION = "1";
  public static final String LABEL_DATA_VERSION = "1";

  public static final String UPC_CATALOG_GLOBAL = "UPC_CATALOG_GLOBAL";

  // Multi manifest related constants
  public static final String ENABLE_MULTI_MANIFEST = "enableMultiManifest";
  public static final String MAX_DELIVERY_MMR_IDLE_DURATION_IN_HOUR =
      "maxDeliveryMMRIdleDurationInHour";
  public static final String ENABLE_MULTI_DELIVERY_LINK = "enableMultiDeliveryLink";
  public static final int MAX_ITEMS_IN_MMR_ERROR = 3;

  public static final String ACC_COMPLETE_DELIVERY_PROCESSOR = "accCompleteDeliveryProcessor";
  public static final String LPN_SWAP_PROCESSOR = "lpnSwapProcessor";
  public static final String DEFAULT_LPN_SWAP_PROCESSOR = "defaultLpnSwapProcessor";
  public static final String HAWKEYE_LPN_SWAP_PROCESSOR = "hawkeyeLpnSwapProcessor";

  public static final String ENABLE_PARENT_ACL_LOCATION_CHECK = "isParentAclLocationCheckEnabled";
  public static final String ENABLE_FALLBACK_PO_SEARCH_LPN_RECEIVING =
      "enableFallbackPOSearchInLPNReceiving";
  public static final String ENABLE_INVALID_ALLOCATIONS_EXCEPTION_CONTAINER_PUBLISH =
      "enableInvalidAllocationsExceptionContainerPublish";
  public static final String ENABLE_GENERIC_FDE_ERROR_EXCEPTION_CONTAINER_PUBLISH =
      "enableGenericFdeErrorExceptionContainerPublish";

  // item update constants
  public static final String ACC_ITEM_UPDATE_PROCESSOR = "accItemUpdateProcessor";
  public static final String ACC_ITEM_UPDATE_SERVICE = "accItemUpdateService";
  public static final String CROSSU = "CROSSU";
  public static final String SSTKU = "SSTKU";

  // robo depal constants
  public static final String ROBO_DEPAL_EVENT_PROCESSOR = "roboDepalEventProcessor";
  public static final String ROBO_DEPAL_EVENT_SERVICE = "roboDepalEventService";
  public static final String ROBO_DEPAL_ACK_EVENT = "DEPAL_ACK";
  public static final String ROBO_DEPAL_FINISH_EVENT = "DEPAL_FINISH";

  public static final String ENABLE_POSSIBLE_UPC_BASED_ITEM_COLLISION_MMR =
      "enablePossibleUpcBasedItemCollisionMMR";
  public static final String NO_COMMON_ITEMS = "";

  public static final String LABEL_DATA_LPN_SERVICE = "labelDataLpnService";

  public static final String OVERFLOW_LPN_RECEIVING_SERVICE = "overflowLpnReceivingService";
  public static final String AIR_OVF = "AIR-OVF";
}
