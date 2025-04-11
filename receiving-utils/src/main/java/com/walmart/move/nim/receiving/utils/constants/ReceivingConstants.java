/** */
package com.walmart.move.nim.receiving.utils.constants;

import java.util.*;

/**
 * This class will have all the String constants literals
 *
 * @author sitakant
 */
public interface ReceivingConstants {

  String OFFLINE_DEFAULT_DOOR_LOCATION = "999";
  int MAX_RANDOM_INT_PRINT_JOB_ID = 9000000;
  int INT_BASE_VALUE = 1000000;
  String CHANNEL_METHOD = "channelMethod";
  String MESSAGE_NUMBER = "messageNumber";
  String PALLET_ID = "palletId";
  String REPACK = "REPACK";
  String SM_URI_AUTHENTICATE = "/authenticate";
  String SM_URI_CAPABILITIES = "/capabilities";
  String SM_BASE_URL_KEY = "smBaseUrl";
  String SM_BASE_URL = "https://nimservices.s%s.us.wal-mart.com:7100/securityMgmt/us/%s";

  String IS_KOTLIN_CLIENT = "isKotlin";
  String REQUEST_ORIGINATOR = "requestOriginator";
  String IS_IGNORE_SCT_HEADER_ADDED = "isIgnoreSCTHeaderAdded";
  String FLOW_DESCRIPTOR = "flowDescriptor";
  String FLOW_RECEIVE_INTO_OSS = "ReceiveIntoOss";
  String HOST_IP = "HOST_IP";
  String APP_NAME_KEY = "APP_NAME";
  String APP_NAME_VALUE = "receiving-api";
  String IGNORE_SCT = "ignoreSct";
  String IGNORE_RAP = "ignoreRap";
  String IGNORE_WFS = "ignoreWfs";
  String SOURCE_APP_NAME_WITRON = "witron";
  String TENENT_COUNTRY_CODE = "facilityCountryCode";
  String TENENT_FACLITYNUM = "facilityNum";
  String FACILITY_NAME = "facilityName";
  String FACILITY_TYPES = "facilityTypes";
  String TENENT_FACILITYNUMBER = "facilityNumber";
  String TENENT_FACILITY_COUNTRY_CODE = "countryCode";
  String INVALID_TENENT_INFO = "Invalid tenant headers";
  String CONTENT_TYPE = "Content-Type";
  String PRODUCT_NAME_HEADER_KEY = "WMT-Product-Name";
  String SECURITY_HEADER_KEY = "WMT-Security-ID";
  String SECURITY_TOKEN = "WMT-Token";
  String USER_ID_HEADER_KEY = "WMT-UserId";
  String MESSAGE_ID_HEADER_KEY = "messageId";
  String CORRELATION_ID_HEADER_KEY = "WMT-CorrelationId";
  String CORRELATION_ID_HEADER_KEY_FALLBACK = "WMT_CorrelationId";
  String CLIENT_REQUEST_TIME = "ClientRequestTime";
  String DCFIN_WMT_API_KEY = "WMT-API-KEY";
  String HEADER_AUTH_KEY = "api-key";
  String WMT_PRODUCT_NAME = "WMT-Product-Name";
  String ACCEPT = "Accept";
  String WM_CORRELATIONID = "WM-CorrelationID";
  String WM_USERID = "WM-UserID";
  String WM_SITEID = "WM-SiteID";
  String WM_CLIENT = "WM-Client";
  String WM_WORKFLOW = "WM-Workflow";
  String HOST = "Host";
  String SECURITY_ID = "securityId";
  String TOKEN = "token";
  String LOCAL = "local";
  String COMPONENT_ID = "componentId";
  String EVENT_TYPE = "eventType";
  String EVENT_SUB_TYPE = "eventSubType";
  String VERSION = "version";
  String MESSAGE_TYPE = "messageType";
  String SYMBOTIC_SYSTEM = "symSystem";

  String ATLAS_RECEIVING = "AtlasRCV";
  String NGR_RECEIVING = "NGR";
  String WMT_REQ_SOURCE = "WMT-Req-Source";
  String NGR_TENANT_MAPPING_ENABLED = "isNGRTenantMappingEnabled";

  String WM_CONSUMER_ID = "WM_CONSUMER.ID";
  String WM_SVC_NAME = "WM_SVC.NAME";
  String WM_SVC_ENV = "WM_SVC.ENV";
  String WM_SVC_VERSION = "WM_SVC.VERSION";
  String APPLICATION_GRAPHQL = "application/graphql";
  String APPLICATION_JSON = "application/json";
  String WMT_TENANT = "WMT-Tenant";
  String WMT_SOURCE = "WMT-Source";
  String WMT_CHANNEL = "WMT-Channel";
  String WMT_INCIDENT_DOMAIN = "WMT-IncidentDomain";
  String WEB = "WEB";
  String GDM = "GDM";
  String TYPE_28 = "28";
  String AUTOMATION = "AUTOMATION";
  String WMT_SELLER_TYPE = "WMT-SellerType";
  String WMT_EXCEPTION_CATEGORY = "WMT-ExceptionCategory";
  String WMT_EXCEPTION_TYPE = "WMT-ExceptionType";

  // JMS library not accepting key's containing hyphen '-'. So, using underscore
  // '_'
  String JMS_CONTENT_TYPE_KEY = "content_type";
  String JMS_USER_ID = "WMT_UserId";
  String JMS_CORRELATION_ID = "WMT_CorrelationId";
  String JMS_QUEUE_PREFIX = "QUEUE";
  String JMS_EVENT_TYPE = "eventType";
  String JMS_MESSAGE_ID = "JMSMessageID";
  String CORRELATION_ID = "correlationId";
  String JMS_REQUESTOR_ID = "requestorId";
  String JMS_MESSAGE_TS = "msgTimestamp";
  String JMS_MESSAGE_VERSION = "version";
  String TOTAL_MESSAGE_COUNT = "totalMessageCount";

  // JPA constants
  String PRIMARY_PERSISTENCE_UNIT = "primaryPersistenceUnit";

  String STATUS_PENDING_BACKOUT = "PENDING_BACKOUT";
  String STATUS_BACKOUT = "backout";
  String STATUS_COMPLETE = "COMPLETE";
  String STATUS_PICKED = "PICKED";
  String STATUS_PUTAWAY_COMPLETE = "PUTAWAY_COMPLETE";
  String STATUS_COMPLETE_NO_ASN = "COMPLETE_NO_ASN";
  String STATUS_ACTIVE = "ACTIVE";
  String STATUS_ACTIVE_NO_ASN = "ACTIVE_NO_ASN";
  String STATUS_PENDING_COMPLETE = "PENDING_COMPLETE";

  // Constants for DeliveryService
  String GDM_V3_DOCUMENT_SEARCH_BY_POTYPE = "/api/deliveries/search";
  String GDM_V3_RECEIPT_SEARCH_BY_PO = "/api/deliveries/purchase-orders";
  String GDM_REQUEST_UPDATE_ITEM_VERIFIED_ON = "/api/items/{itemNumber}";
  String GDM_RE_OPEN_DELIVERY_URI = "/api/v1/deliveries/re-open";
  String GDM_OPEN_TO_WORKING_URI = "/api/v1/deliveries/receiving-started";
  String GDM_DOOR_OPEN_URI = "/api/v1/deliveries/door-open";
  String GDM_DOCUMENT_SEARCH_URI =
      "/document/v2/deliveries/{deliveryNumber}/deliveryDocuments/itemupcs/{upcNumber}?includeActiveChannelMethods=true&includeCrossReferences=true";
  String GDM_DOCUMENT_SEARCH_URI_V3 = "/api/deliveries/{deliveryNumber}/item-scan";
  String GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE = "application/vnd.ItemScanResponse2+json";
  String GDM_GET_DELIVERY_URI = "/document/v2/deliveries/{deliveryNumber}";

  String GDM_GET_LPN_DETAILS_URI = "/api/lpn/{lpnNumber}";

  String GDM_GET_DELIVERY_V2_URI_INCLUDE_DUMMYPO =
      "/document/v2/deliveries/{deliveryNumber}?includeDummyPO=true";
  String GDM_ASN_DOCUMENT_SEARCH = "/v1/document/containers/{asnBarcode}";
  String GDM_DELIVERY_HEADERS_DETAILS_SEARCH = "/api/v1/deliveries/delivery-header-details";
  String GDM_UPDATE_UPC = "/api/v1/deliveries/{deliveryNumber}/items/{itemNumber}/upc-update";
  String GDM_VENDOR_UPC_UPDATE_ITEM_V3 = "/api/items/{itemNumber}";
  String GDM_SHIPMENT_DETAILS_URI = "/api/deliveries/{deliveryNumber}/shipments/{identifier}";
  String GDM_SHIPMENT_DETAILS_WITH_ASN_URI =
      "/api/deliveries/{deliveryNumber}/shipments/{identifier}?documentType=ASN";
  String GDM_SHIPMENT_DETAILS_WITH_UNIT_SERIAL_INFO_URI = "/api/shipment/packItems";
  String GDM_LINK_SHIPMENT_DELIVERY = "/api/deliveries/{deliveryNumber}/shipments/{shipmentNumber}";
  String GDM_SEARCH_SHIPMENT = "/api/shipments/{identifier}";
  String GDM_SEARCH_PACK = "/api/shipments/packs/{identifier}";
  String GDM_FETCH_DELIVERY_DOC_BY_GTIN_AND_LOT_URI = "/api/deliveries/{deliveryNumber}/shipments?";
  String GDM_FETCH_DELIVERY_DOC_BY_GTIN_AND_LOT_WITH_ASN_URI =
      "/api/deliveries/{deliveryNumber}/shipments?documentType=ASN&";
  String GDM_CURRENT_NODE_URI = "/v2/shipments/containers";
  String GDM_CURRENT_AND_SIBLINGS_URI = "/v2/shipments/containers/siblings";
  String GDM_UNIT_LEVEL_CONTAINERS_URI = "/v2/shipments/containers/leafContainers";
  String GDM_UPDATE_EPCIS_POSTING_STATUS_URI = "/api/shipments/v2/containers/updateStatus";

  String GDM_FETCH_SHIPMENT_BASE_URI = "/api/shipments?";
  String GDM_SEARCH_HEADER_URI = "/api/deliveries/search";
  String GDM_SHIPMENT_SEARCH_URI = "/api/shipments/search";
  String GDM_FETCH_PURCHASE_ORDER_INFO = "/api/purchase-orders/{poNumber}";
  String OMS_FETCH_PURCHASE_ORDER_INFO = "/SCHEDLR/OMSPubSubGenericRead2/{poNumber}?LegacyPO=Y";
  String SCHEDULER_APPEND_PO_TO_DELIVERY = "/ILP2/core-api/external/delivery/append";
  String DELIVERY_NUMBER = "deliveryNumber";
  String RECEIVER_USER_ID = "receiverUserId";
  String CONTAINER_NUMBER = "containerNumber";

  String LPN_NUMBER = "lpnNumber";
  String SHIPMENT_NUMBER = "shipmentNumber";
  String UPC_NUMBER = "upcNumber";
  String ITEM_NUMBER = "itemNumber";
  String QUERY_GTIN = "gtin";
  String SHIPMENT_IDENTIFIER = "identifier";
  String PO_NUMBER = "docNbr";
  String ASN = "ASN";
  String PURCHASE_REFERENCE_NUMBER = "po";
  String PO_LINE_NUMBER = "docLineNbr";
  String DELIVERY_SERVICE = "deliveryServiceImpl";
  String DELIVERY_SERVICE_KEY = "deliveryService";
  String PURCHASE_ORDER_NUMBER = "poNumber";
  String GDM_SERVICE_DOWN = "Error while calling GDM";
  String GDM_CATALOG_BAD_REQUEST = "Invalid request for upc update, delivery = %s, item = %s";
  String GDM_VENDOR_UPC_UPDATE_ITEM_BAD_REQUEST =
      "Invalid request for VendorUPC update for item number = %s";
  String GDM_CATALOG_NOT_FOUND = "Delivery %s not found";
  String GDM_VENDOR_UPC_UPDATE_ITEM_NOT_FOUND = "Item %s not found";
  String ITEM_CATALOG_ENABLED = "isItemCatalogEnabled";
  String ITEM_CATALOG_ENABLED_FOR_GDM = "gdm.item.catalog.enabled";
  String ITEM_VENDOR_UPC_UPDATE_ENABLED_FOR_GDM = "gdm.vendor.upc.item.update.v3.enabled";
  String ITEM_CATALOG_ENABLED_FOR_MDM = "mdm.item.catalog.enabled";
  String CATALOG_UPC_CONVERSION_ENABLED = "catalog.upc.conversion.enabled";
  String ENABLE_STORE_FINALIZATION_EVENT = "enable.store.finalization.event";
  String ENABLE_ON_SCAN_RECEIPT = "enable.on.scan.receipt";

  String INVALID_DELIVERY_NUMBER = "Invalid delivery number";
  String GDM_SHIPMENT_NOT_FOUND =
      "Scanned SSCC %s was not found on this delivery. Please quarantine this freight and submit a problem ticket.";
  String GDM_UNIT_SERIAL_INFO_NOT_FOUND =
      "Unit level serialized info not found in GDM. Please quarantine this freight and submit a problem ticket.";
  String GDM_SEARCH_SHIPMENT_FAILED = "Unable to search Shipment details.";
  String GDM_SEARCH_PACK_FAILED = "Unable to search pack %s details.";
  String GDM_SEARCH_PACK_NOT_FOUND =
      "%s was not found in shipment details. Please contact your supervisor.";
  String GDM_DELIVERY_STATUS = "deliveryStatus";
  String DELIVERY_AUTO_COMPLETE_ENABLED = "isDeliveryAutoCompleteEnabled";
  String UNIFIED_RECEIVING_ENABLED = "isUnifiedReceivingEnabled";
  String RDC_ENABLED = "isRdcEnabled";
  String FTS_ITEM_CHECK_ENABLED = "isFtsItemCheckEnabled";
  String GDM_FORCE_COMPLETE_DELIVERY_HEADER = "forcecomplete";

  String GDM_ITEM_NOT_FOUND = "Item %s not found";
  String GDM_ITEM_IMAGE_URL_SIZE_450 = "IMAGE_SIZE_450";
  String IMAGE_URL = "imageUrl";
  String ALLOW_RCV_UNTIL_FBQ = "allowRcvUntilFbq";

  // Constants for delivery trailer temperature
  String GDM_DELIVERY_TRAILER_TEMPERATURE_URI =
      "/api/deliveries/{deliveryNumber}/trailer-temperature";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_CONTENT_TYPE_HEADER =
      "application/vnd.TrailerTemperature1+json";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_PO_FINALIZED_ERROR_CODE =
      "TRAILER_TEMPERATURE_UPDATE_FAILED_AS_PO_FINALIZED";

  String GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_CODE = "GLS-RCV-GDM-TRAILER-SAVE-400";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_CODE =
      "GLS-RCV-GDM-TRAILER-SAVE-206";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_CODE =
      "GLS-RCV-GDM-TRAILER-SAVE-409";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_CODE = "GLS-RCV-GDM-500";

  String GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_MESSAGE =
      "Make sure all entered Zone temperatures have a corresponding PO assigned before saving.";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_MESSAGE =
      "All POs got updated except the following as they are already finalized: %s.";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_MESSAGE =
      "Temperatures cannot be updated because all POs have been finalized.";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_MESSAGE =
      "We are unable to process the request at this time. This may be due to a system issue. Please try again or contact your supervisor if this continues.";

  String GDM_DELIVERY_TRAILER_TEMPERATURE_BAD_DATA_ERROR_DESCRIPTION = "Missing PO assignment";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_PARTIAL_PO_FINALIZED_ERROR_DESCRIPTION =
      "Zone temperatures partially updated.";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_ALL_PO_FINALIZED_ERROR_DESCRIPTION =
      "Zone temperatures not updated.";
  String GDM_DELIVERY_TRAILER_TEMPERATURE_SERVICE_FAILED_ERROR_DESCRIPTION = "GDM service is down.";
  double GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MAX_VALUE = 999.9;
  double GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_MIN_VALUE = -999.9;
  String GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_INVALID_VALUE =
      "Invalid zone temperature value. Must be between %s and %s";
  String GDM_DELIVERY_TRAILER_ZONE_TEMPERATURE_EMPTY_ZONE_VALUE = "Empty zone temperature value";

  // Constants for Problem integrations
  String FIXIT_APP_NAME = "FIXIT";
  String FIXIT_ENABLED = "isFixitEnabled";
  String FIXIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI = "/issues/delivery/{deliveryNumber}/statistics";
  String FIXIT_GET_DAMAGES_BY_DELIVERY_URI = "/issues/delivery/{deliveryNumber}/damages";
  String PROBLEM_SERVICE = "problemService";
  String FIT_SERVICE = "fitService";
  String FIXIT_PLATFORM_SERVICE = "fixitPlatformService";
  String PROBLEM_STATUS_READY_TO_RECEIVE = "READY_TO_RECEIVE";
  String PROBLEM_STATUS_RECEIVED = "RECEIVED";
  String PROBLEM_RESOLUTION_NL = "NL";
  String PROBLEM_RESOLUTION_SUBTYPE_GRANDFATHERED = "GRANDFATHERED";
  String PROBLEM_RESOLUTION_KEY = "problemResolution";
  String ENABLE_PROBLEM_RESOLUTION_TYPE = "EnableProblemResolution";
  String GROCERY_PROBLEM_RECEIVE_FEATURE = "isGroceryProblemReceiveEnabled";
  String MIN_LIFE_EXPECTANCY_V2 = "enableMinLifeExpectancyV2";
  String ENABLE_PROBLEM_RESPONSE_PERSIST_FEATURE = "persistProblemResponseEnabled";
  String ENABLE_FIXIT_SERVICE_MESH_HEADERS = "enableFixitServiceMeshHeaders";
  String FIT_GET_PTAG_DETAILS_URI = "/issues/containers/{problemTagId}";
  String FIXIT_GET_PTAG_DETAILS_URI = "/issues/containers/{problemTagId}";
  String FIT_UPDATE_RECEIVED_CONTAINER_URI = "/issues/{issueId}/containers/{label}/receive";
  String FIXIT_UPDATE_RECEIVED_CONTAINER_URI = "/issues/{issueId}/containers/{label}/receive";
  String FIT_UPDATE_PROBLEM_RECEIVE_ERROR_URI =
      "/issues/{issueId}/containers/{label}/receive-error";
  String FIXIT_UPDATE_PROBLEM_RECEIVE_ERROR_URI =
      "/issues/{issueId}/containers/{label}/receive-error";
  String PROBLEM_V1_URI = "/api/v1";
  String FIT_GET_PROBLEM_COUNT_BY_DELIVERY_URI = "/issues/delivery/{deliveryNumber}/statistics";
  String FIT_GRAPH_QL_URI = "/graphql";
  String FIT_PROBLEMS_GRAPH_QL = "fitProblemsGraphQL";
  String FIT_PROBLEMS_GRAPH_QL_DEFAULT =
      "query{allIssues(freightIssueSearchInput:{deliveryNumber:\"%s\" issueType:[NOP,NOT_A_WALMART_FREIGHT,WRONG_SHIPMENT]}pageNumber:1 pageSize:100 sortBy:\"issueCreatedOn\" sortDirection:DESC){pageInfo{totalCount totalPages pageNumber pageSize filterCount hasNext hasPrev}issues{issueIdentifier issueType itemNumber itemDescription upcNumber plu issueQuantity}}}";
  String FIXIT_PROBLEMS_GRAPH_QL = "fixItProblemsGraphQL";
  String FIXIT_PROBLEMS_GRAPH_QL_DEFAULT =
      "query { searchException( searchInput: { delivery: { number: \"%s\" } exceptionCategory: \"PROBLEM\" details: { exceptionType: [\"NOP\", \"WRP\", \"NWF\"] } } sortDirection: DESC pageSize: 100 sortBy: \"createdOn\" pageContinuationToken: null ) { pageInfo { totalCount totalPages pageNumber pageSize filterCount hasNext hasPrev pageContinuationToken } issues { identifier details { exceptionType remainingQty exceptionQty } items { itemNumber itemUpc itemDescription pluNumber } } }}";
  String FIXIT_PROBLEM_TICKETS_GRAPH_QL = "fixItProblemTicketsGraphQL";
  String FIXIT_PROBLEM_TICKETS_GRAPH_QL_DEFAULT =
      "query { searchException( searchInput: { purchaseOrders: { poNumber: \"%s\" } exceptionCategory: \"PROBLEM\" details: { businessStatus: [\"ANSWERED\", \"ASSIGNED\", \"REASSIGNED\", \"AWAITING_INFORMATION\", \"SOLUTION_AVAILABLE\", \"READY_TO_RECEIVE\"] } } pageSize: 100 ) { pageInfo { totalCount filterCount pageContinuationToken } issues { exceptionId identifier status createdBy createdOn details { businessStatus exceptionType exceptionUOM exceptionQty } delivery { number trailerNumber } purchaseOrders { poNumber } } }}";
  String FIT_GRAPH_QL_ERROR_RESPONSE = "\"errors\":";
  String PROBLEM_RECEIVE_SERIALIZED = "SSCC";
  String PROBLEM_RECEIVE_UPC = "UPC";
  String ISSUE_ID = "issueId";
  String PROBLEM_LABEL = "label";
  String PROBLEM_TAG_ID = "problemTagId";
  String CREATE_PROBLEM_URI = "/v2/problems";
  String LOAD_QUALITY_SOURCE = "LOAD_QUALITY";
  String LOAD_QUALITY_INCIDENT_DOMAIN = "LOAD_EXCEPTION";
  String LOAD_QUALITY_TENANT = "WALMART_US";
  String LQ_INCIDENT_API_PATH = "/api/v1/incident";
  String LQ_EXCEPTION_API_PATH = "/api/v1/exception/{exceptionId}";
  String CREATE_EXCEPTION_ID_JSON_PATH = "$.data.createException.exceptionId";
  String CLOSE_EXCEPTION_ID_JSON_PATH = "$.data.closeException.exceptionId";
  String READY_TO_REVIEW = "READY_TO_REVIEW";
  String ENGLISH = "ENGLISH";
  String LOAD_EXCEPTION = "LOAD_EXCEPTION";
  String EXCEPTION_ID = "exceptionId";
  String CLOSED = "CLOSED";

  // Constants for LPN
  String LPN18_GENERATOR_BY_COUNT = "/lpn-generator/lpn18/";
  String LPN7_GENERATOR_BY_COUNT = "/lpn-generator/lpn7/";
  String LPN25_GENERATOR_BY_COUNT = "/lpn-generator/lpn25/";
  String LPNS = "lpns";
  String IS_LPN_7_DIGIT_ENABLED = "isLpn7DigitEnabled";
  // Constants for Topic and Queue Names
  // Listeners: WFT
  String PUB_SCAN_LOCATION_TOPIC = "US/WFT/RECEIVING/LOCATION/TOPIC";
  String PUB_INSTRUCTION_TOPIC = "TOPIC/RECEIVE/INSTRUCTION";
  String PUB_MOVE_TOPIC = "TOPIC/WMSMM/MOVE";
  // Listeners: RCV | Inventory publishes
  String SUB_INV_ADJ_QUEUE = "QUEUE.INVENTORY.CORE.CONTAINERINVENTORYTO";
  // Listeners: Inventory
  String PUB_RECEIPTS_TOPIC = "TOPIC/RECEIVE/RECEIPTS";
  String PUB_RECEIPTS_EXCEPTION_TOPIC = "TOPIC/RECEIVE/EXCEPTIONCONTAINER";
  // Listeners: Witron RTU msg
  String PUB_PUTAWAY_RTU_TOPIC = "WMSRECV/PUTAWAYREQUEST";

  String PUB_RECEIVE_UPDATES_ENABLED = "isPublishReceiveUpdatesEnabled";
  // Listeners: Receiving App
  String PUB_MQTT_NOTIFICATIONS_TOPIC = "TOPIC/RCV/PUSHNOTIF";

  // Constants for Putaway
  String PUTAWAY_HANDLER = "putawayHandler";
  String DEFAULT_PUTAWAY_HANDLER = "DefaultPutawayHandler";
  String WITRON_PUTAWAY_HANDLER = "WitronPutawayHandler";
  String PUTAWAY_EVENT_TYPE = "Putaway Request";
  String PUTAWAY_REQUEST = "PUTAWAY_REQUEST";
  String RECEIVING = "receiving";
  String PUTAWAY_ADD_ACTION = "add";
  String PUTAWAY_DELETE_ACTION = "delete";
  String PUTAWAY_UPDATE_ACTION = "update";
  String ZERO_STRING = "0";
  int VENDOR_DEPT_SEQ_NUMBERS_DEFAULT_SIZE = 9;

  // Constants for move
  String MOVE_CODE = "moveTypeCode";
  String MOVE_DESC = "moveTypeDesc";
  String MOVE_TYPE_PRIORITY = "movePriority";
  String MOVE_TYPE_CODE = "code";
  String MOVE_TYPE_DESC = "desc";
  String MOVE_TYPE = "moveType";
  String MOVE_EVENT = "moveEvent";
  String MOVE_TO_LOCATION = "toLocation";
  String MOVE_QTY_UOM = "moveQtyUOM";
  String MOVE_PRIORITY = "priority";
  String MOVE_SEQUENCE_NBR = "sequenceNbr";
  String MOVE_CONTAINER_TAG = "containerTag";
  String MOVE_FROM_LOCATION = "fromLocation";
  String MOVE_CORRELATION_ID = "correlationID";
  String MOVE_QTY = "moveQty";
  String MOVE_LAST_CHANGED_BY = "lastChangedBy";
  String MOVE_LAST_CHANGED_ON = "lastChangedOn";
  String MOVE_DEST_BU_NUMBER = "destBUNumber";
  String MOVE_DEST_NON_CON_DOCK_TAG = "moveDestForNonConDockTag";
  String MOVE_FLOOR_LINE_DEST_NON_CON_DOCK_TAG = "moveFloorLineDestForNonConDockTag";
  String MOVE_TYPE_BULK = "Bulk";
  String MOVE_TYPE_SINGLE = "Single";
  String MOVE_PRIME_LOCATION = "primeLocation";
  String MOVE_PRIME_LOCATION_SIZE = "primeLocationSize";
  String MOVE_PUBLISH_ENABLED = "isMovePublishEnabled";
  String MOVE_DEST_BU_ENABLED = "isMoveDestBuEnabled";
  String ENABLE_UPDATE_TOTAL_RECEIVE_QUANTITY = "updateTotalRcvQtyInInstructionDeliveryDocsEnabled";

  String PUT_ON_HOLD_SERVICE = "putOnHoldService";
  String DEFAULT_PUT_ON_HOLD_SERVICE = "DefaultPutOnHoldService";
  String WITRON_PUT_ON_HOLD_SERVICE = "WitronPutOnHoldService";
  String UPDATE_INVENTORY_EVENT_TYPE = "cntrAndItemAggStatusUpdate";
  String UPDATE_INVENTORY_URI =
      "/inventory/inventories/containers/statuses?action=" + UPDATE_INVENTORY_EVENT_TYPE;
  String UPDATE_INVENTORY_CRITERIA_URI = "/inventory/inventories/criteria";
  String INVENTORY_SEARCH_URI = "/inventory/inventories/containers/search";
  String UPDATE_INVENTORY_LOCATION_URI = "/inventory/inventories/containers/moves";
  String INVENTORY_EXCEPTION_CONTAINER_URL = "/inventory/inventories/exceptions";
  String INVENTORY_EXCEPTION_ADJUSTMENT_BULK_URL =
      "/inventory/inventories/containers/bulk/adjustment";

  String INVENTORY_EXCEPTION_BULK_URL = "/inventory/inventories/exceptions/bulk";

  // Constants for Inventory Adjustment Listener in JMSEventListener
  String INVENTORY_EXCEPTIONS_URI = "/inventory/inventories/exceptions/container";
  String INVENTORY_VTR_V2 = "/containers/adjust";

  String INVENTORY_ADJUSTMENT_EVENT = "event";
  String INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED = "container.updated";
  String INVENTORY_ADJUSTMENT_EVENT_CONTAINER_DELETED = "container.deleted";
  String INVENTORY_ADJUSTMENT_EVENT_OBJECT = "eventObject";
  String INVENTORY_ADJUSTMENT_TRACKING_ID = "trackingId";
  String INVENTORY_ADJUSTMENT_CONTAINER_TYPE = "containerTypeAbbr";
  String INVENTORY_ADJUSTMENT_PO_DETAILS = "poDetails";
  String INVENTORY_ADJUSTMENT_PURCHASE_REF_NUM = "poNum";
  String INVENTORY_ADJUSTMENT_PURCHASE_REF_LINE_NUM = "purchaseReferenceLineNumber";
  String INV_TOTE = "Tote";
  String INVENTORY_ADJUSTMENT_ITEM_LIST = "itemList";
  String INVENTORY_ADJUSTMENT_INBOUND_CHANNEL_TYPE = "inboundChannelType";
  String INVENTORY_ADJUSTMENT_CHILD_CONTAINERS = "childContainers";
  String INVENTORY_ITEM_DEST_SLOT_ID = "destSlotId";
  String INVENTORY_VNPK_RATIO = "vendorPkRatio";
  String INVENTORY_WHPK_RATIO = "warehousePkRatio";
  String INVENTORY_AVAILABLE_TO_SELL_QTY_UOM = "unitOfMeasurement";
  String INVENTORY_AVAILABLE_TO_SELL_QTY = "availabletosellQty";
  String INVENTORY_ADJUSTMENT_ADJUSTMENT_TO = "adjustmentTO";
  String INVENTORY_ITEM_STATUS_CHANGE = "itemStatusChange";
  String INVENTORY_RECEIVING_IN_PROGRESS_QTY = "receivingInProgressQty";
  String INVENTORY_ADJUSTMENT_REASON_CODE = "reasonCode";
  String INVENTORY_ADJUSTMENT_CODE = "code";
  String INVENTORY_ADJUSTMENT_TARGET_CONTAINERS = "targetContainers";
  String INVENTORY_ADJUSTMENT_QTY = "value";
  String INVENTORY_ADJUSTMENT_QTY_UOM = "uom";
  String INVENTORY_CONTAINER_ITEM_LIST = "containerInventoryList";
  String INVENTORY_ADJUSTMENTS_URI = "/inventory/inventories/exceptions";
  String INVENTORY_QTY_IN_VNPK = "venPkQuantity";
  String INVENTORY_DESTINATION_LOCATION_ID = "destinationLocationId";
  String CONTAINER_STATUS = "containerStatus";
  int VTR_REASON_CODE = 28; // Void To Reinstate
  int VTX_REASON_CODE = 52;
  int SHIP_VOID = 27;
  int TRUE_OUT = 29;
  int XDK_VOID = 131;
  String VTR_REASON_DESC = "Void To Reinstate";
  int DAMAGE_REASON_CODE = 11;
  int CONCEALED_DAMAGE_REASON_CODE = 38;
  int INVENTORY_RECEIVING_CORRECTION_REASON_CODE = 52;
  int INVENTORY_RECEIVING_CORRECTION_REASON_CODE_28 = 28;
  String RC_DESC = "Receiving Correction";
  int RDC_INVENTORY_RECEIVE_ERROR_REASON_CODE = 51;
  int RDC_RECEIVING_CORRECTION_REASON_CODE = 50;
  int VDM_REASON_CODE = 53;
  int RCS_CONCEALED_SHORTAGE_REASON_CODE = 54;
  int RCO_CONCEALED_OVERAGE_REASON_CODE = 55;
  int SAMS_VENDOR_CONCEALED_DAMAGE_OR_SHORT_REASON_CODE = 124;
  int SAMS_VENDOR_CONCEALED_OVERAGE_REASON_CODE = 125;
  String VTR_COMMENT = "RECEIVING ADJUSTMENT";
  String IDEM_POTENCY_KEY = "idemPotencyKey";
  String FLOW_NAME = "flowName";
  String MARKET_TYPE_PHARMACY = "PHARMACY";
  String MARKET_TYPE = "marketType";
  String DOCUMENT_TYPE = "documentType";
  String ADJUSTMENT_FLOW = "adjustmentFlow";
  String VTR_FLOW = "VTRFlow";

  String BASE_DIVISION_CODE = "WM";
  String SAMS_BASE_DIVISION_CODE = "sams";
  String WM_BASE_DIVISION_CODE = "wm";
  String FINANCIAL_REPORTING_GROUP_CODE = "US";
  String VDM_CLAIM_TYPE = "VENDOR";
  String INVENTORY_CONTAINERS_URL = "/inventory/inventories/containers/{trackingId}?";
  String INVENTORY_CONTAINERS_DETAILS_URI = INVENTORY_CONTAINERS_URL + "details=true";
  String INVENTORY_ITEMS_URI = "/inventory/inventories/containers/items";
  String INVENTORY_ITEMS_URI_ADJUST_V2 = "/container/item/adjust";
  String SOURCE_MOBILE = "MOBILE";
  String INVENTORY_V2_ITEM_NUMBER = "ITEM_NUMBER";

  String INVENTORY_ADJUSTMENT_URI = "/inventory/inventories/exceptions";
  String INVENTORY_PALLET_OFF_HOLD = "/inventory/inventories/items/unhold";
  String INVENTORY_PALLET_ON_HOLD = "/inventory/inventories/items/hold";
  String INVENTORY_PALLET_OFF_HOLD_V2 = "/containers/unhold";
  String INVENTORY_PALLET_ON_HOLD_V2 = "/containers/hold";
  String INVENTORY_OFF_HOLD_CONTAINER_ID = "containerId";
  String INVENTORY_OFF_HOLD_QTY = "unHoldAllQty";
  String INVENTORY_OFF_HOLD_TARGET_QTY = "targetQty";
  String INVENTORY_OFF_HOLD_TARGET_QTY_VALUE = "receivedQty";
  String INVENTORY_FINANCIAL_REPORTING_GROUP = "financialReportingGroup";
  String INVENTORY_BASE_DIVISION_CODE = "baseDivisionCode";
  String INVENTORY_ADJUST_BY = "adjustBy";
  String INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION =
      "/inventory/inventories/receipt?flow=rcvCorrection";
  String INVENTORY_RECEIPT_RECEIVE_AS_CORRECTION_V2 = "/containers/create";
  String INVENTORY_RECEIPT = "receipt";
  String INVENTORY_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
  String INVENTORY_EVENT = "invevent";
  String INVENTORY_EVENT_MOVED = "moved";
  String INVENTORY_EVENT_DELETED = "deleted";
  String INVENTORY_EVENT_LOADED = "loaded";
  String INVENTORY_LOCATION_NAME = "locationName";
  String INVENTORY_RE_INDUCT_LOCATION_NAME = "RE_INDUCT";
  String INVENTORY_DA_ATLAS_ITEM_LABEL_TYPE = "labelType";
  String INVENTORY_DA_ATLAS_ITEM_CONTAINER_STATUS = "containerStatus";
  String INVENTORY_DA_ATLAS_ITEM_CONTAINER_LOCATION_DETAILS = "containerLocationDetails";
  String INVENTORY_CONTAINER_LOCATION_DETAILS_SORTER_ID = "sorterId";
  String INVENTORY_EVENT_PICKED = "picked";
  String INVENTORY_EVENT_SOURCE_SYSTEM = "sourceSys";
  int INVENTORY_EVENT_SOURCE_SYSTEM_ATLAS = 0;
  String VNPK = "VNPK";
  String WHPK = "WHPK";
  String CASE = "CASE";
  String SERIALIZED = "SERIALIZED";
  String PO_TEXT = "PO";
  String ASN_TEXT = "ASN";
  String EPCIS_TEXT = "EPCIS";
  String GDM_PURCHASE_ORDER_ACCEPT_TYPE = "application/vnd.purchaseorderresponse1+json";
  String GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE = "application/vnd.DeliveryResponse1+json";
  String GDM_DOCUMENT_GET_BY_POLEGACY_V3_CONTENT_TYPE = "application/vnd.searchdelivery1+json";
  String GDM_DELIVERY_SEARCH_V3_CONTENT_TYPE = "application/vnd.SearchDeliveryHeader1+json";
  String GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE =
      "application/vnd.DeliveryShipmentScanResponse2+json";
  String GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v3 =
      "application/vnd.DeliveryShipmentScanResponse3+json";
  String GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_v4 =
      "application/vnd.DeliveryShipmentScanResponse4+json";
  String IS_GDM_SHIPMENT_GET_BY_SCAN_v4_ENABLED = "is.gdm.shipment.api.v4.enabled";
  String GDM_SHIPMENT_GET_BY_SCAN_SSCC_ACCEPT_TYPE_V1 =
      "application/vnd.DeliveryShipmentScanResponse1+json";
  String GDM_SEARCH_SHIPMENT_BY_DELIVERY_ACCEPT_TYPE =
      "application/vnd.packglobalsearchresponse1+json";
  String GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_ACCEPT_TYPE = "application/vnd.GlobalSearchResponse1+json";
  String GDM_SEARCH_SHIPMENT_BY_GTIN_LOT_ACCEPT_TYPE_V2 =
      "application/vnd.GlobalSearchResponse2+json";
  String GDM_LINK_DELIVERY_WITH_SHIPMENT_ACCEPT_TYPE =
      "application/vnd.deliveryshipmentattach1+json";
  String GDM_UPADATE_ITEM_VERIFIED_ON_ACCEPT_TYPE = "application/vnd.UpdateItemVerifiedOn1+json";
  String GDM_SEARCH_DELIVERY_HEADER_CONTENT_TYPE = "application/vnd.SearchDeliveryHeader1+json";
  String GDM_SEARCH_CONSOLIDATED_DELIVERY_DETAILS_TYPE =
      "application/vnd.SearchDeliveryConsolidated1+json";
  String GDM_SHIPMENT_GET_BY_PACK_NUM_ACCEPT_TYPE_V2 =
      "application/vnd.PackGlobalSearchResponse2+json";

  String GDM_SEARCH_SHIPMENT_HEADER_V1 = "application/vnd.ShipmentHeaderSearch1+json";
  String RCV_GDM_INCLUDE_OPEN_INSTRUCTIONS = "openInstructions";
  String RCV_GDM_PO = "purchaseReferenceNumber";
  String RCV_GDM_PO_LINE = "lineNumber";
  String GDM_DELIVERY_HEADER_DETAILS_BY_DELIVERY_NUMBERS_ACCEPT_TYPE =
      "application/vnd.DeliveryHeaderDetailsResponse+json";
  int GTIN14_LENGTH = 14;

  // Constants for RestUtils
  String RESTUTILS_INFO_MESSAGE = "Outbound url={}, request={}, response={}";
  String GDM_RESTUTILS_INFO_MESSAGE = "GDM url={}, request={}, response={}";
  String RESTUTILS_ERROR_MESSAGE =
      "Error fetching url={}, request={}, response={}, error & httpStatus={}";
  String RESTUTILS_ERROR_MESSAGE_WITH_ERROR_DETAILS =
      "Error fetching url={}, request={}, response={}, httpStatus={} ,error={}";
  String SCHEDULER_APPEND_PO_TO_DELIVERY_DETAILS =
      "Append PO to Delivery URI = {} , AppendRequestBody = {} , AppendRequestResponse = {}";

  String SCHEDULER_APPEND_PO_TO_DELIVERY_RESPONSE =
      "Received success response: {} from scheduler for request: {}";

  String SCHEDULER_APPEND_PO_TO_DELIVERY_ERROR =
      "Error in calling scheduler Service for url={}  jsonRequest={}, response={}, Headers={}";

  String OMS_GET_PO_DETAILS_ERROR =
      "Error in OMS API Service for getting PO details for url={} poNumber = {} , Headers={}, Exception={}";

  String OMS_GET_PO_DETAILS_UNSUCCESSFUL =
      "Response is empty or status code not 200 poNumber= {} , url={}, Headers={}, response={}";

  String BAD_RESPONSE_ERROR_MSG =
      "Client exception from %s. HttpResponseStatus= %s ResponseBody = %s";
  String OF = "OF";

  // Constants for dock tag service
  String DOCK_TAG_NOT_FOUND_MESSAGE = "Error: DockTag '%s' not found.";
  String DOCK_LPN_TAG_NOT_FOUND_MESSAGE = "Error: DockTag/ContainerId '%s' not found.";
  String DOCK_TAG_ALREADY_RECEIVED_MESSAGE = "Dock tag: %s has already been completed by user %s";
  String DOCK_TAG_NOT_NON_CON_MESSAGE = "Dock tag: %s is not a non con dock tag";
  String DOCK_TAG_NOT_FLOOR_LINE_MESSAGE = "Dock tag: %s is not a floor line dock tag";
  String DOCK_TAG_ERROR_MESSAGE = "Exception while processing dock tag id = %s";
  String DOCK_TAG_CREATE_ERROR_MESSAGE = "Exception while creating dock tag";
  String DOCK_TAG_COMMON_ITEM_UPC_MMR_ERROR_MESSAGE =
      "Scanned dock tag %s contains one or more items in common with the dock tags attached at the ACL";
  String DOCK_TAG_COMMON_ITEM_MMR_ERROR_MESSAGE =
      "Scanned dock tag %s contains one or more items in common with the dock tags attached at the ACL";
  String DOCK_TAG_LOCATION_IN_US_MMR_ERROR_MESSAGE =
      "Scanned location %s already has a dock tag attached";
  String INVENTORY_CONTAINERS_DELETE_URI = INVENTORY_CONTAINERS_URL + "keyForDelete=delKey";
  String INVENTORY_NOT_FOUND_MESSAGE = "Inventory not found for tracking id = %s";
  String INVENTORY_SERVICE_DOWN = "Error while calling Inventory";
  String EXCEED_MAX_ALLOWED_DOCKTAG_REQUEST = "Maximum allowed docktags to complete is 15";
  String INVENTORY_CONTAINERS_DELETE_PATH = "/containers/trackingId";
  String DOCK_TAG_NOT_FOUND_RDC_MESSAGE =
      "The scanned location or tag does not have a delivery assigned to it. Please contact your supervisor.";
  String DATA_NOT_FOUND_FOR_THE_GIVEN_SEARCH_CRITERIA =
      "DockTag details not found for the given search criteria";
  String UNABLE_TO_COMPLETE_DOCKTAG = "Error while completing docktag = %s";
  String GDM_PARTIAL_RESPONSE = "Partial response from GDM, Delivery or Po/PoLine is not present";

  // Endgame Constants
  String LABELING_HANDLER = "labelingHandler";
  String DELIVERY_EVENT_HANDLER = "deliveryEventHandler";
  String KAFKA_DELIVERY_EVENT_HANDLER = "kafkaDeliveryEventHandler";
  String DELIVERY_STATUS_PUBLISHER = "deliveryStatusHandler";
  String SYM_SYSTEM_DEFAULT_VALUE = "SYM2_5";
  String DEFAULT_LABELING_SERVICE = "defaultLabelingService";
  String DEFAULT_DELIVERY_EVENT_PROCESSOR = "defaultDeliveryEventProcessor";
  String ENDGAME_LABELING_SERVICE = "endGameLabelingService";
  String ENDGAME_DELIVERY_EVENT_PROCESSOR = "endgameDeliveryEventProcessor";
  String ENDGAME_MANUAL_DELIVERY_PROCESSOR = "endgameManualDeliveryProcessor";
  String ENDGAME_SLOTTING_SERVICE = "endgameSlottingService";
  String QUEUE_DELIVERY_UPDATED = "QUEUE.RCV.GDM.DELIVERY.UPDATE";
  String EVENT_DELIVERY_SCHEDULED = "SCHEDULED";
  String EVENT_DOOR_ASSIGNED = "DOOR_ASSIGNED";
  String EVENT_DELIVERY_ARRIVED = "ARRIVED";
  String EVENT_DELIVERY_ARV = "ARV";
  String EVENT_PO_ADDED = "PO_ADDED";
  String EVENT_PO_UPDATED = "PO_UPDATED";
  String EVENT_PO_LINE_ADDED = "PO_LINE_ADDED";
  String EVENT_PO_LINE_UPDATED = "PO_LINE_UPDATED";
  String EVENT_DELIVERY_SHIPMENT_ADDED = "DELIVERY_SHIPMENT_ADDED";
  String SHIPMENT_ADDED = "SHIPMENT_ADDED";
  String SHIPMENT_UPDATED = "SHIPMENT_UPDATED";
  String OSDR_PROCESSOR = "osdrProcessor";
  String DEFAULT_OSDR_PROCESSOR = "defaultOsdrProcessor";
  String PRE_LABEL_GEN_FALLBACK = "PRE_LABEL_GEN_FALLBACK";

  String RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE = "Error in fetching resource";
  String RESTUTILS_UNKNOWN_EXCEPTION = "Unknown exception";
  String GDM_INTERNAL_SERVER_ERROR = "GDM error while calling delivery update api";
  String EXCEPTION_HANDLER_ERROR_MESSAGE = "Got exception {}";
  String EXCEPTION_HANDLER_ERROR_MESSAGE_TYPE = "{} : Got exception {}";
  String EXCEPTION_HANDLER_ERROR_MESSAGE_DETAIL = "{} : Got exception {}. Error Code {}";

  String ENCRYPTION_ERROR_MESSAGE = "Got exception while encrypting the {} {} exception {}";
  String DECRYPTION_ERROR_MESSAGE = "Got exception while decrypting the {} {} exception {}";
  String DECRYPT_ERROR_MESSAGE = "Unable to decrypt.";

  String OSDR_DETAILS_NOT_FOUND_ERROR_MSG =
      "Osdr details not available for [deliveryNumber={}] [exception={}]";

  String DELIM_DASH = "-";

  // PrintLabel
  String PRINTING_ANDROID_ENABLED = "printingAndroidEnabled";
  String PRINT_LABEL_WITRON_TEMPLATE = "pallet_lpn_witron"; // pallet_lpn_witron.fmt
  String PRINT_LABEL_USER_ID = "WMT_UserId";
  String PRINT_LABEL_PRINT_DATE = "printDate";
  String PRINT_LABEL_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss z";
  String PRINT_LABEL_MONTH_DAY = "MM/dd";
  String PRINT_LABEL_USER_ID_V1 = "userId";
  String PRINT_LABEL_IDENTIFIER = "labelIdentifier";
  String PRINT_LABEL_CORRELATION_ID = "WMT_CorrelationId";
  String PRINT_LABEL_FORMAT_NAME = "formatName";
  String PRINT_LABEL_TTL = "ttlInHours";
  String PRINT_LABEL_TTL_CONFIG_KEY = "printLabelTtlHrs";
  int PRINT_LABEL_DEFAULT_TTL = 72; // hours
  long TCL_MAX_PER_DELIVERY = 5000;
  long TCL_MIN_PER_DELIVERY = 300;
  int MABD_DEFAULT_NO_OF_DAYS = 365; // days
  String MABD_RESTRICT_NO_OF_DAYS = "mabdRestrictDays";
  String IS_VNPK_PALLET_QTY_ENABLED = "isVnpkEqualPalletQtyCheck";
  String TCL_MAX_PER_DELIVERY_KEY = "tclMaxPerDelivery";
  String TCL_MIN_PER_DELIVERY_KEY = "tclMinPerDelivery";
  String PRINT_LABEL_ROTATE_DATE_MM_DD_YYYY = "MM/dd/yyyy";

  String PRINT_PRINTER_ID = "printerId";
  String PRINT_KEY = "key";
  String PRINT_VALUE = "value";
  String PRINT_DATA = "data";
  String PRINT_WAREHOUSE_AREA_CD = "WA";
  String PRINT_JOB_ID_KEY = "printJobId";
  String PRINT_HEADERS_KEY = "headers";
  String PRINT_CLIENT_ID_KEY = "clientId";
  String PRINT_REQUEST_KEY = "printRequests";
  String SOUTH_CENTRAL_DC_CONFIG = "scDbConfig";
  String US_WEST_DC_CONFIG = "uwDbConfig";
  String COMMON_DB_CONFIG = "commonDbConfig";
  String SOUTH_CENTRAL_DATACENTER = "sc";
  String US_WEST_DATACENTER = "uw";
  String DC_FIN_POST_RECEIPTS = "/purchase";
  String DC_FIN_POST_RECEIPTS_V2 = "/v2/purchase";
  String PO_CLOSURE = "/poClose";
  String DC_FIN_POST_V2_ADJUST = "/v2/adjustment";
  String DCFIN_PO_CLOSURE = "dcfinPoClose";
  String DCFIN_ADJUST = "dcfinAdjust";
  String DCFIN_PURCHASE_POSTING = "dcfinPurchasePosting";
  String GDM_EVENT_POSTING = "gdmEventPosting";
  String DELIM_MODULO = "%";
  String DB_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
  String HBM2DDL_AUTO_NONE = "none";
  String HIBERNATE_DIALECT = "org.hibernate.dialect.SQLServer2012Dialect";

  String SLOTTING_DIVERT_URI = "/smartslotting/api/divert/getDivertLocations";
  String SLOTTING_PALLET_OFF_HOLD = "OFF_HOLD";

  String MDM_ITEM_SEARCH_PATH = "/items/wm";
  String GENERIC_MDM_ITEM_SEARCH_PATH = "/items/%s";
  String MDM_UPC_UPDATE_PATH = "/items/%s/dcprop/?overrideRules=true";
  String MDM_SSOT_READ_QUERY_PARAM = "?isSSOTflow=%s";

  String AUDIT_FLAG_PATH = "/auditFlag";

  // Constants for activity name of instruction
  String DA_CON_ACTIVITY_NAME = "DACon";
  String DA_NON_CON_ACTIVITY_NAME = "DANonCon";
  String SSTK_ACTIVITY_NAME = "SSTK";
  String PBYL_ACTIVITY_NAME = "PBYL";
  String ACL_ACTIVITY_NAME = "ACL";
  String POCON_ACTIVITY_NAME = "POCON";
  String DSDC_ACTIVITY_NAME = "DSDC";
  String PBYL_DOCK_TAG_ACTIVITY_NAME = "PBYL DOCKTAG";

  String ONEOPS_ENVIRONMENT = "ONEOPS_ENVIRONMENT";
  String COUNTRY_CODE_US = "US";

  String DELIVERY_STATUS = "deliveryStatus";
  String DEFAULT_MDC = "self";
  String DEFAULT_MDC_USER = "RCV_ASYNC";
  String TENANT_NOT_SUPPORTED_ERROR_MSG = "Tenant-%s not supported";
  String INVALID_BEAN_NAME_ERROR_MSG = "Bean = %s is not in current spring-context";
  String TENANT_CONFIG_ERROR_MSG = "Error while getting tenant specific configs";
  String EMPTY_STRING = "";

  String CONFIRM_PO_PROCESSED_STATUS = "PROCESSED";

  String COUNTRY_CODE = "countryCode";
  String ORIGIN_FACILITY_NUM = "originFacilityNum";
  String BU_NUMBER = "buNumber";
  String ACL_NOTIFICATION_ENABLED = "aclNotificationEnabled";
  String ACL_VERIFICATION_PROCESSOR = "aclVerificationProcessor";
  String ACL_NOTIFICATION_PROCESSOR = "aclNotificationProcessor";
  String INSTRUCTION_SAVE_ENABLED = "instructionSaveEnabled";
  String ENABLE_FILTER_CANCELLED_PO = "enableFilterCancelledPo";
  String ENABLE_POPULATE_RECEIVED_QTY_PO = "enablePopulateReceivedQtyInPO";
  String ENABLE_POPULATE_RECEIVED_QTY_PO_MOBILE = "enablePopulateReceivedQtyInPOMobile";
  String DC_FIN_PO_CLOSE_ASYNC_ENABLED = "dcFinPoCloseAsyncEnabled";
  String IS_CONFIRM_POS_PERF_V1_ENABLED = "isConfirmPOsPerfV1Enabled";
  // READ_ALL_RECEIPTS_ONCE and not for each po, poLine db hit
  String GET_DELIVERY_DETAILS_PERF_V1 = "isDeliveryDetailsPerfV1Enabled";
  String ENABLE_SYM3_SLOT_CHANGE_IN_LABEL = "is.enable.sym3.slot.change.in.label";

  String BOL_WEIGHT_CHECK_ENABLED = "enableBOLWeightCheck";

  String DOCKTAG_INSTRUCTION_MESSAGE = "Create dock tag container instruction";

  // Constants for location service
  String LOCATION_SEARCH_URI = "/locations/search?fields=tags";
  String NAMES = "names";
  String SUCCESS_TUPLES = "successTuples";
  String RESPONSE = "response";
  String LOCATIONS = "locations";
  String ATTRIBUTES = "attributes";
  String TAGS = "tags";
  String DOMAIN = "domain";
  String TAG_NAMES = "tagNames";
  String ACL = "acl";
  String KEY = "key";
  String KAFKA_TOPIC = "KAFKA_TOPIC";
  String VALUE = "value";
  String TYPE = "type";
  String SUB_TYPE = "subType";
  String FLOORLINE = "Floorline";
  String MAPPED_FLOOR_LINE = "mapped_floor_line";
  String MAPPED_PBYL_AREA = "mapped_pbyl_area";
  String MULTI_MANIFEST_LOCATION = "multi_manifest_location";
  String MAPPED_PARENT_ACL_LOCATION = "mapped_parent_acl_location";
  String PBYL_DOOR = "pbyl_door";
  String PROPERTIES = "properties";
  String NAME = "name";
  String PRIME_SLOT = "primeSlot";
  String SC_CODE = "scCode";
  String ID = "id";
  String FLIB_DOOR = "flib.door";
  String FLIB_TAG_NAME = "flib";
  String LOCATION_SERVICE_DOWN = "Error while searching for location = %s";
  String LOCATION_NOT_FOUND = "Location %s not found";
  String INVALID_LOCATION = "Invalid Location";
  String INVALID_LOCATION_FOR_FLOOR_LINE =
      "The scanned location is not valid. Please scan a valid floor line ACL.";
  String INVALID_LOCATION_FOR_FLOOR_LINE_MUTLI_MANIFEST =
      "The scanned location is a multi manifest location. Please scan a valid work location.";
  String INVALID_LOCATION_FOR_PBYL =
      "The scanned location is not valid. Please scan a valid PBYL Location.";
  String LOCATION_RESP_IS_EMPTY =
      "The scanned location %s is not valid. Please scan a valid location.";
  String INVALID_LOCATION_RESPONSE =
      "The locationId or locationType or locationName or sccCode is missing in location api response for locationId: %s";
  String VALIDATE_LOCATION_INFO_ENABLED = "isValidateLocationInfoEnabled";
  String KAFKA_WFT_LOCATION_PUBLISH_ENABLED = "isWftLocationPublishEnabledInKafka";
  String ENABLE_EMPTY_LOCATION_RESPONSE_ERROR = "enableEmptyLocationResponseError";
  String CASE_WEIGHT_LOWER_LIMIT = "caseWeightLowerLimit";
  String CASE_WEIGHT_MULTIPLIER = "caseWeightMultiplier";
  double DEFAULT_CASE_WEIGHT_LOWER_LIMIT = 2;
  double DEFAULT_CASE_WEIGHT_MULTIPLIER = 0.9;

  // Constants for labelling jar
  String LABEL_FORMATTING_ERROR = "Error while generating label";
  String LABELLING_LIBRARY_REFRESH_ERROR = "Error while refreshing labelling library";
  String INVALID_TENANT_ERROR_MSG = "Invalid tenant headers in %s";
  String INVALID_HEADER_ERROR_MSG = "Invalid header key=%s, value=%s";
  String INVALID_DELIVERY_STATUS_UPDATE_REQUEST =
      "Mandatory delivery details are missing while updating GDM delivery status for deliveryNumber: %s";
  String INVALID_REPRINT_LABEL_REQUEST =
      "Mandatory label identifier details are missing while reprinting container labels";
  String GDM_SHIPMENT_DELIVERY_LINK_FAILURE = "Error while linking Shipment with Delivery.";
  String GDM_GET_UNIT_SERIAL_DETAIL_FAILURE = "Error while getting unit serial details.";

  // Constants for ACL Service
  String ACL_SERVICE_DOWN = "Unable to reach ACL service.";

  // Constants for SUMO
  String SUMO = "Sumo";
  String SUMO_API_PUSH = "/api/push";
  String SUMO2_PUSH = "/apiv2/push";
  String SUMO_SERVICE_DOWN = "We are unable to reach Sumo service.";
  String DEFAULT_DELIVERY_STATUS_PUBLISHER = "defaultDeliveryStatusProcessor";
  String KAFKA_DELIVERY_STATUS_PUBLISHER = "kafkaDeliveryStatusProcessor";
  String JMS_INSTRUCTION_PUBLISHER = "jmsInstructionPublisher";
  String KAFKA_INSTRUCTION_PUBLISHER = "kafkaInstructionPublisher";
  String INSTRUCTION_PUBLISHER = "instructionPublishHandler";
  String DELIVERY_COMPLETE_EVENT_HANDLER = "deliveryStatusHandler";
  String API_NOT_ACCESSABLE = "This API is not accessible for env=%s";
  String SEND_ACL_NOTIFICATIONS_TO_SUMO_ENABLED = "isSendACLNotificationsToSumoEnabled";
  String SEND_ACL_NOTIFICATIONS_VIA_MQTT_ENABLED = "isSendACLNotificationsViaMqttEnabled";
  String IS_HOLD_FOR_SALE_TAG_ENABLED = "isHoldForSaleTagEnabled";

  String IS_CREATE_CONTAINER_IN_SYNC_FOR_INVENTORY = "isCreateContainerInSyncForInventory";
  String IS_OUTBOX_ENABLED_FOR_INVENTORY = "isOutboxEnabledForInventory";
  String IS_EG_DC_FIN_OUTBOX_PATTERN_ENABLED = "isEGDCFinOutboxPatternEnabled";

  String ENABLE_ENTER_QUANTITY_1P = "enableEnterQuantity1p";
  String ENABLE_AUDIT_BY_CASE_WEIGHT = "enableAuditByCaseWeight";
  String ENABLE_RIP_INVENTORY_ADJUSTMENT = "enableRIPInventoryAdjustment";
  double GRAM_TO_LB = 0.00220462;
  String IS_NEW_ITEM = "isNewItem";
  String DISABLE_AUTO_RECEIVING = "disableAutoReceiving";
  String ENABLE_AUDIT_REQUIRED_FLAG = "enableAuditRequiredFlag";
  String IS_AUDIT_REQUIRED = "isAuditRequired";
  String AUDIT_V2_ENABLED = "audit.v2.enabled";
  String IS_AUDIT_IN_PROGRESS = "isAuditInProgress";
  String CASES_TO_BE_AUDITED = "casesToBeAudited";
  String EXPECTED_CASE_QUANTITY = "expectedCaseQuantity";
  String RECEIVED_CASE_QTY = "receivedcaseQty";
  String TRUSTED_VENDOR = "Trusted";
  String NON_TRUSTED_VENDOR = "Non-Trusted";
  String IN_CONSISTENT_VENDOR = "Inconsistent";
  String FLUID_REPLEN_CASE_ENABLED = "fluidReplenCaseEnabled";
  String ASN_RECEIVING_ENABLED = "asnReceivingEnabled";
  String WFS_AUDIT_CHECK_ENABLED = "wfsAuditCheckEnabled";
  String CANCEL_MULTIPLE_INSTR_REQ_HANDLER = "cancelMultipleInstructionRequestHandler";
  String IS_SUMO2_ENABLED = "isSumo2Enabled";
  String IS_UPDATE_CONTAINER_LABEL_ENABLED = "isUpdateContainerLabelEnabled";

  // Constants for MQTT
  int MQTT_QOS = 2;
  boolean MQTT_MESSAGE_TO_BE_RETAINED = false;
  String MQTT_NOTIFICATION_PUBLISHER = "mqttNotificationPublisher";

  // RefreshInstructionHandler
  String REFRESH_INSTRUCTION_HANDLER_KEY = "refreshInstructionHandler";
  String GDC_REFRESH_INSTRUCTION_HANDLER = "gdcRefreshInstructionHandler";
  String DEFAULT_REFRESH_INSTRUCTION_HANDLER = "DefaultRefreshInstructionHandler";

  // Constants for Witron Instructions Provider
  String INSTRUCTION = "instruction";
  String CONTAINER = "container";
  String CONTAINER_LIST = "containerlist";
  String OSDR_PAYLOAD = "osdrPayload";
  String GDC_RECEIVE_INSTRUCTION_HANDLER = "gdcReceiveInstructionHandler";
  String CC_RECEIVE_INSTRUCTION_HANDLER = "CCReceiveInstructionHandler";
  String INSTRUCTION_SERVICE = "instructionService";
  String CONTAINER_SERVICE = "containerService";
  String DEFAULT_CONTAINER_SERVICE = "ContainerService";
  String RDC_CONTAINER_SERVICE = "RdcContainerService";
  String EXCEPTION_SERVICE = "exceptionService";
  String DEFAULT_INSTRUCTION_SERVICE = "DefaultInstructionService";
  String WITRON_INSTRUCTION_SERVICE = "WitronInstructionService";
  String TIMESTAMP_HEADER = "timestamp";
  String MSG_TIMESTAMP = "msgTimestamp";
  String MESSAGE_ID_HEADER = "messageId";
  String MESSAGE_ID = "Message_id";
  String FDE_SERVICE = "fdeServiceImpl";
  String FDE_SERVICE_BEAN = "fdeService";
  String ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES = "enableAllocationServiceErrorMessages";
  String WITRON_DELIVERY_METADATA_SERVICE = "WitronDeliveryMetaDataService";
  String ENDGAME_DELIVERY_METADATA_SERVICE = "endGameDeliveryMetaDataService";
  String DEFAULT_NODE = "default";
  String IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED =
      "isDeliveryStatusPublishInInstructionSearchDisabled";

  String DC_FIN_SERVICE = "dcFinService";
  String DEFAULT_DC_FIN_SERVICE = "DCFinService";
  String DC_FIN_SERVICE_V2 = "dcFinServiceV2";
  Integer DSDC_LEGACY_TYPE = 73;

  String DOCK_TAG_SERVICE = "dockTagService";
  String DEFAULT_DOCK_TAG_SERVICE = "DefaultDockTagService";
  String DEFAULT_LPN_CACHE_SERVICE = "lpnCacheService";
  String DEFAULT_LPN_SERVICE = "lpnService";
  String WITRON_LPN_SERVICE = "WitronLPNService";
  String WITRON_LPN_CACHE_SERVICE = "WitronLPNCacheService";

  String REPORT_SERVICE = "reportService";
  String DEFAULT_REPORT_SERVICE = "DefaultReportService";

  int PRINT_FIRST_REQUEST = 0;
  String DOCK_TAG = "Dock Tag";
  String RECEIVING_PROVIDER_ID = "RCV";
  String DELIVERY_EVENT_SERVICE = "deliveryEventService";
  String INSTRUCTION_PERSISTER_SERVICE = "instructionPersisterService";
  String CONTAINER_PERSISTER_SERVICE = "containerPersisterService";
  String RECEIPT_SERVICE = "receiptService";
  String PRINTJOB_SERVICE = "printjobService";
  String RETRY_SERVICE = "retryService";
  String ITEM_CATALOG_SERVICE = "itemCatalogService";
  String ITEM_SERVICE_HANDLER = "itemServiceHandler";
  String ITEM_OVERRIDE_SAME_DAY_ALLOWED = "item.override.same.day.allowed";
  String IS_TEMP_ITEM_OVERRIDE_ENABLED = "is.temp.item.override.enabled";
  String ACC_ITEM_CATALOG_SERVICE = "accItemCatalogService";
  String DEFAULT_ITEM_CATALOG_SERVICE = "DefaultItemCatalogService";
  String DEFAULT_ITEM_SERVICE_HANDLER = "defaultItemServiceHandler";
  String KAFKA_ACC_ITEM_CATALOG_SERVICE = "kafkaACCItemCatalogService";
  String DELIVERY_METADATA_SERVICE = "deliveryMetaDataService";
  String ACC_DELIVERY_METADATA_SERVICE = "accDeliveryMetaDataService";
  String FIXTURE_DELIVERY_METADATA_SERVICE = "fixtureDeliveryMetaDataService";
  String ACC_NOTIFICATION_SERVICE = "accNotificationService";
  String ACC_DELIVERY_EVENT_SERVICE = "accDeliveryEventService";
  String ACC_LABEL_DATA_SERVICE = "accLabelDataService";
  String LABEL_INSTRUCTION_DATA_SERVICE = "labelInstructionDataService";
  String JMS_DELIVERY_LINK_PUBLISHER = "jmsDeliveryLinkPublisher";
  String LABEL_GENERATOR_SERVICE = "labelGeneratorService";
  String GENERIC_LABEL_GENERATOR_SERVICE = "genericLabelGeneratorService";
  String HAWK_EYE_LABEL_GENERATOR_SERVICE = "hawkEyeLabelGeneratorService";
  String LABEL_DATA_PUBLISHER = "labelDataPublisher";
  String JMS_LABEL_DATA_PUBLISHER = "jmsLabelDataPublisher";
  String KAFKA_LABEL_DATA_PUBLISHER = "kafkaLabelDataPublisher";
  String DELIVERY_LINK_SERVICE = "deliveryLinkService";

  // International constants
  String JMS_RECEIPT_PUBLISHER = "jmsReceiptPublisher";
  String KAFKA_RECEIPT_PUBLISHER = "kafkaReceiptPublisher";
  String RECEIPT_EVENT_HANDLER = "receiptEventHandler";
  String KAFKA_RECEIPT_EVENT_HANDLER = "kafkaReceiptEventHandler";
  String KAFKA_RECEIPT_PUBLISH_ENABLED = "isKafkaReceiptPublishEnabled";
  String RECEIPT_PUBLISH_FLOW = "Receipts publish to SCT flow";
  String MOVE_EVENT_HANDLER = "moveEventHandler";
  String JMS_MOVE_PUBLISHER = "jmsMovePublisher";

  String RC_CONTAINER_SERVICE = "rcContainerService";
  String RC_PACKAGE_TRACKER_SERVICE = "rcPackageTrackerService";
  String ITEM_TRACKER_SERVICE = "itemTrackerService";
  String RC_WORKFLOW_SERVICE = "rcWorkflowService";
  String RECEIVING_WORKFLOW_TRANSFORMER = "receivingWorkflowTransformer";
  String RECEIVING_WORKFLOW_UTIL = "receivingWorkflowUtil";
  String RECEIVING_WORKFLOW_VALIDATOR = "receivingWorkflowValidator";
  String BLOB_FILE_STORAGE_SERVICE = "blobFileStorageService";
  String RC_PRODUCT_CATEGORY_GROUP_SERVICE = "rcProductCategoryGroupService";
  String PRODUCT_CATEGORY_GROUP_TRANSFORMER = "productCategoryGroupTransformer";
  String RC_ITEM_IMAGE_SERVICE = "rcItemImageService";
  String ORDER_LINES_ENRICHMENT_UTIL = "orderLinesEnrichmentUtil";
  String EVENT = "event";
  String LPN_EXCEPTION = "lpnException";
  String LPN_CREATE = "lpn-create";
  String LABEL_TYPE_FOR_PUT_SYSTEM = "label_type";
  String LABEL_TYPE_PUT = "PUT";
  String ERROR = "error";
  String CONTROL_TOWER_SERVICE_DOWN = "We are unable to reach Control Tower";
  Object CONTROL_TOWER = "Control Tower";
  Object ITEM_REP = "Item Rep";
  String ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY =
      "atlasSecuredKafkaListenerContainerFactory";

  String ATLAS_OFFLINE_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY =
      "atlasOfflineSecuredKafkaListenerContainerFactory";
  String KAFKA = "Kafka";
  String DELIVERY_SERVICE_V3 = "deliveryServiceV3Impl";
  String DELIVERY_SERVICE_RETRYABLE_V3 = "deliveryServiceRetryableV3Impl";
  String KAFKA_PRODUCER = "receiveKafkaProducer";
  String SECURE_KAFKA_PRODUCER = "secureAtlasKafkaProducer";
  String SECURE_KAFKA_TEMPLATE = "secureAtlasKafkaTemplate";
  String LOG_WRONG_MESSAGE_FORMAT = "Message format is wrong {}.";
  String WRONG_DELIVERY_UPDATE_MESSAGE_FORMAT =
      "Message format on DeliveryUpdate Listener is wrong - {}";
  String INVALID_ADJUSTMENT_MESSAGE_FROM_INVENTORY =
      "Received invalid adjustment message from Inventory, message: %s";
  String INVALID_INSTRUCTION_DOWNLOAD_MESSAGE = "Received invalid instruction download message: %s";
  String UNABLE_TO_PROCESS_DEL_UPDATE_ERROR_MSG =
      "Unable to process the delivery update message . payload = %s";
  String BACKOUT_CONTAINER_RESPONSE =
      "This container %s is already backed-out. No need to re-process.";
  String SUCCESS = "Success";
  String DEFAULT_LABEL_SERVICE = "defaultLabelService";
  String RDC_LABEL_SERVICE = "rdcLabelService";
  String GDC_LABEL_SERVICE = "gdcLabelService";
  String LABEL_SERVICE = "labelService";
  String LABEL_SEQUENCE_SERVICE = "labelSequenceService";
  String CC_LABEL_SERVICE = "ccLabelService";
  String KAFKA_NOT_ACCESSIBLE_ERROR_MSG = "Unable to access Kafka. Flow= %s";
  String MULTIPLE_PALLET_RECEIVING_FLOW = "multiple pallet receiving flow";
  String CONTAINER_EVENT_PUBLISH_FLOW = "container event publish flow";
  String NGR_SHIPMENT_ARRIVAL_EVENT_PUBLISH_FLOW = "ngr shipment arrival event publish flow";
  String DELIVERY_STATUS_PUBLISH_FLOW = "delivery status publish to GDM flow";
  String RECON_PUBLISH_FLOW = "recon service flow";
  String INSTRUCTION_SEARCH_REQUEST_HANDLER = "instructionSearchRequestHandler";
  String DEFAULT_INSTRUCTION_SEARCH_REQUEST_HANDLER = "DefaultInstructionSearchRequestHandler";
  String SPLIT_PALLET_INSTRUCTION_SEARCH_REQUEST_HANDLER =
      "SplitPalletInstructionSearchRequestHandler";
  String DEFAULT_CANCEL_MULTIPLE_INST_REQ_HANDLER =
      "DefaultCancelMultipleInstructionRequestHandler";
  String INSTRUCTION_PUBLISH_FLOW = "instruction service flow";
  String CATALOG_MESSAGE_PUBLISH_FLOW = "instruction service flow";
  String PACK_STATUS_RECEIVED = "RECEIVED";
  String OSS_TRANSFER = "OSSTransfer";
  String UNABLE_TO_PROCESS_NOTIFICATION_ERROR_MSG = "Unable to process Notification message = %s";

  String ROUND_ROBIN_BATCH_THRESHOLD = "roundRobinBatchThreshold";
  int PUTAWAY_MOVE_CODE = 5;
  String PUTAWAY_MOVE_DESC = "Putaway Move";
  String SLOTTING_SSTK_RECEIVING_METHOD = "SSTK";
  String CONTAINER_TAG = "CONTAINER_TAG";
  String CONTAINER_TAG_HOLD_FOR_SALE = "HOLD_FOR_SALE";
  String CONTAINER_SET = "SET";
  String PREP_TYPE_PREFIX = "IB_PREP_TYPE_";
  String ADD_ON_SERVICES = "addonServices";

  int DELIVERY_NUMBERS_MAX_PAGE_OFFSET = 100;
  String UPC = "UPC";
  String SSCC = "SSCC";
  String LPN = "LPN";
  String ASN_CUSTOM_MAPPER = "asnCustomMapper";
  String DEFAULT_ASN_CUSTOM_MAPPER = "AsnToDeliveryDocumentsCustomMapper";
  String ITEM_TAG = "itemTag";
  String Y = "Y";
  String N = "N";
  String EXPRESSION_PREFIX = "{";
  String EXPRESSION_SUFIX = "}";

  String CONTAINER_TRACKING_ID = "trackingId";

  String CONTAINER_CREATE_PROCESSOR = "containerCreateProcessor";
  Long DUMMY_INSTRUCTION_ID = 99999999L;
  String ATLAS_KAFKA_IDEMPOTENCY = "WMT-IdempotencyKey";
  String ATLAS_KAFKA_IDEMPOTENCY_FALLBACK = "WMT_IdempotencyKey";
  String DEFAULT_DELIVERY_UNLOADING_PROCESSOR = "defaultDeliveryUnloadingCompleteProcessor";
  String DELIVERY_UNLOADING_PROCESOR = "deliveryUnloadingProcessor";
  String RETRYABLE_DELIVERY_DOCUMENTS_SEARCH_HANDLER = "retryableDeliveryDocumentsSearchHandler";
  String RETRYABLE_GDM_V3_DELIVERY_DOCUMENTS_SEARCH_HANDLER =
      "retryableGdmV3DeliveryDocumentsSearchHandler";
  String IS_GDM_HAZMAT_ITEM_VALIDATION_ENABLED = "gdm.hazmat.item.validation.enabled";
  int HAZMAT_ITEM_GROUND_TRANSPORTATION = 1;
  String HAZMAT_ITEM_OTHER_REGULATED_MATERIAL = "ORM-D";
  String HAZMAT_ITEM_REGION_CODE_UN = "UN";
  String IS_IQS_INTEGRATION_ENABLED = "iqs.integration.enabled";
  String GSON_UTC_ADAPTER = "gsonUTCDateAdapter";
  String IS_IQS_ITEM_SCAN_ACTIVE_CHANNELS_ENABLED = "iqs.gdm.itemscan.activechannels.enabled";
  String ENABLE_FBQ_CHECK_IN_ROUND_ROBIN_PO_SELECTION = "enableFbqCheckInRoundRobinPoSelection";
  String ENABLE_STREAMLINED_PBYL_RECEIVING = "enableStreamlinedPbylReceiving";
  String BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR = "deliveryProgressUpdateProcessor";
  String SCHEDULER_CONFIG = "schedulerConfig";
  String PUBLISH_SINGLE_CONTAINER_AS_LIST = "publishSingleContainerAsList";
  String SYM = "SYM";
  String PUT_INBOUND = "PUT_INBOUND";
  Integer SORTER_CONTRACT_VERSION_TWO = 2;
  String LABEL_TYPE = "label_type";
  String SORTER_CONTRACT_VERSION = "sorter.contract.version";
  String CORRELATIONID = "correlationId";

  enum POStatus {
    CNCL
  }

  enum POLineStatus {
    CANCELLED
  }

  String VENDOR_PACK = "Vendor Pack";
  String STAPLESTOCK = "STAPLESTOCK";
  String BEAN_RETRYABLE_CONNECTOR = "retryableRestConnector";
  String BEAN_REST_CONNECTOR = "restConnector";
  String WORK_IN_PROGRESS = "WORK_IN_PROGRESS";
  String RECEIVED = "RECEIVED";
  String PROBLEM_OV = "OV";
  String PROBLEM_NA = "NA";
  String PROBLEM_RECEIVED = "PROBLEM_RECEIVED";
  String AVAILABLE = "AVAILABLE";
  String EACHES = "EACHES";
  String VENDORPACK = "VNPK";
  String WAREHOUSEPACK = "WHPK";
  String EACH = "EACH";
  String CURRENCY_USD = "USD";
  Long DEFAULT_DELIVERY_NUMBER = 00000000L;

  String DEFAULT_AUDIT_USER = "RCV_Default";
  String UTC_TIME_ZONE = "UTC";
  String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  String DELIVERY_SEARCH_CLIENT_TIME_FORMAT = "MM/dd/yyyy";
  String LABEL_TIMESTAMP_FORMAT = "EEE MMM d HH:mm:ss yyyy";
  String SIMPLE_DATE = "yyyy-MM-dd";
  String DC_TIMEZONE = "dcTimeZone";
  String OUTBOX_DELIVERY_EVENT_ENABLED = "isOutboxDeliveryEventEnabled";
  String OUTBOX_DELIVERY_EVENT_SERVICE_NAME = "outboxDeliveryEventServiceName";
  String OUTBOX_VENDOR_DIMENSION_EVENT_ENABLED = "isOutboxVendorDimensionsEventEnabled";
  String OUTBOX_VENDOR_DIMENSION_EVENT_SERVICE_NAME = "outboxVendorDimensionsEventServiceName";
  String HAWKEYE_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss";

  // Constants for setting time for HAWKEYE
  Integer MILLISECONDS_START_OF_DAY = 1;
  Integer MILLISECONDS_END_OF_DAY = 999;
  Integer HOUR_END_OF_DAY = 23;
  Integer MINUTE_END_OF_DAY = 59;
  Integer SECOND_END_OF_DAY = 59;
  String OVERWRITE_FACILITY_INFO = "overwriteFacilityInfo";
  String IS_RECEIPT_POSTING_DISABLED = "isReceiptPostingDisabled";

  // Constants for Item MDM
  String ITEM_MDM = "item-MDM";
  String ITEM_FOUND_SUPPLY_ITEM = "foundSupplyItems";
  String ITEM_NOT_FOUND_SUPPLY_ITEM = "notFoundSupplyItems";
  String ITEM_MDM_ITEM_NUMBER = "number";
  String ITEM_MDM_ITEM_UPC = "consumableGTIN";
  String ITEM_MDM_CASE_UPC = "orderableGTIN";
  String FTS = "fts";
  String ENABLE_SSOT_READ = "enableSSOTRead";
  String DC_PROPERTIES = "dcProperties";
  String OFFER = "offer";
  String SELLER_ID = "sellerId";
  String INVALID_ITEM_CATALOG_REQUEST =
      "Invalid request for upc update, catalogGTIN = %s, old upc = %s can't be same for item = %s.";
  String NOT_A_VALID_ITEM_CATALOG_REQUEST =
      "Invalid UPC update request to %s for catalogGTIN = %s, item = %s";
  String INVALID_ITEM_CATALOG_RESPONSE_FROM_NGR =
      "Received an invalid UPC update response from %s for catalogGTIN = %s, item = %s";

  String INVALID_PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_REQUEST =
      "Invalid request to import product category group csv file, null cells at row %s.";
  String PRODUCT_CATEGORY_GROUP_IMPORT_CSV_FILE_UNKNOWN_ERROR =
      "Unknown error during product category group csv file import";

  // Prelabel generation constants
  String INCLUDE_ACTIVE_CHANNEL_METHODS = "includeActiveChannelMethods";
  String RETRYABLE_DELIVERY_SERVICE = "retryableDeliveryService";
  String METHOD_NOT_ALLOWED = "Method Not Allowed";
  String UNABLE_TO_PARSE = "Unable to parse";
  String LPNS_NOT_FOUND = "LPNs are currently not available";
  String GENERIC_LABELING_SERVICE = "genericLabelingService";
  String PRINT_TYPE_ZEBRA = "ZEBRA";
  String PRINT_MODE_CONTINUOUS = "continuous";
  String FALLBACK_GENERATION_ERROR = "Error while generating labels in fallback for delivery %s";
  String CONTENT_ENCODING = "Content-Encoding";
  String CONTENT_ENCODING_GZIP = "gzip";

  // Constants for Witron and Hawkeye Integration
  Integer WITRON_HAWKEYE_MESSAGE_VERSION = 3;
  String WITRON_HAWKEYE_PUBLISH_PUTAWAY_ENABLED = "isWitronHawkeyePublishPutawayEnabled";
  String HAWKEYE_WITRON_PUTAWAY_PUBLISH_FLOW = "hawkeye witron putaway message publish flow";
  String INVALID_INVENTORY_STATUS_TO_ADJUST = "invalidInventoryStatusToAdjust";

  // Constants for Symbotic and Hawkeye Integration
  String HAWKEYE_KAFKA_LISTENER_CONTAINER_FACTORY = "hawkeyeKafkaListenerContainerFactory";
  String HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY =
      "hawkeyeSecureKafkaListenerContainerFactory";
  String HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_EUS =
      "hawkeyeSecureKafkaListenerContainerFactoryEUS";
  String HAWKEYE_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY_SCUS =
      "hawkeyeSecureKafkaListenerContainerFactorySCUS";
  String HAWKEYE_SYM_PUTAWAY_PUBLISH_FLOW = "hawkeye sym putaway message publish flow";
  String UNABLE_TO_PROCESS_SYM_NACK_ERROR_MSG =
      "Unable to process symbotic nack message. Error = %s";
  String WRONG_SYM_NACK_MESSAGE_FORMAT = "Message format on Sym Nack Listener is wrong {}";
  String WRONG_PUTAWAY_CONFIRMATION_MESSAGE_FORMAT =
      "Message format on Putaway Confirmation Listener is wrong {}";
  String UNABLE_TO_PROCESS_PUTAWAY_CONFIRMATION_ERROR_MSG =
      "Unable to process the putaway confirmation message. payload = %s";
  String SYM_EVENT_TYPE_KEY = "eventType";
  String KAFKA_HAWKEYE_PUTAWAY_PUBLISHER = "kafkaHawkeyePutawayMessagePublisher";
  String SYM_MESSAGE_ID_HEADER = "messageId";
  String SYM_SYSTEM_KEY = "system";

  String SYM_PUTAWAY_SYSTEM_DEFAULT_VALUE = "symbotic.putaway.default.system";
  String SYM_VERSION_KEY = "version";
  String SYM_MSG_TIMESTAMP = "msgTimestamp";
  String SYM_PUTAWAY_ORDER_NO = "PutawayOrderNo";
  String SYM_VERSION_VALUE = "2";
  String LABEL_ID_KEY = "labelId";
  String RDCSYM = "RDCSYM";
  String PRIME_SLOT_TYPE = "prime";
  String RESERVE_SLOT_TYPE = "reserve";
  String SYM_DEFAULT_STORE_ID = "0";
  Integer ZERO_QTY = 0;
  Long SYM_DEFAULT_ITEM_NUM = 999999999L;
  List<String> RDS_SYM_ELIGIBLE_INDICATOR = Arrays.asList("A", "M");
  Integer OSDR_MASTER_RECORD_VALUE = 1;
  String SYM_CASE_PACK_ASRS_VALUE = "SYM2";
  String SYM_BRKPK_ASRS_VALUE = "SYM2_5";
  String PTL_ASRS_VALUE = "MANUAL";

  // Item Config resource
  String ATLAS_CONVERTED_ITEM_SEARCH = "/api/item/search";

  interface Uom {
    String EACHES = "EA";
    String WHPK = "PH";
    String VNPK = "ZA";
    String LB = "LB";
    String DCFIN_LB_ZA = "LB/ZA";
    String CA = "CA";
    String GRAMS = "Grams";
  }

  long DUMMY_ITEM_NUMBER = -1L;
  int PO_CON_VNPK_WHPK_QTY = 1;
  int DEFAULT_PO_CASE_QUANTITY = 1;

  String OF_ERROR_MESSAGES = "messages";
  String OF_ERROR_DESC = "desc";
  String OF_ERROR_DETAILED_DESC = "detailed_desc";
  String OF_ERROR_CODE = "code";

  String PROCESS_EXPIRY_ENALBED = "processExpireEnabled";
  String FEFO_FOR_WRTC4_ENABLED = "fefoForWrtCode4Enabled"; // FEFO for warehouseRotationTypeCode 4
  String POCON_FEATURE_FLAG = "isPoconFeatureEnabled";
  String DYNAMIC_PO_FEATURE_FLAG = "isDynamicPOFeatureEnabled";
  String IS_NON_NATIONAL_INSTRUCTION_V2_ENABLED = "isNonNationalInstructionV2Enabled";
  String DSDC_FEATURE_FLAG = "isDSDCFeatureEnabled";
  String MFC_INDICATOR_FEATURE_FLAG = "isMFCIndicatorEnabled";

  String EVENT_TYPE_FINALIZED = "FINALIZED";
  String DEFAULT_USER = "rcvuser";
  String AUTO_COMPLETE_DELIVERY_USERID = "rcvscheduler";
  String MCC_DELIVERY_EVENT_PROCESSOR = "mccDeliveryEventProcessor";

  String INVENTORY_ADJUSTMENT_PROCESSOR = "inventoryAdjustmentProcessor";
  String DEFAULT_INVENTORY_ADJUSTMENT_PROCESSOR = "inventoryAdjustmentProcessor";
  String KAFKA_INVENTORY_ADJUSTMENT_PROCESSOR = "kafkaInventoryAdjustmentProcessor";
  String KAFKA_INSTRUCTION_DOWNLOAD_PROCESSOR = "kafkaInstructionDownloadProcessor";
  String RDC_KAFKA_INSTRUCTION_DOWNLOAD_PROCESSOR = "rdcKafkaInstructionDownloadProcessor";
  String DEFAULT_KAFKA_INSTRUCTION_DOWNLOAD_PROCESSOR = "defaultKafkaInstructionDownloadProcessor";

  String INVENTORY_ADJUSTMENT_PROCESSOR_V2 = "inventoryEventProcessorV2";
  String WEIGHT_FORMAT_TYPE_CHECK_FEATURE = "isWeightFormatTypeCheckEnabled";
  String RECEIVE_AS_CORRECTION_FEATURE = "isReceiveAsCorrectionEnabled";
  String MANAGER_OVERRIDE_FEATURE = "isManagerOverrideFeatureEnabled";
  String MANAGER_OVERRIDE_V2 = "isManagerOverrideV2";
  String KOTLIN_ENABLED = "isKotlinEnabled";
  String INVALID_PO_LINE_STATUS = "invalidPoLineStatus";
  String INVALID_PO_STATUS = "invalidPoStatus";

  String TRUE_STRING = "true";
  String IGNORE_EXPIRY = "ignoreExpiry";
  String IGNORE_OVERAGE = "ignoreOverage";
  String APPROVED_HACCP = "approvedHaccp";
  String HACCP = "haccp";
  String EXPIRY = "expiry";
  String OVERAGES = "overages";

  String FIXED = "fixed";
  String FIXED_WEIGHT_FORMAT_TYPE_CODE = "F";
  String VARIABLE = "variable";
  String VARIABLE_WEIGHT_FORMAT_TYPE_CODE = "V";

  Float MAX_WHITE_WOOD_PALLET_WEIGHT = 2100.0f;

  String WEIGHT_FORMAT_TYPE_CODE_MISMATCH = "WEIGHT_FORMAT_TYPE_CODE_MISMATCH";

  String DSDC_PURCHASE_REF_LEGACY_TYPE = "73";
  int DSDC_VNPK_WHPK_QTY = 1;
  int NON_NATIONAL_VNPK_WHPK_QTY = 1;
  Integer NON_NATIONAL_PALLET_QTY = 1;
  String DUMMY_PURCHASE_REF_NUMBER = "-1";
  Integer DEFAULT_PAGESIZE = 9999;
  int RDC_DA_DEFAULT_PALLET_TIE = 1;
  int RDC_DA_DEFAULT_PALLET_HIGH = 1;

  String UPDATE_INSTRUCTION_HANDLER_KEY = "updateInstructionHandler";
  String RECEIVE_INSTRUCTION_HANDLER_KEY = "receiveInstructionHandler";
  String RECEIVE_EXCEPTION_HANDLER_KEY = "receiveExceptionHandler";
  String DEFAULT_UPDATE_INSTRUCTION_HANDLER = "DefaultUpdateInstructionHandler";
  String RECEIVE_PACK_HANDLER_KEY = "receivePackHandler";
  String CC_UPDATE_INSTRUCTION_HANDLER = "ccUpdateInstructionHandler";
  String PUBLISH_CONTAINER = "isPublishContainerFeatureEnabled";

  String LOG_OSDR_DETAILS = "Osdr details [deliveryNumber={}] [osdrSummary={}]";
  String OSDR_EVENT_TYPE_KEY = "eventType";
  String OSDR_EVENT_TYPE_VALUE = "rcv_osdr";
  String POST_DELIVERY_COMPLETE_EVENT = "postDeliveryCompleteFlowEvent";

  String OSDR_SERVICE = "osdrService";

  String FROMDATE_GREATER = "FromDate Greater than ToDate";
  String MAX_DATETIMEINTERVAL = "DateTime interval can not be more than 24 hrs";
  String MANDATORY_DATE_FIELDS = "FromDate and ToDate are mandatory fields";

  // scanned data labels
  String KEY_GTIN = "gtin";
  String KEY_UNITGTIN = "unitGtin";
  String KEY_LOT = "lot";
  String KEY_LOT_NUMBER = "lotNumber";
  String KEY_SERIAL = "serial";
  String KEY_EXPIRY_DATE = "expiryDate";
  String KEY_SSCC = "sscc";
  String EXPIRY_DATE_FORMAT = "yyMMdd";
  String ORDER_DESC = "desc";

  String RX_STK = "RxSSTK";
  String EPCIS_BAD_RESPONSE_ERROR_MSG =
      "Client exception from EPCIS. HttpResponseStatus= %s ResponseBody = %s";
  String SERIALIZED_DEPT_TYPE = "38";
  String IS_DSCSA_EXEMPTION_IND = "isDscsaExemptionInd";

  String SLOTTING_RESOURCE_RESPONSE_ERROR_MSG = "Resource exception from EPCIS. Error MSG = %s";
  String SMART_SLOTTING_RESPONSE_ERROR_CODE_AND_MSG =
      "Resource exception from SMART-SLOTTING. Error Code = %s, Error Message = %s";
  String MANUAL_SLOTTING_NOT_SUPPORTED_FOR_ATLAS_ITEMS =
      "Manual Split Pallet slotting is not supported for Atlas items";
  String SMART_SLOTTING_RESPONSE_ERROR_MSG =
      "Resource exception from SMART-SLOTTING. Error Message = %s";
  String SMART_SLOT_BAD_DATA_ERROR =
      "Bad request sent while fetching slot information from Smart Slotting";
  String SMART_SLOTTING_INVALID_RESPONSE =
      "Invalid response from Smart Slotting while receiving item %s";
  String WFT_MANDATORY_FIELD = "Either trackingId or instructionId should be mandatory";

  String RECEIPT_SUMMARY_PROCESSOR = "receiptSummaryProcesor";
  String DEFAULT_RECEIPT_SUMMARY_PROCESSOR = "defaultReceiptSummaryProcessor";

  String UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER_KEY = "updateContainerQuantityRequestHandler";
  String DEFAULT_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER =
      "DefaultUpdateContainerQuantityRequestHandler";

  String COMPLETE_DELIVERY_PROCESSOR = "completeDeliveryProcessor";
  String DEFAULT_COMPLETE_DELIVERY_PROCESSOR = "DefaultCompleteDeliveryProcessor";
  String WFS_COMPLETE_DELIVERY_PROCESSOR = "WfsCompleteDeliveryProcessor";
  String MANUAL_FINALISE_DELIVERY = "manualFinalise";

  String CANCEL_CONTAINER_PROCESSOR = "cancelContainerProcessor";
  String DEFAULT_CANCEL_CONTAINER_PROCESSOR = "DefaultCancelContainerProcessor";

  String ENDGAME_OSDR_SERIVCE = "endgameOsdrService";
  String WITRON_OSDR_SERIVCE = "witronOsdrService";
  String CC_OSDR_SERIVCE = "ccOsdrService";
  String DEFAULT_OSDR_SERIVCE = "defaultOsdrService";

  String OVG = "OVG";

  List<String> validDotIdsForlithiumIon = Arrays.asList("3480", "3481", "3090", "3091");

  String RELOAD_DELIVERY_DOCUMENT_FEATURE_FLAG = "isReloadDeliveryDocumentEnabled";
  String ALLOW_SINGLE_ITEM_MULTI_PO_LINE = "isSingleItemMultiPoEnabled";

  int vendorComplianceDuration = 365;
  List<String> stateReasoncodes = Arrays.asList("DOOR_OPEN", "WORKING");

  List<String> containerExceptions = Arrays.asList("OV", "CF", "NA");

  String SLOTTING_FEATURE_TYPE = "featureType";
  String SLOTTING_FIND_SLOT = "findSlot";
  String SLOTTING_FIND_PRIME_SLOT = "findPrimeSlot";
  String SLOTTING_VALIDATE_PRIME_SLOT_FOR_SPLIT_PALLET = "validateSplitPallet";
  String SLOTTING_SPLIT_PALLET_PRIMES_COMPATIBLE_ERROR_CODE = "GLS-SMART-SLOTING-400097";
  String SLOTTING_PRIME_SLOT_NOT_FOUND = "GLS-SMART-SLOTING-4040009";
  String SLOTTING_AUTO_SLOT_NOT_AVAILABLE = "GLS-SMART-SLOTING-4040008";
  String SLOTTING_MANUAL_SLOT_NOT_AVAILABLE = "GLS-SMART-SLOTING-4040011";
  String MANUAL_SLOTTING_NOT_SUPPORTED = "GLS-SMART-SLOTING-4000103";
  String BULK_SLOTTING_DELIVERY_NOT_FOUND = "GLS-SMART-SLOTING-4090044";
  String BULK_SLOT_CAPACITY_NOT_AVAILABLE_FOR_DELIVERY = "GLS-SMART-SLOTING-4040013";
  List<String> BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY =
      Arrays.asList(
          "GLS-SMART-SLOTING-4090043", "GLS-SMART-SLOTING-4090045", "GLS-SMART-SLOTING-4090046");

  String BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_43 = "GLS-SMART-SLOTING-4090043";
  String BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_45 = "GLS-SMART-SLOTING-4090045";
  String BULK_SLOT_NOT_AVAILABLE_FOR_DELIVERY_ERROR_CODE_46 = "GLS-SMART-SLOTING-4090046";
  String DELETE_CONTAINERS_HANDLER = "deleteContainersHandler";
  String ITEM_CATALOG_NOTIFICATION_ENABLED = "itemCatalogNotificationEnabled";

  String SLOTTING_INVENTORY_AVAILABLE_FOR_DIFFERENT_ITEM_IN_PRIMESLOT = "GLS-SMART-SLOTING-4090117";

  List<String> properShipping =
      Arrays.asList(
          "Lithium Ion Battery",
          "Lithium Ion Battery Packed with Equipment",
          "Lithium Ion Battery Contained in Equipment",
          "Lithium Metal Battery",
          "Lithium Metal Battery Packed with Equipment",
          "Lithium Metal Battery Contained in Equipment");
  List<String> pkgInstruction = Arrays.asList("965", "966", "967", "968", "969", "970");
  String NOT_AVAILABLE = "N/A";
  String ORMD = "ORM-D";
  List<String> LIMITED_QTY_DOT_HAZ_CLASS = Arrays.asList("ORM-D", "LTD-Q");

  String ITEM_WAREHOUSE_PACK_GTIN = "warehousePackGtin";
  String ITEM_CATALOG_GTIN = "catalogGTIN";
  String VERSION_V1 = "v1";
  String FEATURE_PALLET_RECEIVING = "closePallet";
  String HEADER_FEATURE_TYPE = "featureType";
  String PALLET = "PALLET";
  String ATLAS = "ATLAS";
  String PALLET_CAMEL_CASE = "Pallet";
  String UNIT = "UNIT";
  String SLOTTING_GET_SLOT_URL = "/smartslotting/api/container/closedivert";
  String SLOTTING_MOVES_CANCEL_PALLET = "/smartslotting/api/instruction/cancelPallet/";
  String SLOTTING_MOVES_INSTRUCTION = "/smartslotting/api/instruction/";
  String ACCEPT_PARTIAL_RESERVATION = "acceptPartialReservation";
  String IS_PARTIAL_RESERVATION = "false";
  String STATUS_CANCELLED = "cancelled";
  String LABEL_BACKOUT = "labelBackout";
  String SUBCENTER_ID_HEADER = "subcenterId";
  String ORG_UNIT_ID_HEADER = "orgUnitId";
  String ORG_UNIT_ID_DEFAULT_VALUE = "1";
  String MISSING_ORG_UNIT_ID_CODE = "MissingOrgUnitId";
  String MISSING_ORG_UNIT_ID_DESC = "request header missing orgUnitId";
  String IS_RECEIVING_INSTRUCTS_PUT_AWAY_MOVE_TO_MM = "isReceivingInstructsPutAwayMoveToMM";

  // Slotting errors
  String SLOTTING_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  String SLOTTING_RESOURCE_NIMRDS_RESPONSE_ERROR_MSG =
      "Resource exception from Slotting. Error MSG = %s";
  String SLOTTING_QTY_CORRECTION_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  String SLOTTING_QTY_CORRECTION_RESOURCE_RESPONSE_ERROR_MSG =
      "Resource exception from Slotting. Error MSG = %s";
  String RDS_RESPONSE_ERROR_MSG = "Exception from RDS. Error MSG = %s";
  String DELIVERY_DOCUMENT_ERROR_MSG = "Delivery Document Error MSG = %s";
  String GET_RECEIPTS_ERROR_RESPONSE_IN_RDS = "Received error response: %s in RDS";
  String NO_CONTAINERS_RECEIVED_IN_RDS = "No containers received in RDS for PO %s and PO Line %s";
  String RECEIVE_CONTAINERS_RDS_ERROR_MSG =
      "No containers received in RDS for PO %s and PO Line %s; Error Message: %s";
  String RDS_PRIME_SLOT_ERROR_MSG =
      "Prime slot is not set for item %s. Please contact QA to fix this. [%s]";
  String NGR_RESPONSE_ERROR_MSG = "Exception from %s. Error MSG = %s";

  String RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR = "runAutoCompleteDeliveryInHour";
  String MAX_DELIVERY_IDLE_DURATION_IN_HOUR = "maxDeliveryIdealDurationInHour";
  String ENDGAME_DELIVERY_SERVICE = "endGameDeliveryService";
  String UNABLE_TO_GET_DELIVERY_FROM_GDM = "Unable to get Delivery information from GDM";
  // Overboxing field
  String OVERBOXING_REQUIRED = "overboxingRequired";

  String GDM_DOCUMENT_GET_BY_DELIVERY_V3 = "/api/deliveries/{deliveryNumber}";
  String GDM_DOCUMENT_GET_BY_BOX_ID = "/api/deliveries/{deliveryNumber}/shipments/{boxId}";
  String MAX_RECEIVE_QTY_RECEIVED =
      "There are open instructions for this item. Please transfer the open pallet to yourself and receive or cancel that pallet and scan again to continue.";
  String NO_LABEL_FOUND = "Labels not found for delivery: %s";
  String LPN_NOT_FOUND = "No container found for trackingId: %s";
  String DEFAULT_KAFKA_KEY = "default_kafka_key";
  String CONTAINERS_PUBLISH = "containers publishing flow";

  String LITHIUM_LABEL_CODE_3480 = "3480";
  String LITHIUM_LABEL_CODE_3481 = "3481";
  String LITHIUM_LABEL_CODE_3090 = "3090";
  String LITHIUM_LABEL_CODE_3091 = "3091";
  String PKG_INSTRUCTION_CODE_965 = "965";
  String PKG_INSTRUCTION_CODE_966 = "966";
  String PKG_INSTRUCTION_CODE_967 = "967";
  String PKG_INSTRUCTION_CODE_968 = "968";
  String PKG_INSTRUCTION_CODE_969 = "969";
  String PKG_INSTRUCTION_CODE_970 = "970";

  String RDC_RECEIVING_SOURCE_FOR_WFT = "rdc-receiving";
  String WFT_LOCATION_SCAN_PUBLISH_FLOW = "WFT location scan message publish flow";

  String FIXIT_ISSUE_TYPE_DI = "DI";
  String INSTRUCTION_NOT_FOUND = "Instruction not found for id: %s";

  String INVALID_INPUT_INSTRUCTION_IDS = "Invalid input instruction-id list.";
  String INVALID_INPUT_USERID = "Invalid input UserId.";

  String HACCP_ENABLED = "isHACCPEnabled";
  String CHECK_QUANTITY_MATCH_ENABLED = "isCheckQuantityMatchEnabled";
  String CHECK_QUANTITY_MATCH_BY_GLS_BASELINE_ENABLED = "isCheckQuantityByGlsPOs";
  String CHECK_PO_CONFIRMED_ENABLED = "isCheckPoConfirmedEnabled";
  String ZERO_QTY_OSDR_MASTER_ENABLED = "isZeroQtyOsdrMasterEnabled";
  String SPLIT_PALLET = "SplitPallet";
  String INVALID_RUNTIME_STATUS = "Invalid value provided for Runtime Status field";
  String INSTR_CREATE_SPLIT_PALLET_NOT_BREAK_PACK_ITEM =
      "Only breakpack items can be added to split pallets. Validate the pack type for this item.";
  String INSTR_CREATE_SPLIT_PALLET_SYMBOTIC_BREAK_PACK_ITEM =
      "Symbotic eligible items cannot be added to a split pallet.";
  String INSTR_CREATE_SPLIT_PALLET_PRIMES_COMPATIBLE =
      "Primes must be in the same level and module.";

  String COMPLETE_MULTIPLE_INSTR_REQ_HANDLER = "completeMultipleInstructionRequestHandler";
  String DEFAULT_COMPLETE_MULTIPLE_INSTR_REQ_HANDLER =
      "DefaultCompleteMultipleInstructionRequestHandler";
  String RDC_COMPLETE_MULTIPLE_INST_REQ_HANDLER = "RdcCompleteMultipleInstructionRequestHandler";

  String NIM_RDS_MULTI_LABEL_GENERIC_ERROR = "Error occurred while fetching label from Nim-RDS.";
  String INVALID_INSTRUCTION_STATE =
      "One or more Instructions in this request is either cancelled/completed. Please reload.";
  String IS_GDC_CANCEL_INSTRUCTION_ERROR_ENABLED = "isGdcCancelInstructionErrorEnabled";
  String REQUEST_TRANSFTER_INSTR_ERROR_CODE = "GLS-RCV-MULTI-INST-400";
  String MULTI_INSTR_ERROR_CODE = "GLS-RCV-MULTI-SPLIT-INST-400";
  String MULTI_INSTR_PROBLEM_ERROR_CODE = "GLS-RCV-MULTI-OPEN-INST-PROBLEM-400";

  String ERROR_DELIVERY_UPDATE_YMS = "GLS-RCV-YMS-500";

  // label reprint constants
  String INVALID_LPN = "invalidLPN";
  String JSON_STRING = "json";
  String LABEL_ID_PROCESSOR = "labelIdProcessor";
  String CC_LABEL_ID_PROCESSOR = "ccLabelIdProcessor";
  String RDC_LABEL_ID_PROCESSOR = "rdcLabelIdProcessor";
  String WITRON_LABEL_ID_PROCESSOR = "witronLabelIdProcessor";
  String RX_LABEL_ID_PROCESSOR = "rxLabelIdProcessor";
  String DEFAULT_LABEL_ID_PROCESSOR = "defaultLabelIdProcessor";
  String CC_DA_NON_CON_LABEL_DATA_PROCESSOR = "ccDaNonConLabelDataProcessor";
  String CC_DA_CON_PALLET_LABEL_DATA_PROCESSOR = "ccDaConPalletLabelDataProcessor";
  String CC_DA_CON_CASE_LABEL_DATA_PROCESSOR = "ccDaConCaseLabelDataProcessor";
  String CC_SSTK_LABEL_DATA_PROCESSOR = "ccSstkLabelDataProcessor";
  String CC_PBYL_LABEL_DATA_PROCESSOR = "ccPbylLabelDataProcessor";
  String CC_NON_NATIONAL_LABEL_DATA_PROCESSOR = "ccNonNationalLabelDataProcessor";
  String CC_DOCKTAG_LABEL_DATA_PROCESSOR = "ccDocktagLabelDataProcessor";
  String CC_ACL_LABEL_DATA_PROCESSOR = "ccAclLabelDataProcessor";
  String WITRON_LABEL_DATA_PROCESSOR = "witronLabelDataProcessor";
  String RX_LABEL_DATA_PROCESSOR = "rxLabelDataProcessor";
  String RDC_LABEL_DATA_PROCESSOR = "rdcLabelDataProcessor";
  String DEFAULT_LABEL_DATA_PROCESSOR = "defaultLabelDataProcessor";
  String WFS_LABEL_DATA_PROCESSOR = "wfsLabelDataProcessor";
  String DEFAULT_KAFKA_INVENTORY_EVENT_PROCESSOR = "defaultKafkaInventoryEventProcessor";
  String DEFAULT_VERSION = "1";
  String MSG_TIME_STAMP = "msgTimestamp";
  String API_VERSION = "API_VERSION";
  String API_VERSION_VALUE = "v2";
  String APPLICATION_INTENT_READ_ONLY = "applicationIntent=ReadOnly";
  String SEMI_COLON = ";";
  // Item Update Service
  String ITEM_UPDATE_SERVICE_URI = "/services/v3/indigo";
  String ITEM_UPDATE_SERVICE_EVENT_TYPE = "ITEM_DC_ATTRIBUTE_UPDATE";
  String ITEM_UPDATE_SERVICE_NODE_TYPE = "RDC";
  String ITEM_UPDATE_REQUEST_SRC = "atlas_update";
  String ITEM_UPDATE_SERVICE_ERROR = "Error returned while updating item";
  String ITEM_UPDATE_SERVICE_RESPONSE_ERROR_MSG =
      "Resource exception from item update service. Error MSG = %s";
  String UNABLE_TO_UPDATE_ITEM_DETAILS_IN_RDS = "Unable to update item details in RDS";
  String ERROR_WHILE_UPDATING_ITEM_PROPERTIES_IN_RDS =
      "Error while updating item properties in RDS.";
  String IQS_ITEM_UPDATE_ENABLED = "item.update.feature.enabled";
  String IQS_ITEM_UPSERT_ENABLED = "iqs.item.upsert.enabled";
  String PROVIDER_ID = "RDC-RCV";
  String IQS_CONSUMER_ID_KEY = "wm_consumer.id";
  String IQS_CORRELATION_ID_KEY = "wm_qos.correlation_id";
  String IQS_SVC_ENV_KEY = "wm_svc.env";
  String IQS_SVC_KEY = "wm_svc.name";
  String IQS_SVC_CHANNEL_TYPE_KEY = "wm_consumer.channel.type";
  String IQS_SVC_VALUE = "PartnerDataReceiver";
  String IQS_ITEM_UPDATE_TEST_ENABLED = "isItemUpdateTestEnabled";
  Integer IQS_TEST_FACILITY_NUMBER = 6020;
  String IQS_COUNTRY_CODE = "country_code";

  String GET_CONTAINER_REQUEST_HANDLER = "getContainerRequestHandler";
  String DEFAULT_GET_CONTAINER_REQUEST_HANDLER = "DefaultGetContainerRequestHandler";
  String IS_ATLAS_CONVERTED_ITEM = "isAtlasConvertedItem";

  String TO_SUBCENTER = "toSubcenter";
  String FROM_SUBCENTER = "fromSubcenter";
  String TO_ORG_UNIT_ID = "toOrgUnitId";
  String FROM_ORG_UNIT_ID = "fromOrgUnitId";
  String IS_RECEIVE_FROM_OSS = "isReceiveFromOSS";
  String PO_TYPE = "poType";
  String ELIGIBLE_TRANSFER_POS_CCM_CONFIG = "eligibleTransferPOs";
  String DEFAULT_ELIGIBLE_TRANSFER_PO_TYPE = "28";
  String SECONDARY_QTY_UOM = "secondaryQtyUom";
  String ENTIRE_DC_ATLAS_CONVERTED = "EntireDcAtlasConverted";
  String ONE_ATLAS_CONVERTED_ITEM = "oneAtlasConvertedItem";
  String ONE_ATLAS_NOT_CONVERTED_ITEM = "oneAtlasNotConvertedItem";
  String SLOT = "slot";
  String SLOT_TYPE = "slotType";
  String PRIME_SLOT_ID = "slotId";

  String DEFAULT_ERROR_MESSAGE =
      "We are unable to process the request at this time. This may be due to a system issue. Please try again or contact your supervisor if this continues.";

  String DEFAULT_DOCUMENTS_SEARCH_HANDLER = "defaultDeliveryDocumentsSearchHandler";
  String DELIVERY_DOCUMENT_SEARCH_HANDLER = "deliveryDocumentSearchHandler";

  String CONVERT_QUANTITY_TO_EACHES_FLAG = "isUpdateQtyToEachEnabled";
  String MIXED_ITEM_TYPES_IN_SPLIT_PALLET_CREATION =
      "Item No: %s cannot be added to this split pallet. Atlas and Non-atlas items cannot be mixed on a split pallet";
  String MIXED_PO_NUMBERS_NOT_ALLOWED_IN_SPLIT_PALLET =
      "Item No: %s cannot be added to this split pallet. Multiple PO items cannot be mixed on a split pallet";
  String INVALID_ITEM_HANDLING_METHOD =
      "Looks like item %s has an invalid handling method or pack type. Have the QA team resolve this before receiving";
  String GET_ZONETEMPERATURE_V3 = "/api/deliveries/{deliveryNumber}/trailer-temperature";
  Integer DEFAULT_TRAILER_TEMP_ZONES = 3;

  String SORTER_PUBLISHER = "sorterPublisher";
  String ENABLE_CF_SORTER_DIVERT = "enableChannelFlipSorterDivert";
  String ENABLE_NA_SORTER_DIVERT = "enableNoAllocationSorterDivert";
  String ENABLE_STORE_LABEL_SORTER_DIVERT = "enableStoreLabelSorterDivert";
  String ENABLE_OFFLINE_DACON_STORE_LABEL_SORTER_DIVERT = "enableDAConStoreLabelSorterDivert";
  String ENABLE_XBLOCK_SORTER_DIVERT = "enableXBlockSorterDivert";
  String ENABLE_NO_DELIVERY_DOC_SORTER_DIVERT = "enableNoDeliveryDocSorterDivert";
  String WMT_FLIB_LOCATION_HEADER_KEY = "WMT-Flib-Location";
  String MANIFEST_IMPLEMENTATION_TITLE = "Implementation-Title";
  String MANIFEST_IMPLEMENTATION_VERSION = "Implementation-Version";
  String JMS_SORTER_PUBLISHER = "jmsSorterPublisher";

  String EVENT_DESTINATION_SWAP = "DESTINATION_SWAP";

  String INVALID_MESSAGE_TYPE =
      "Missing Mandatory Message type header information, please send the required message type header details";
  String UNSUPPORTED_MESSAGE_TYPE = "Unsupported message type";
  String INVALID_SYM_SYSTEM =
      "Missing Mandatory Symbotic System header information, please send the required Symbotic system header details";
  String INVALID_PUTAWAY_REQUEST = "Invalid Putaway request";
  String PARTIAL_PUTAWAY_REQUEST =
      "Mandatory putaway request parameters are missing in the payload";

  // WFS Constants
  String PUT_FULFILLMENT_TYPE = "PUT";
  String PBYL_FULFILLMENT_TYPE = "PBYL";
  String WFS_CHANNEL_METHOD = "WFS";
  String ENABLE_AUTO_DELIVERY_OPEN = "enableAutoDeliveryOpen";
  String WFS_LABEL_TIMESTAMP = "LABELTIMESTAMP";
  String WFS_LABELLING_POSTING_ENABLED = "wfsLabellingPostingEnabled";
  String WFS_TM_HAZMAT_CHECK_ENABLED = "wfsTmHazmatCheckEnabled";
  String IS_CONTAINER_STATUS_VALIDATION_ENABLED_VTR = "enableContainerStatusValidationInVtr";
  String INVALID_INVENTORY_STATUS_FOR_GET_BY_TRACKING_ID =
      "invalidInventoryStatusForGetByTrackingId";
  String IS_CONTAINER_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT =
      "enableContainerStatusValidationInReEngageDecant";
  String IS_DELIVERY_STATUS_VALIDATION_ENABLED_REENGAGE_DECANT =
      "enableDeliveryStatusValidationInReEngageDecant";
  String VENDOR_VALIDATOR = "vendorValidator";

  String SSCC_SCAN_RECEIVING_NOT_ALLOWED = "Receiving freight by SSCC is not supported.";
  String IS_AUTO_POPULATE_RECEIVE_QTY_ENABLED = "autoPopulateReceiveQtyEnabled";
  String DEFAULT_DOOR = "999";

  String MULTI_SKU_PALLET = "Scanned label is multi item Pallet. Sort and receive by each Item";
  String MULTI_SKU_INST_CODE = "AsnMultiSkuPallet";
  String MULTI_SKU_INST_MSG = "Build Pallet for MultiSku";

  String ASN_MULTISKU_HANDLER = "asnMultiSkuHandler";
  String DEFAULT_MULTI_SKU_HANDLER = "DefaultMultiSkuHandler";

  String ORIGIN_TYPE = "originType";
  String PRO_DATE = "proDate";
  String ORIGIN_FACILITY_NUMBER = "originFacilityNum";
  String ORIGIN_FACILITY_COUNTRY_CODE = "originFacilityCountryCode";
  List<String> IMPORTS_PO_TYPES = Arrays.asList("40", "42");
  String PURCHASE_REF_LEGACY_TYPE = "purchaseReferenceLegacyType";
  String PURCHASE_REF_TYPE = "purchaseRefType";
  String TRAILER_NUMBER = "trailerNumber";
  String IS_AUDITED = "isAudited";

  String AUTO_DOCK_TAG_COMPLETE_HOURS = "autoDockTagCompleteHours";
  Integer DEFAULT_AUTO_DOCK_TAG_COMPLETE_HOURS = 72;

  String AUTO_CANCEL_INSTRUCTION_MINUTES = "autoCancelInstructionMinutes";
  Integer DEFAULT_AUTO_CANCEL_INSTRUCTION_MINUTES = 30;

  String PRODATE_NOT_FOUND_IN_PO =
      "Missing mandatory parameter proDate in delivery document for instruction: %s";

  String CONTAINER_TRANSFORMER_BEAN = "containerTransformer";
  String RX_CONTAINER_TRANSFORMER_BEAN = "rxContainerTransformer";

  // NEW Fields add as part GDC ASN Receiving
  String IS_HACCP = "isHACCP";
  String DC_WEIGHT_FORMAT_TYPE_CODE = "dcWeightFormatTypeCode";
  String OMS_WEIGHT_FORMAT_TYPE_CODE = "omsWeightFormatTypeCode";
  String PROFILED_WARE_HOUSE_AREA = "profiledWarehouseArea";
  String WARE_HOUSE_MIN_LIFE_REMAINING_TO_RECEIVE = "warehouseMinLifeRemainingToReceive";
  String WARE_HOUSE_ROTATION_TYPE_CODE = "warehouseRotationTypeCode";
  String WEIGHT_FORMAT_TYPE_CODE = "weightFormatTypeCode";
  String WARE_HOUSE_AREA_CODE = "warehouseAreaCode";
  String WARE_HOUSE_GROUP_CODE = "warehouseGroupCode";
  String PROMO_BUY_IND = "promoBuyInd";
  String WAREHOUSE_AREA_DESC = "warehouseAreaDesc";
  String WEIGHT = "weight";
  String WEIGHT_UOM = "weightUOM";
  String EACH_WEIGHT = "eachWeight";
  String EACH_WEIGHT_UOM = "eachWeightUOM";
  String CUBE = "cube";
  String SOURCE = "source";
  String DECANT_API = "decant-api";
  String WHITE_WOOD_MAX_WEIGHT_KEY = "whiteWoodPalletMaxWeight";
  String IS_AUTO_SELECT_LINE_DISABLED = "isAutoSelectLineDisabled";
  String MANUAL_PO_SELECTION = "MANUAL_PO_SELECTION";
  String IS_MANUAL_PO_SELECTION_CODE_ENABLED = "isManualPoSelectionCodeEnabled";
  String SPLUNK_ALERT = "Splunk Alert. ";

  String MAP_WEIGHT_FROM_GLS = "mapWeightFromGLS";
  String PACK_TYPE_CODE = "packTypeCode";
  String HANDLING_CODE = "handlingCode";
  String TEMPORARY_PACK_TYPE_CODE = "temporaryPackTypeCode";
  String TEMPORARY_HANDLING_METHOD_CODE = "temporaryHandlingMethodCode";
  String IS_DCFIN_API_DISABLED = "isDCFinApiDisabled";
  String PALLET_ALREADY_RECEIVED = "Pallet %s with delivery number %d has already been received.";
  String FEATURE_NOT_IMPLEMENTED_ERROR_MESSAGE = "Feature Not Implemented.";

  // Constants for resource bundle

  String MESSAGES = "messages";
  String LABEL_FORMAT = "labelFormat";
  String HEADER_MESSAGES = "headerMessages";

  String IS_INVENTORY_API_DISABLED = "isInventoryApiDisabled";
  String IS_GLS_API_ENABLED = "isGLSApiEnabled";
  String IS_FNL_CHECK_ENABLED = "isFnlCheckEnabled";
  String BLOCK_RECEIVING_ON_GLS_QTY_MISMATCH = "blockReceivingOnGlsQtyMismatch";
  String BLOCK_RECEIVING_ON_GLS_DOWN = "blockReceivingOnGlsDown";
  String VTR = "VTR";
  String RECEIVING_CORRECTION = "RCV-CORRECTION";
  String PUBLISH_TO_WITRON_DISABLED = "publishToWitronDisabled";
  String PUBLISH_CANCEL_MOVE_ENABLED = "publishCancelMoveEnabled";
  String PUBLISH_TO_DCFIN_ADJUSTMENTS_ENABLED = "publishToDcFinAdjustsEnabled";
  String EXCEPTION_COUNT = "_EXCEPTION_COUNT";
  String ALPHA_NUMERIC_REGEX_PATTERN = "^[a-zA-Z0-9]*$";
  String ATLAS_LPN_REGEX_PATTERN = "^[a-zA-Z].*$";
  String GLS_LPN_REGEX_PATTERN = "^[0-9].*\\-[a-zA-Z0-9_]*$";
  String NGR_SITE_ID_PATTERN = "\\.s|\\.us";
  String PO_QTY_MIS_MATCH_ERROR = "PO %s's Receipts Quantity not matching with Container";
  String RETRYABLE_FDE_SERVICE = "retryableFdeService";
  String DISABLE_DOCK_TAG_CONTAINER_PUBLISH = "disableDockTagContainerPublish";
  String WFS_INVALID_LABEL_FOR_CORRECTION_INV_NOT_FOUND_ERROR_MSG =
      "The label you scanned was not found and is not eligible for correction.";
  String WFS_INVALID_LABEL_FOR_CORRECTION_INV_CONTAINER_STATUS_ERROR_MSG =
      "The label you scanned was not in a correct status and is not eligible for correction.";
  String WFS_INVALID_LABEL_FOR_RESTART_DECANT_INV_CONTAINER_STATUS_ERROR_MSG =
      "The label you scanned was not in a correct status and is not eligible for restarting decant.";
  String WFS_INVALID_LABEL_FOR_RESTART_DECANT_INV_STATUS_ERROR_MSG =
      "The label you scanned is in {} status. Only induct LPNs are supported for restarting decant.";
  String WFS_INVALID_INDUCT_LPN_DESTINATION_CTR_ERROR_MSG =
      "Please scan valid induct LPN. This is a destination container.";
  String WFS_INVALID_LABEL_FOR_RESTART_DECANT_INV_NOT_FOUND_ERROR_MSG =
      "The label you scanned was not found and is not eligible for restarting decant.";

  // Regulated Item Validation
  String REGULATED_ITEM_VALIDATION = "regulatedItemValidation";
  String DOCKTAG_ALREADY_COMPLETED_OLD_ERROR_MSG =
      "DockTag: %s has already been completed by user: %s";
  String PARTIAL_DOCKTAG_CREATION_ERROR_MSG =
      "Error printing a new docktag, existing docktag '%s' completed. Please retry printing a new docktag for delivery %s. If issue persists, contact support.";
  String DOCKTAG_ALREADY_COMPLETED_ERROR_MSG =
      "Error: Dock tag '%s' has already been completed by user %s at timestamp %s";
  String INVENTORY_SERVICE_NOT_AVAILABLE_ERROR_MSG =
      "Error: Docktag '%s' could not be completed. Inventory service is not available. Please contact support.";
  String FLOORLINE_ITEM_COLLISION_SUCCESS_RESPONSE_ENABLED =
      "enableFloorlineItemCollisionSuccessResponse";
  String ITEM_COLLISION_INSTRUCTION_CODE = "ITEM_COLLISION";
  String ITEM_COLLISION_INSTRUCTION_MESSAGE =
      "There are items [%s] on delivery %s already being received at a location on this conveyor. Please receive this delivery at different conveyor or manually.";
  String DELIVERY_TYPE_CODE = "deliveryTypeCode";
  String FREIGHT_TYPE = "freightType";

  // RDC DA label backout errors
  String INVALID_DA_LBL_BACKOUT_REQUEST = "Invalid DA backout label request.";
  String NIM_RDS_SERVICE_UNAVAILABLE_ERROR = "NIM RDS service is unavailable.";
  String ITEM_CONFIG_SERVICE_ENABLED = "item.config.service.enabled";
  String ITEM_CONFIG_SERVICE_NO_RETRY_ENABLED = "ItemConfigServiceNoRetryEnabled";

  String AUTO_CASE_RECEIVE_FEATURE_TYPE = "AUTO_CASE_RECEIVE";
  String SCAN_TO_PRINT_INSTRUCTION_CODE = "ScanToPrint";
  String IS_DC_ONE_ATLAS_ENABLED = "isDCOneAtlasEnabled";
  String IS_RECEIVE_ALL_ENABLED = "isReceiveAllEnabled";
  String MAX_ALLOWED_STORAGE_DATE = "maxAllowedStorageDate";
  String MAX_ALLOWED_STORAGE_DAYS = "maxAllowedStorageDays";
  String SAFEGUARD_MAX_ALLOWED_STORAGE = "safeguardMaxAllowedStorage";
  String IS_INVENTORY_FROM_GLS_ENABLED = "isInventoryFromGLSEnabled";
  String IS_MANUAL_GDC_ENABLED = "isManualGdcEnabled";
  String ACTION_TYPE = "actionType";
  String CREATE_CORRECTION_EVENT = "createCorrectionEvent";
  String UPDATE_CORRECTION_EVENT = "updateCorrectionEvent";
  String EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES = "EventRunAfterThresholdTimeMinutes";
  String CORRECTION_CONTAINER_EVENT_PROCESSOR = "CorrectionContainerEventProcessor";

  // for trusted seller, wfs
  String GS1_BARCODE = "gs1Barcode";
  String EXPECTED_PACK_QUANTITY = "expectedPackQuantity";
  String ITEM_BARCODE_VALUE = "itemBarCodeValue";

  // XBlock Handler
  String[] X_BLOCK_ITEM_HANDLING_CODES = {"X", "R"};
  String X_BLOCK_ITEM_ERROR_MSG =
      "Item %s is showing as blocked and cannot be received. Please contact the QA team on how to proceed.";
  String ITEM_X_BLOCKED_VAL = "itemXBlockedVal";
  String ITEM_VARIABLE_WEIGHT_CHECK_ENABLED = "isItemVariableWeightCheckEnabled";

  String IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP = "isDeliveryUpdateStatusEnabledByHttp";
  String CONTROLLED_SUBSTANCE_ERROR =
      "The scanned item %s is a controlled substance. Quarantine this item";

  String CONTAINER_TO_BE_AUDITED = "TO_BE_AUDITED";

  String PUTAWAY_TO_PRIME = "PUTAWAY_TO_PRIME";
  String PRIME = "Prime";
  String ADJUST_HI_ENABLED = "isAdjustHiEnabled"; // adjust Hi based on actual quantity
  String INV_V2_ENABLED = "invV2Enabled";
  String NULL = null;
  String SHOW_EXPIRY_DATE_PRINT_LABEL = "showExpiryDateOnPrintLabel";
  String SHOW_LOT_NUMBER_PRINT_LABEL = "showLotNumberOnPrintLabel";
  String IS_EXPIRY_DATE_VALIDATION_ENABLED = "isExpiryDateValidationEnabled";
  String DCFIN_POCLOSURE_STATE_URI = "/poClose/state/{poNumber}/{deliveryNumber}";
  String CHECK_DCFIN_PO_STATUS_ENABLED = "isCheckDCFinPOStatus";

  String MAPPED_DECANT_STATION = "mapped_decant_station";
  String LOCATION_SUBTYPE_INDUCT_SLOT = "Induct Slot";
  String LOCATION_TYPE_SLOT = "Slot";

  public static final String ZA = "ZA";
  public static final String EA = "EA";
  public static final Integer ONE = 1;
  public static final String CA = "CA";
  String IS_GDM_SHIPMENT_V4_ENABLED = "is.gdm.shipment.api.v4.enabled";
  String DEFAULT_PALLET_AUDIT_COUNT = "default.pallet.audit.count";
  String DEFAULT_CASE_AUDIT_COUNT = "default.case.audit.count";
  String IS_EPCIS_ENABLED_VENDOR = "isEpcisEnabledVendor";
  String ENABLE_ATLAS_INVENTORY_TEST = "isRxAtlasInventoryTestEnabled";
  String IS_EVENT_TYPE_HEADER_ENABLED = "is.event.type.header.enabled";

  // outbox policy ids
  String OUTBOX_POLICY_KAFKA_MOVES = "URN::ATLAS::RECEIVING::KafkaEventPublisher::201";
  String OUTBOX_POLICY_HTTP_PENDING_CONTAINERS = "URN::ATLAS::RECEIVING::HttpEventPublisher::100";
  String OUTBOX_POLICY_HTTP_EACHES_DETAIL = "URN::ATLAS::RECEIVING::HttpEventPublisher::101";
  String OUTBOX_POLICY_HTTP_PS_SERVICES = "URN::ATLAS::RECEIVING::HttpEventPublisher::102";
  String OUTBOX_POLICY_KAFKA_INVENTORY = "URN::ATLAS::RECEIVING::KafkaEventPublisher::200";
  String OUTBOX_POLICY_HTTP_PS_SERVICES_CAPTUREMANY =
      "URN::ATLAS::RECEIVING::HttpEventPublisher::103";
  String OUTBOX_POLICY_HTTP_PS_SERVICES_V3_CAPTUREMANY =
      "URN::ATLAS::RECEIVING::HttpEventPublisher::104";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_INVENTORY = "urn:us:wm:amb:rcv:publisher:kafka:1";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_PUTAWAY_HAWKEYE = "urn:us:wm:amb:rcv:publisher:kafka:2";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_SORTER = "urn:us:wm:amb:rcv:publisher:kafka:3";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_WFT = "urn:us:wm:amb:rcv:publisher:kafka:4";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_EI_DC_RECEIVE_EVENT = "urn:us:wm:amb:rcv:publisher:kafka:5";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_EI_DC_PICK_EVENT = "urn:us:wm:amb:rcv:publisher:kafka:6";
  String OUTBOX_KAFKA_PUBLISHER_POLICY_EI_DC_VOID_EVENT = "urn:us:wm:amb:rcv:publisher:kafka:7";
  String OUTBOX_KAFKA_PUBLISHER_HAWKEYE_LABEL_DATA = "urn:us:wm:amb:rcv:publisher:kafka:8";

  String USE_DEFAULTS_FOR_DAMAGES = "useDefaultsForDamages";
  int VDM_REASON = 53;

  int RCS_CONCEALED_SHORTAGE_REASON = 54;

  int RCO_CONCEALED_OVERAGE_REASON = 55;

  String INVENTORY_ADJUSTMENT_EVENT_DATA = "eventData";

  String INVENTORY_UPDATED_ITEMS = "updatedItems";
  String ITEM_CHANGE_LIST = "itemChangeList";
  String INVENTORY_PAYLOAD = "payload";
  String INVENTORY_QUANTITY_CHANGE_AND_ITEM_STATE = "quantityChangeAndItemState";

  String INVENTORY_QUANTITY_CHANGE = "quantityChange";

  String INVENTORY_QTYUOM = "qtyUOM";

  String INVENTORY_DELTAQTY = "deltaQty";
  String OUTBOX_PATTERN_ENABLED = "isOutboxPatternEnabled";
  String INVENTORY_LINES = "inventoryLines";
  String INVENTORY_AVAILABLE_TO_SELL = "availableToSellQty";

  String SPLIT_PALLET_TRANSFER = "SPLIT_PALLET_TRANSFER";

  String INVENTORY_CONTAINER_TYPE = "containerType";
  String INVENTORY_LABEL_TYPE = "labelType";
  String OP_FULFILLMENT_METHOD = "fulfillmentMethod";
  String STORE_PICK_BATCH = "pickBatch";
  String STORE_PRINT_BATCH = "printBatch";
  String STORE_AISLE = "aisle";
  String DEST_TYPE = "destType";
  String INVENTORY_CONTAINER_TYPE_RCID = "RCID";
  String INVENTORY_TARGET_CONTAINER = "targetContainer";
  String GDC_KAFKA_INVENTORY_EVENT_PROCESSOR = "gdcKafkaInventoryEventProcessor";

  // Receiving Correction rules - operational restrictions into system
  String GDC_UPDATE_CONTAINER_QUANTITY_REQUEST_HANDLER = "GdcUpdateContainerQuantityRequestHandler";
  String GDM_GET_DELIVERY_HISTORY = "/api/v1/deliveries/{deliveryNumber}/history";
  String ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED = "allowedDaysForCorrectionAfterFinalized";
  String DEFAULT_ALLOWED_DAYS_FOR_CORRECTION_AFTER_FINALIZED = "30";
  String EVENT_TYPE_BILL_SIGNED = "Bill Signed";
  /**
   * CCM value should be PutawayCompleted else if empty then disable feature. This should have only
   * one value and not a list
   */
  String GET_MOVES_URL = "/v0/getMoves";
  /**
   * CCM value should be WORKING else if empty then disable feature. This should have only one value
   * and not a list
   */
  String INVALID_MOVE_STATUS_CORRECTION = "invalidMoveStatusForCorrection"; //

  String INVALID_INVENTORY_STATUS_CORRECTION = "invalidInventoryStatusForCorrection";
  String INVALID_MOVE_TYPE_AND_STATUS_CORRECTION = "invalidMoveTypeAndStatusForCorrection";
  String VALID_MOVE_STATUS_VTR = "validMoveStatusForVtr";

  String ENFORCE_MOVES_CHECK_FOR_VTR = "enforceMovesCheckForVtr"; // true/false
  String ENFORCE_INVENTORY_CHECK_FOR_VTR = "enforceInventoryCheckForVtr"; // true/false

  /**
   * CCM value can be haulPENDING,haulOPEN,putawayPENDING,putawayOPEN else if empty then disable
   * feature. This can have list of strings with move(Type and status)
   */
  String VALID_MOVE_TYPE_AND_STATUS_VTR = "validMoveTypeAndStatusForVtr";

  String MOVE_STATUS = "status";
  String INVALID_MOVE_TYPE_AND_STATUS_AFTER_BILL_SIGNED = "invalidMoveTypeAndStatusAfterBillSigned";
  String ALLOCATED_QTY = "allocatedQty";
  String INVALID_MOVE_IF_BILL_NOT_SIGNED = "invalidMoveIfBillNotSigned"; // PutawayCompleted
  String CONTAINER_IDS = "containerIds";
  String KAFKA_MOVE_PUBLISHER = "kafkaMovePublisher";
  String MOVE_PUBLISH_FLOW = "Move info flow";

  String DOCUMENT_ID = "documentId";
  String EPCIS = "EPCIS";
  String RECEIVED_STATUS = "2";
  String BACKOUT_STATUS = "3";
  String DECOMISSIONED_STATUS = "4";
  String RETURNED_TO_VENDOR_STATUS = "5";
  String GDM_UPDATE_STATUS_URL = "/api/shipments/status";
  String GDM_UPDATE_STATUS_V2_URL = "/api/shipments/v2/status";
  String DOCUMENT_PACK_ID = "documentPackId";
  String Rx_ASN_RCV_OVER_RIDE_KEY = "Wmt-Asn-Rcv-Override";
  String Rx_ASN_RCV_OVER_RIDE_VALUE = "true";

  String MANUAL_FINALISE_EVENT_LISTENER = "manualFinalizationEventListener";
  String MANUAL_FINALISE_PROCESSOR = "ManualFinalizationProcessor";
  String STORE_BULK_RECEIVING_PROCESSOR = "storeBulkReceivingProcessor";

  String STORE_PALLET_INCLUDED = "includeStorePallet";
  String MFC_PALLET_INCLUDED = "includeMFCPallet";

  String PALLET_RELATIONS = "palletRelations";
  String STACKED_CONTAINERS = "stackedContainers";

  // hawkshaw configs
  String HAWKSHAW_HEADER_ERROR = "Unable to get hawkshaw header for key = %s";
  String KAFKA_PUBLISH_ERROR_MSG = "Unable to publish Kafka for topic = %s";
  String HAWKSHAW_ENRICHER_BEAN = "hawkshawEnricher";
  String HAWKSHAW_HEADER = "audit";
  String HAWKSHAW_ENABLED = "isHawkshawEnabled";
  String KAFKA_HAWKSHAW_PUBLISHER = "kafkaHawkshawPublisher";
  String HAWKSHAW_SEGMENT = "wm";
  String HAWKSHAW_URN_SOURCE = "urn:wm:source:";
  String INVALID_DSDC_RECEIVE_REQUEST = "Invalid DSDC pack receive request.";
  String DSDC_RECEIVE_ERROR_RDS = "Unable to receive DSDC Pack: %s in RDS.";
  String INVALID_MOVE_TYPE_STATUS_AFTER_BILL_SIGNED = "invalidMoveTypeAfterBillSigned";

  String INVALID_MOVE_TYPE_AND_STATUS_MIX_FOR_VTR = "invalidMoveTypeAndStatusMixForVtr";
  String GDM_GET_PURCHASE_ORDER = "/api/purchase-orders/";
  String IS_KAFKA_RECEIPTS_ENABLED = "isKafkaReceiptsEnabled";
  String IS_KAFKA_RECEIPTS_DC_FIN_VALIDATE_ENABLED = "isKafkaReceiptsDcFinValidateEnabled";
  String KAFKA_PUBLISHER = "kafkaPublisher";
  String KAFKA_RECEIPT_UPDATES_PUBLISH_ENABLED = "isKafkaReceiptUpdatesPublishEnabled";
  String KAFKA_UNABLE_TO_SEND_ERROR_MSG = "Unable to send message through Kafka.";

  String INVENTORY_CONTAINER_CREATE_URI = "/inventory/inventories/receipt/v2";
  String SLOTTING_CANCEL_PALLET_MOVE_URL = "/smartslotting/api/moves/status";
  String INVENTORY_CREATE_CONTAINERS_REQUEST_MSG =
      "[Response Inventory call to post containers={} statusCode={}]";
  String INVENTORY_CREATE_CONTAINERS_ERROR_MSG =
      "[Unexpected error while calling Inventory for containers={} error={}]";
  String INVENTORY_CREATE_CONTAINERS_OUTBOX_ERROR_MSG =
          "[Unexpected error while saving inventory receipts to outbox for containers={} error={}]";
  String INVENTORY_CREATE_CONTAINERS_ERROR_RESP_MSG =
      "[Error accessing Inventory for containers={}, response={}, error={}]";
  String TIHI_WEIGHT_THRESHOLD_POUNDS = "tihiWeightThresholdPounds";
  String TIHI_WEIGHT_THRESHOLD_VALIDATION_ENABLED = "tihiWeightThresholdValidationEnabled";
  String INVALID_WEIGHT_UOM_ERROR_MESSAGE = "Invalid Weight UOM: {}";

  String ORIGIN_COUNTRY_CODE = "originCountryCode";

  String ORIGIN_COUNTRY_CODE_RESPONSE = "isOriginCountryCodeAcknowledged";

  String PACK_TYPE_RESPONSE = "isPackTypeAcknowledged";

  String SAMS_MDM_ITEM_SEARCH_PATH = "/items/sams";

  String IS_VALIDATE_ORIGIN_COUNTRY_CODE_ACK_ENABLED = "isOriginCountyCodeAcknowledgeEnabled";

  String IS_VALIDATE_PACK_TYPE_ACK_ENABLED = "isPackTypeAcknowledgeEnabled";
  String ORIGIN_COUNTRY_CODE_CONDITIONAL_ACK = "isOriginCountryCodeConditionalAcknowledged";
  String GLS_RCV_ITEM_SAVE_CONFIRMATION_DESCRIPTION =
      "COO on pack does not match with system. Please contact support to proceed";
  String OCC_FIELDS_MISSING = "originCountryCode Can not be null";
  String PACK_FIELDS_MISSING = "VnpkQty or whpkQty Can not be Empty";
  String WEIGHT_THRESHOLD_EXCEEDED_ERROR_MESSAGE =
      "Pallet will be over the weight limit. To continue, enter {} cases or less and finish this pallet.";

  String PACK_ID = "packId";
  String GDM_PALLET_PACK_SCAN_URI = "/api/deliveries/{deliveryNumber}/shipments/{identifier}";
  String INCLUDE_PACK_ID = "includePackId";
  String INCLUDE_CROSS_REFERENCES = "includeCrossReferences";

  String NO_RECEIPTS_FOUND_IN_RDS_FOR_PO_AND_POL = "NIMRDS-023";
  String PO_LINE_IS_CANCELLED_IN_RDS = "NIMRDS-025";
  String INCLUDE_DISTRIBUTIONS = "includeDistributions";
  String PURCHASE_REF_TYPE_DA = "DA";
  String PURCHASE_REF_TYPE_SSTK = "SSTK";
  String PURCHASE_REF_TYPE_DSDC = "DSDC";
  String ENABLE_LINE_LEVEL_FBQ_CHECK = "enableLinefbqCheckForMaxReceivedQty";
  String LINE_LEVEL_FBQ_CHECK_ERROR_MESSAGE =
      "Line level FBQ is not present for the DeliveryNbr {}";

  String KAFKA_EXCEPTION_CONTAINER_PUBLISHER = "kafkaExceptionContainerPublisher";

  String EXCEPTION_CONTAINER_PUBLISHER = "exceptionContainerPublisher";

  String JMS_EXCEPTION_CONTAINER_PUBLISHER = "jmsExceptionContainerPublisher";

  String TWENTY_FIVE_CONSTANT = "25";
  String FIFTY_CONSTANT = "50";
  String SEVENTY_FIVE_CONSTANT = "75";
  String HUNDRED_CONSTANT = "100";
  String UNLOAD_PROGRESS = "UNLOAD_PROGRESS";
  String VERSION_1_0 = "1.0";
  String SOURCE_ID = "sourceId";
  String EVENT_TIMESTAMP = "eventTimestamp";
  String DC_NUMBER = "dcNumber";
  String IS_RECEIVING_PROGRESS_PUB_ENABLED = "isReceivingProgressPubEnabled";
  String INV_BOH_QTY_URL = "/api/item/boh/unified";

  String IS_STORE_AUTO_INITIALIZATION_ENABLED = "isStoreAutoInitializationEnabled";
  String PROBLEM_CREATION_ON_UNLOAD_FOR = "problemCreationOnUnloadFor";
  String DELAYED_DOCUMENT_INGEST_EARLY_STOCKED_ENABLED = "delayedDocumentIngestEarlyStockedEnabled";
  String SCALING_QTY_ENABLED_FOR_REPLENISHMENT_TYPES = "scalingQtyEnabledForReplenishmentTypes";

  String BEYOND_THRESHOLD_DATE_WARN_ERROR_MSG_1 =
      "The date (%s) entered is beyond the number Max of (%s) days allowed for this item. To continue, confirm the Date or press Cancel to correct the date Entered";
  String BEYOND_THRESHOLD_DATE_WARN_ERROR_MSG_2 =
      "The date (%s) entered is beyond todays date, this item requires a pack date Prior to today";

  String QTY = "qty";
  String BOH_DISTRIBUTION = "bohDistribution";
  String CONTAINER_ITEM_AGGREGATED_LIST = "containerItemAggregatedList";
  String CONTAINER_LEVEL = "containerLevel";
  String BOH = "boh";
  String CONTAINER_ITEM = "CONTAINER_ITEM";
  String SUBCENTER_ID = "SUBCENTER_ID";
  String BASE_DIV_CODE = "BASE_DIV_CODE";
  String FINANCIAL_REPORTING_GROUP = "FINANCIAL_REPORTING_GROUP";
  String IGNORE_INVENTORY = "ignoreInventory";

  String OCC_NOT_FOUND_ERROR_MESSAGE = "OriginCountryCode attribute is missing";
  String OCC_AND_PACK_SIZE_NOT_FOUND_ERROR_MESSAGE =
      "OriginCountryCode and VNPK/WHPK attributes are missing";
  String PACK_SIZE_NOT_FOUND_ERROR_MESSAGE = "VNPK/WHPK attribute is missing";
  String IS_OCC_ACK = "isOriginCountryCodeAcknowledged";
  String IS_OCC_CONDITIONAL_ACK = "isOriginCountryCodeConditionalAcknowledged";
  String IS_PACK_ACK = "isPackTypeAcknowledged";
  String GLS_RCV_ITEM_SAVE_COO_PACK_CONFIRMATION_DESCRIPTION =
      "Country of Origin & WHPK/VNPK on pack does not match with system. Please contact support to proceed.";
  String GLS_RCV_ITEM_SAVE_COO_CONFIRMATION_DESCRIPTION =
      "Country of Origin on pack does not match with system. Please contact support to proceed.";
  String GLS_RCV_ITEM_SAVE_PACK_CONFIRMATION_DESCRIPTION =
      "WHPK/VNPK on pack does not match with system. Please contact support to proceed.";

  String ORG_UNIT_ID = "ORG_UNIT_ID";
  String TRACKING_ID = "TRACKING_ID";
  String GDC_DELIVERY_UNLOADER_PROCESSOR = "gdcDeliveryUnloaderProcessor";

  String DELIVERY_UNLOADER_PROCESOR = "deliveryUnloaderProcessor";

  String DEFAULT_DELIVERY_UNLOADER_PROCESSOR = "defaultDeliveryUnloaderProcessor";

  String VALID_UNLOADER_EVENT_TYPES = "validUnloaderEventTypes";

  String OPEN_QTY_CALCULATOR = "openQtyCalculator";
  String DEFAULT_OPEN_QTY_CALCULATOR = "defaultOpenQtyCalculator";
  String DELIVERY_DOCUMENT_SELECTOR = "deliveryDocumentSelector";
  String DEFAULT_DELIVERY_DOCUMENT_SELECTOR = "defaultDeliveryDocumentSelector";

  // constants for Firefly event
  String IS_TEMP_COMPLIANCE = "isTempCompliance";
  String CONTAINER_CREATE_TS = "containerCreateTs";
  String DATE_FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
  String CSM_DATE_FORMAT = "dd MMM yyyy HH:mm:ss.SSSZ";

  // Constants for item update events
  String ITEM_HANDLING_CODE_UPDATE_ENABLED = "isItemHandlingCodeUpdateEnabled";
  String ITEM_CATALOG_UPDATE_ENABLED = "isItemCatalogUpdateEnabled";
  String ITEM_UNDO_CATALOG_ENABLED = "isItemUndoCatalogEnabled";
  String ITEM_CHANNEL_FLIP_ENABLED = "isItemChannelFlipEnabled";
  String UNABLE_TO_PROCESS_ITEM_UPDATE_ERROR_MSG =
      "Unable to process the item update message . payload = %s";
  String ITEM_UPDATE_PROCESSOR = "itemUpdateProcessor";
  String ITEM_UPDATE_INSTRUCTION_PUBLISHER = "itemUpdateInstructionPublisher";
  String KAFKA_ITEM_UPDATE_INSTRUCTION_PUBLISHER = "kafkaItemUpdateInstructionPublisher";
  String PUBLISH_ON_HAWKEYE_CATALOG_TOPIC = "isPublishOnHawkeyeCatalogTopicEnabled";
  String EMPTY_CATALOG_GTIN = "00000000000000";

  String IS_STORAGE_CHECK_ENABLED = "isStorageCheckEnabled";
  String IS_MULTI_USER_RECEIVE_ENABLED = "isMultiUserReceiveEnabled";

  String IS_OVERAGE_CHECK_IN_PROBLEM_RECEIVING_ENABLED = "isOverageCheckInProblemReceivingEnabled";

  // constants for robo depal
  String DEFAULT_SKU_INDICATOR = "SINGLE";
  Integer DEFAULT_DELIVERY_PRIORITY = 3;
  String DOCK_TAG_CREATED_EVENT = "DOCK_TAG_CREATED";
  String ROBO_DEPAL_FEATURE_ENABLED = "isRoboDepalFeatureEnabled";
  String ROBO_DEPAL_PARENT_FLOORLINES = "roboDepalParentFloorlines";
  String DOCKTAG_INFO_PUBLISHER = "dockTagInfoPublisher";
  String KAFKA_DOCKTAG_INFO_PUBLISHER = "kafkaDockTagInfoPublisher";
  String DOCKTAG_EVENT_TIMESTAMP = "eventTimestamp";
  String WRONG_ROBO_DEPAL_LISTENER_MESSAGE_FORMAT =
      "Message format on Robo Depal Container Status Change Listener is wrong. message: {}";
  String UNABLE_TO_PROCESS_ROBO_DEPAL_EVENT_ERROR_MSG =
      "Unable to process Robo Depal event message: %s. Error: %s";

  String IS_MECH_CONTAINER = "isMechContainer";
  String AUTOMATION_TYPE = "automationType";
  String AUTOMATION_TYPE_DEMATIC = "DEMATIC";
  String AUTOMATION_TYPE_SWISSLOG = "SWISSLOG";
  String AUTOMATION_TYPE_SCHAEFER = "SCHAEFER";

  String FACILITY_NUM = "facilitynum";

  String IS_RECEIPT_AGGREGATOR_CHECK_ENABLED = "isReceiptsAggregatorCheckEnabled";

  public static final String RC_MISSING_RETURN_INITIATED = "RC_MISSING_RETURN_INITIATED";
  public static final String FLOW = "FLOW";

  public static final String TENANT_ID = "tenant_id";
  public static final String SCAN_MODE = "SCAN_MODE";
  public static final String RESCAN = "RESCAN";
  public static final String RECEIPT = "RECEIPT";
  String UNABLE_TO_FETCH_DELIVERY_DETAILS =
      "Issue in fetching delivery details from GDM, Request payload = {} , Headers = {} ";
  String FAILED_ATTACH_PO_EXCEPTION =
      "Failed to attach PO to delivery [PO numbers={}] [Delivery Number={}] [Exception={}]";
  String START_ATTACHING_PO =
      "Trying to attach POs to delivery [PO numbers={}] [Delivery Number={}] ";
  String GDM_FETCH_PO_DETAILS_ISSUE =
      "Issue in fetching PO details from GDM [Purchase Order={}] [Exception={}]";
  String OMS_FETCH_PO_DETAILS_ISSUE =
      "Issue in fetching PO details from OMS [Purchase Order={}] [Exception={}]";
  String OMS_PO_DETAILS_WRONG_NODE =
      "OMS dcnbr not matching facilitynum for poNumber [facilitynum={}] [ponumber={}]";
  String CODE = "code";
  String MODE = "Mode";
  String REPLEN = "Replen";

  String RECEIVE_REJECTION = "RECEIVE_REJECTION";
  String RECEIVE_VTR = "RECEIVE_VTR";
  String RECEIVE_TI_HI_UPDATES = "RECEIVE_TIHI_UPDATES";
  String SEND_UPDATE_EVENTS_TO_GDM = "sendUpdateEventsToGDM";
  String USER_ROLE = "userRole";
  String ENABLE_ACTIVITY_NAME_FROM_RECEIVING = "enableActivityNameFromReceiving";

  String ON_CONVEYOR_FLAG = "setOnConveyorFlag";

  public static final String IS_INVENTORY_VALID_ITEM_CHECK_ENABLED =
      "isInventoryValidItemCheckEnabled";
  String ENABLE_PUBLISH_UNLOAD_PROGRESS_AT_DELIVERY_COMPLETE =
      "enablePublishUnloadProgressAtDeliveryComplete";
  String DEFAULT_YMS2_UNLOAD_EVENT_PROCESSOR = "DefaultYms2UnloadEventProcessor";
  String DEFAULT_DOOR_NUM = "999";
  String CREATE_DELIVERY_COMPLETE_EVENT = "createDeliveryCompleteEvent";
  String AUTO_DELIVERY_COMPLETE_EVENT_PROCESSOR = "AutoDeliveryCompleteEventProcessor";

  // constants for robo depal
  Integer DEFAULT_PRIORITY = 3;
  String DOCK_TAG_CREATED = "DOCK_TAG_CREATED";

  String SHIPMENT_FINANCE_PROCESSOR = "shipmentFinanceProcessor";
  String MANUAL_FINALIZATION_PROCESSOR = "manualFinalizationProcessor";
  String SHIPMENT_MANUAL_FINALIZATION = "MANUAL_FINALIZATION";
  String EVENT_MANUAL_FINALIZATION = "WRK";

  String REPLEN_CASE = "REPLEN_CASE";
  String BOX_ID = "boxId";

  String SCH_FACILITY_COUNTRY_CODE = "wmt_sch_country";
  String SCH_FACILITY_NUMBER = "wmt_sch_node";
  String STORE_NGR_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY =
      "StoreNGRSecureKafkaListenerContainerFactory";

  String WRONG_NGR_FINALIZATION_LISTENER_MESSAGE_FORMAT =
      "Message format on NGR Finalization Listener is wrong. Message: {}";

  String UNABLE_TO_PROCESS_NGR_FINALIZATION_EVENT_MSG =
      "Unable to process the ngr finalization event message . payload = %s";
  String CREATE_DELIVERY_UNLOAD_COMPLETE_EVENT = "createDeliveryUnloadCompleteEvent";
  String DELIVERY_UNLOAD_COMPLETE_CREATE_EVENT_PROCESSOR =
      "DeliveryUnloadCompleteCreateEventProcessor";
  String UNABLE_TO_GET_SHIPMENT_FROM_GDM = "Unable to get Shipment information from GDM";

  String SHIPMENT_NOT_ATTACHED_TO_DELIVERY = "Shipment is not attached in delivery = %s";
  String ERROR_ACCESSING_INVENTORY = "Error accessing Inventory";
  String INVALID_DATA_WHILE_CALLING_INVENTORY = "Unexpected data error while calling Inventory";

  String ORIGIN_TS = "origin_ts";

  String LABEL_DATA_LPN_INSERTION_ENABLED = "isLabelDataLpnInsertionEnabled";
  String LABEL_DATA_LPN_QUERY_ENABLED = "isLabelDataLpnQueryEnabled";

  String IS_RE_RECEIVING_CONTAINER = "isReReceivingContainer";
  String TWO_TIER = "TWO_TIER";
  String GDM_LPN_LIST_URI = "/api/upc/{upcNumber}";
  String IS_RE_RECEIVING_LPN_FLOW = "isReReceivingLPNFlow";
  String RECEIVING_STATUS = "receivingStatus";
  String RECEIVING_STATUS_OPEN = "Open";

  String RECEIVING_STATUS_RECEIVED = "Received";
  String RECEIVING_ERROR_INVALID_RECEIVING_STATUS =
      "Error: The re-receiving container %s is already received. Please scan another container!";
  String RE_RECEIVING_SHIPMENT_NUMBER = "reReceivingShipmentNumber";

  String DISABLE_PRINTING_MASTER_PALLET_LPN = "disablePrintingMasterPalletLPN";

  String FORMAT_NAME = "formatName";
  String PRINT_REQUESTS = "printRequests";
  String PRINT_DISABLED_LABEL_FORMAT = "printDisabledLabelFormat";
  String PALLET_LABEL_PRINTING_DISABLED =
      "Requested label for reprint is pallet label and printing pallet label is disabled.";

  String DELIVERY_NOT_RECEIVABLE_ERROR_MESSAGE =
      "Delivery %s can not be received as the status is in %s in GDM .Please contact your supervisor.";
  String DELIVERY_NOT_RECEIVABLE_REOPEN_ERROR_MESSAGE =
      "Delivery is in PNDFNL or FNL state. To continue receiving, please reopen the delivery %s from GDM and retry.";

  String INSTRUCTION_DOWNLOAD_BLOB_SERVICE_CLIENT = "instructionDownloadBlobServiceClient";
  String REST_TEMPLATE_WITH_ENCODING_DISABLED = "restTemplateWithEncodingDisabled";

  String IS_ATLAS_ITEM = "isAtlasItem";
  String LABEL_TYPE_STORE = "STORE";
  List<String> SSTK_CHANNEL_METHODS_FOR_RDC = Arrays.asList("SSTKU", "SINGLE", "STAPLESTOCK");
  List<String> DA_CHANNEL_METHODS_FOR_RDC =
      Arrays.asList("CROSSU", "CROSSMU", "MULTI", "CROSSDOCK", "CROSSNA", "CROSSNMA", "CROSSMA");
  int CARTON_TAG_STORE_NUMBER_START_POSITION = 0;
  int CARTON_TAG_DIVISION_NUMBER_START_POSITION = 5;
  int CARTON_TAG_DIVISION_NUMBER_END_POSITION = 7;
  int CARTON_TAG_END_POSITION = 18;
  int LPN_LENGTH_18 = 18;
  String IS_OUTBOX_PATTERN_ENABLED = "is.outbox.pattern.enabled";
  String IS_RDS_RECEIVING_BLOCKED = "is.rds.receiving.blocked";
  String IS_OUTBOX_PATTERN_ENABLED_FOR_DSDC = "is.outbox.pattern.enabled.dsdc";
  String IS_AUTOMATION_OUTBOX_PATTERN_ENABLED = "is.automation.outbox.pattern.enabled";
  String IS_DSDC_REPRINT_BLOCKED_FOR_ALREADY_RECEIVED_SSCC =
      "is.dsdc.reprint.blocked.for.already.received.sscc";
  int LPN_LENGTH_25 = 25;

  // EI
  String DC_PICKS = "dc_picks";
  String DC_RECEIVING = "dc_receiving";
  String DC_VOID = "dc_void";
  String DC_SHIP_VOID = "dc_ship_void";
  String DC_TRUE_OUT = "dc_true_out";
  String DC_XDK_VOID = "dc_xdk_void";
  String[] EI_DC_RECEIVING_AND_PICK_EVENTS = {DC_RECEIVING, DC_PICKS};
  String[] EI_DC_RECEIVING_EVENT = {DC_RECEIVING};
  String[] EI_DC_PICKED_EVENT = {DC_PICKS};
  String[] EI_DC_VOID_EVENT = {DC_VOID};
  String EI_DR_EVENT = "DR";
  String EI_DP_EVENT = "DP";
  String EI_DV_EVENT = "DV";
  String TURN_INV_TYPE_IND = "TURN";
  String VNPK_WGT_FORMAT_CODE = "F";
  int AVG_CASE_WGT_QTY = 0;
  String DIST_CHANNEL_TYPE = "DIST";
  String EI_KAFKA_PRODUCER = "eiKafkaProducer";
  String EI_KAFKA_TEMPLATE = "eiKafkaTemplate";
  int STORE_NUMBER_MAX_LENGTH = 5;
  String PURCHASE_COMPANY_ID_PREFIX = "100";
  String PURCHASE_COMPANY_ID = "1";
  String ENABLE_EI_KAFKA = "${is.ei.kafka.enabled:false}";

  // Tick Tick
  String HOP_ID = "hopId";
  String ATLAS_RECEIVING_HOP_ID = "atlas-receiving";
  String TICK_TICK_TRACKING_ID = "trackingId";
  String EVENT_ID = "eventId";
  String CUSTOM_FIELDS = "customFields";
  String IS_TICK_TICK_INTEGRATION_ENABLED = "is.tick.tick.integration.enabled";
  String EI_COUNTRY_CODE = "country_code";
  String EI_STORE_NUMBER = "store_number";
  String EI_EVENT_TYPE = "event_type";
  String DC_PICK_EVENT_CODE = "DP";
  String DC_RECEIVING_EVENT_CODE = "DR";
  String DC_VOIDS_EVENT_CODE = "DV";
  String CHANNEL_TYPE = "channelType";

  // for exception receiving
  String RESOURCE_MIRAGE_RESPONSE_ERROR_MSG = "Resource exception from mirage. Error MSG = %s";
  String MIRAGE_INVALID_BARCODE_ERROR_MSG =
      "Invalid barcode scanned, please scan the correct barcode";
  String DSDC_AUDIT_LABEL = "DSDC_AUDIT_LABEL";
  String HAZMAT = "HAZMAT";
  String LITHIUM = "LITHIUM";
  String LIMITED_ITEM = "LIMITED_ITEM";
  String OVERAGE = "OVERAGE";
  String BREAKOUT = "BREAKOUT";
  String INVALID_REQUEST = "INVALID_REQUEST";
  String NO_DATA_ASSOCIATED = "NO_DATA_ASSOCIATED";
  String SYSTEM_ERROR = "SYSTEM_ERROR";
  String SSTK_INELIGIBLE = "SSTK";
  String RCV_ERROR = "RCV_ERROR";
  String SSTK_ATLAS_ITEM = "SSTK_ATLAS_ITEM";
  String LPN_RECEIVED_SSTK = "LPN_RECEIVED_SSTK";
  String ERROR_INVALID_BARCODE = "ERROR_INVALID_BARCODE";
  String EXCEPTION_LPN_NOT_FOUND = "EXCEPTION_LPN_NOT_FOUND";
  String ERROR_LPN_NOT_FOUND = "ERROR_LPN_NOT_FOUND";
  String EXCEPTION_LPN_RECEIVED = "EXCEPTION_LPN_RECEIVED";
  String LPN_NOT_RECEIVED = "LPN_NOT_RECEIVED";
  String LPN_NOT_RECEIVED_SSTK = "LPN_NOT_RECEIVED_SSTK";
  String ERROR_LPN_BACKOUT = "ERROR_LPN_BACKOUT";

  // Config Flag for Exception Receiving Flow
  String IGNORE_EXCEPTION_MESSAGE_CHECK_FOR_OVERAGE = "ignoreExceptionMessageCheckForOverage";

  String RECEIVING_API_INVOKER = "RECEIVING";

  String NONCON = "NONCON";
  String X_BLOCK = "X_BLOCK";
  String ACL_EXCEPTION = "/aclexception/lpn";
  String VOID_LPN = "/labels/received";
  String MATCH_FOUND = "MATCH_FOUND";
  String NO_BARCODE_SEEN = "NO_BARCODE_SEEN";
  String UPC_NOT_FOUND = "UPC_NOT_FOUND";
  String GDM_FETCH_DELIVERY_DOC_BY_ITEM_AND_DELIVERY_URI =
      "/api/deliveries/{deliveryNumber}/item-search?includeActiveChannelMethods=false&items={itemNumber}";
  String IS_ATLAS_EXCEPTION_RECEIVING = "isAtlasExceptionReceiving";

  String HAWKEYE_GET_DELIVERIES_URI = "/v2/get/delivery-history";
  String LOCATION_ID = "locationId";
  String DELIVERY_SEARCH_PARAM_FROM_DATE = "fromDate";
  String DELIVERY_SEARCH_PARAM_TO_DATE = "toDate";
  String HAWKEYE_RECEIVE_ERROR_DESCRIPTION =
      "Unable to get delivery information. Error accessing Hawkeye";
  String HAWKEYE_RECEIVE_NO_DELIVERY_FOUND_DESCRIPTION =
      "No deliveries found for the search criteria {}";
  String HAWKEYE_RECEIVE_INVALID_INPUT_DESCRIPTION = "Invalid Input parameter {}";
  String UPC_MATCH_NOT_FOUND = "UPC %s match not found";
  String INVALID_UPC = "UPC number cannot be null";
  String MIRAGE_UNAVAILABLE_ERROR =
      "Unable to reach receiving mirage. Please try again in some time";
  String ALLOWED_AUTOMATION_MANUALLY_ENTERED_SLOTS_FOR_DA_CONVEYABLE_ITEM =
      "allowedAutomationManuallyEnteredSlotsForDAConveyableItem";
  String AUTOMATION_AUTO_SLOT_FOR_DA_CONVEYABLE_ITEM = "automationAutoSlotForDAConveyableItem";

  // For automation
  String WRONG_COMPLETION_MESSAGE_FORMAT =
      "Message format on Completion confirmation Listener is wrong {}";
  String COMPLETED_STATUS = "COMPLETED";
  String UNABLE_TO_PROCESS_COMPLETION_MESSAGE_ERROR_MSG =
      "Unable to process the symbotic completion message. payload = %s";
  String TENENT_GROUP_TYPE = "WMT_Group_Type";
  String RDC_VERIFICATION_GROUPTYPE = "RCV";
  String FLIB_USER = "flibUsr";

  String RDC_VERIFICATION_MESSAGE_LISTENER = "RDC_VERIFICATION_MESSAGE_LISTENER";
  String KAFKA_VERIFICATION_EVENT_PROCESSOR = "kafkaVerificationEventProcessor";
  String RDC_KAFKA_VERIFICATION_EVENT_PROCESSOR = "rdcKafkaVerificationEventProcessor";
  String LABEL_GROUP_UPDATE_COMPLETED_EVENT_LISTENER = "labelGroupUpdateCompletedEventListener";
  String KAFKA_LABEL_GROUP_UPDATE_COMPLETED_EVENT_PROCESSOR =
      "kafkaLabelGroupUpdateCompletedEventProcessor";
  String RDC_KAFKA_LABEL_GROUP_UPDATE_COMPLETED_EVENT_PROCESSOR =
      "rdcKafkaLabelGroupUpdateCompletedEventProcessor";

  String IS_BREAKPACK_VALIDATION_DISABLED = "is.breakpack.item.validation.disabled";
  String IS_SSTK_VALIDATION_DISABLED = "is.sstk.item.validation.disabled";
  String IS_RESTRICTED_ITEM_VALIDATION_DISABLED = "is.restricted.item.validation.disabled";
  String IS_HAZAMT_ITEM_VALIDATION_DISABLED = "is.hazmat.validation.disabled";
  String IS_LIMITED_QTY_VALIDATION_DISABLED = "is.limited.qty.validation.disabled";
  String IS_LITHIUM_ION_VALIDATION_DISABLED = "is.lithium.ion.validation.disabled";
  String IS_NONCON_VALIDATION_DISABLED = "is.noncon.validation.disabled";
  String IS_MASTER_PACK_ITEM_VALIDATION_DISABLED = "is.masterpack.item.validation.disabled";

  String LABEL_UPDATE_URI = "/label/update";
  String LABEL_UPDATE_INVALID_REQUEST_DESCRIPTION = "Invalid Label Update Request";
  String LABEL_UPDATE_CONFLICT_DESCRIPTION = "lpn not found";
  String LABEL_UPDATE_INTERNAL_SERVER_ERROR_DESCRIPTION = "lpn/update Internal service failure";
  String SYSTEM_BUSY = "System is currently busy processing requests. Please try after some time";

  String WRONG_VERIFICATION_MESSAGE_FORMAT =
      "Message format on Verification Message Listener is wrong {}";

  String HAWK_EYE_LABEL_DATA_EVENT_TYPE_HEADER_VALUE = "LABEL_DATA";

  String LABEL_DATA_VERSION = "1";
  String ENABLE_LABEL_DATA_COMPRESSION = "enableLabelDataCompression";
  String WMT_MSG_TIMESTAMP = "WMT-msgTimestamp";
  String RDC_MESSAGE_TYPE_NORMAL = "NORMAL";
  String RDC_MESSAGE_TYPE_BYPASS = "BYPASS";
  String RDC_MESSAGE_TYPE_UNKNOWN = "UNKNOWN";

  String HAWKEYE_ITEM_UPDATE_URI = "/label/item/update";
  String HAWKEYE_ITEM_UPDATE_FAILED = "Item update failed for request {}";

  String HAWKEYE_LABEL_READINESS_URL = "/label/group/readiness";
  String GROUP_NBR = "groupNbr";
  String GROUP_TYPE = "groupType";

  String LABEL_READINESS_INVALID_REQUEST_DESCRIPTION = "Invalid Request";
  String LABEL_READINESS_CONFLICT_DESCRIPTION =
      "Cannot link delivery because delivery %s is still linked in SYM HMI. Please unlink delivery %s from SYM HMI and try again";
  String LABEL_READINESS_INTERNAL_SERVER_ERROR_DESCRIPTION = "Internal Service Failure";

  String LABEL_DOWNLOAD_EVENT_SERVICE = "labelDownloadEventService";
  String UNABLE_TO_PROCESS_VERIFICATION_EVENT_ERROR_MSG =
      "Unable to process the verification event message. payload = %s";
  String RDC_DELIVERY_EVENT_PROCESSOR = "rdcDeliveryEventProcessor";
  String RDC_SSTK_LABEL_GENERATION_ENABLED = "rdcSstkLabelGenerationEnabled";
  String RDC_DELIVERY_EVENT_TYPE_CONFIG_ENABLED = "rdcDeliveryEventTypeConfigEnabled";

  String YMS_UNLOADING_PROGRESS_ON_DELIVERY_COMPLETE = "enableUnloadingAtDeliveryComplete";
  String WMT_FACILITY_COUNTRY_CODE = "WMT-facilityCountryCode";
  String WMT_FACILITY_NUM = "WMT-facilityNum";
  String DELIVERY_EVENT_PERSISTER_SERVICE = "deliveryEventPersisterService";
  String HAWKEYE_FETCH_LPNS_URI = "/label/manual-receiving";
  String HAWKEYE_FETCH_LPNS_FAILED = "Fetch lpns failed for request {}";
  String HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND_DESCRIPTION = "No Delivery or Item found for request {}";
  String HAWKEYE_NO_DELIVERY_OR_ITEM_FOUND = "No Delivery or Item found";
  String HAWKEYE_LABEL_GENERATION_IN_PROGRESS_RETRY_AGAIN =
      "Label generation in progress, please try again";
  String HAWKEYE_LABELS_UNAVAILABLE = "Labels not available, please contact support";
  String HAWKEYE_LABEL_GROUP_UPDATE_URL = "/label/group/{deliveryNumber}";
  String HAWKEYE_LABEL_GROUP_UPDATE_FAILED = "Delivery linking failed for request {}";
  String HAWKEYE_LABEL_GROUP_UPDATE_CONFLICT = "Group update failed ! Label count didn't match";
  String START_STATUS = "START";

  String IS_THREE_SCAN_DOCKTAG_ENABLED_FOR_SSCC = "is.three.scan.docktag.enabled.for.sscc";
  int SSCC_LENGTH_20 = 20;
  String DSDC_IDENTIFIER = "00";
  String DOOR = "DOOR";

  String ITEM_CATALOG_CHECK_DIGIT_ENABLED = "item.catalog.check.digit.enabled";

  String ITEM_CATALOG_CHECK_DIGIT_ERROR_MESSAGE = "Scanned barcode is not a valid UPC";

  // constants for Firefly event
  String WRONG_FIREFLY_MESSAGE_FORMAT = "Message format on Firefly Listener is wrong - {}";
  String UNABLE_TO_PROCESS_FIREFLY_EVENT_MSG =
      "Unable to process the firefly event message . payload = %s";
  String FIREFLY_SECURE_KAFKA_LISTENER_CONTAINER_FACTORY =
      "fireflySecureKafkaListenerContainerFactory";
  String USER_ID_AUTO_FINALIZED = "AutoFinalized";
  String STORE_APP_METRICS = "storeAppMetrics";
  String IS_RECEIVED_THROUGH_AUTOMATED_SIGNAL = "isReceivedThroughAutomatedSignal";
  String IS_DSDC_SSCC_PACKS_AVAILABLE_IN_GDM = "is.dsdc.sscc.packs.available.in.gdm";
  String IS_ATLAS_DSDC_RECEIVING_ENABLED_FOR_ALL_VENDORS =
      "is.atlas.dsdc.receiving.enabled.for.all.vendors";
  Integer DSDC_PO_TYPE_CODE = 73;
  String DSDC_CHANNEL_METHODS_FOR_RDC = "DSDC";

  // Constants for Offline Receiving
  String IS_PUTAWAY_ENABLED_FOR_BREAKPACK_ITEMS = "isPutawayEnabledForBreakPackItems";
  String IS_SYM_CUTOVER_COMPLETED = "is.symbotic.cut.over.completed";

  String CROSSDOCK = "Crossdock";

  String XDK1 = "XDK1";

  String XDK2 = "XDK2";

  String AUDIT_LOG_PROCESSOR = "auditLogProcessor";
  String DEFAULT_AUDIT_LOG_PROCESSOR = "defaultAuditLogProcessor";
  // cancel container outbox flag
  String IS_CANCEL_CONTAINER_OUTBOX_PATTERN_ENABLED = "is.cancel.container.outbox.pattern.enabled";

  String ATLAS_COMPLETE_MIGRATED_DC_LIST = "atlas.complete.migrated.sites";

  String ATLAS_ADD_ITEM = "/api/item";

  String ATLAS_ALL_FREIGHT_ENABLED = "is.atlas.allfrieght.enabled";

  String ATLAS_FREIGHT_MIGRATED_TYPES = "atlas.complete.migrated.freightTypes";

  String INVENTORY_BULK_CONTAINERS_URL =
      "/inventory/inventories/containers/bulk/search?details=true&allocation_details=false";
  String PRINT_MISSING_LPNS = "missingLpns";
  String APQ_ID = "Apq_ID"; // requiredField
  String IQS_DEFAULT_API_PATH = "/itemQuery/service/apq";
  String SIZE_INCHES = "sizeInches";
  String REPRINT_LABEL_HANDLER_KEY = "reprintLabelHandler";
  String RDC_REPRINT_LABEL_HANDLER = "RdcReprintLabelHandler";
  String DSDC_ASN = "DSDC_ASN";
  String WORK_STATION_SSCC = "WORK_STATION_SSCC";
  String SCAN_TO_PRINT_SSCC = "SCAN_TO_PRINT_SSCC";
  String PO_EVENT = "poEvent";
  String IS_RAPID_RELAYER_CALL_BACK_ENABLED_FOR_DSDC_RECEIVING =
      "is.rapid.relayer.call.back.enabled.for.dsdc.container";
  int DEFAULT_TRANSACTION_TIME_OUT_IN_SECONDS = 60;

  // Constants for item update events
  String ITEM_CONVEYABILITY_CHANGE_ENABLED = "isItemConveyabilityChangeEnabled";
  String WRONG_ITEM_UPDATE_MESSAGE_FORMAT = "Message format on ItemUpdate Listener is wrong - {}";
  String HANDLING_CODE_UPDATE_ENABLED = "isHandlingCodeUpdateEnabled";
  String IS_ALL_VENDORS_ENABLED_IN_GDM = "is.all.vendors.enabled.in.gdm";
  String GDM_SSCC_SCAN_NOT_FOUND_ERROR_MESSAGE = "ASN details not available in GDM";
  String GDM_SSCC_SCAN_ASN_NOT_FOUND = "NOT FOUND";

  String MFC = "MFC";
  Integer MFC_MESSAGE_CODE = 56;

  String ORDER_WELL_STORAGE_ZONE_DISTRIBUTION_URL = "/orderwell/US/1/ordersbyzone";

  String MFC_ALIGNED_STORES = "mfcAlignedStores";

  String MFC_PO_EVENT = "MFC REPLEN";

  String DELIVERY_WORKING_STATUS_EVENT_RESPONSE_ERROR_MSG =
      "Resource exception while invoking GDM delivery status update event for Working status. Error MSG = %s";
  String DELIVERY_OPEN_STATUS_EVENT_RESPONSE_ERROR_MSG =
      "Resource exception while invoking GDM delivery status update event for Open status. Error MSG = %s";

  String IS_MFC_DISTRIBUTION_PALLET_PULL_SUPPORTED = "isMfcDistributionPalletPullSupported";

  String PRINT_SHIPPING_LABEL_FROM_ROUTING_LABEL_URI = "/pick/slFromRl";
  String UPDATE_REJECTED_LABELS_IN_OP_URI = "/v1/lpn/confirm";

  String ORDER_SERVICE_REJECTED_LPNS_CANCELLATION_FAILED =
      "Order service rejected lpns cancellation failed for item number: %s";
  String ORDER_FULFILLMENT_PRINT_SL_FROM_RL_FAILED =
      "Print Shipping label from Routing label failed in order fulfillment for container : %s";

  String SMART_LABEL_REGEX_PATTERN = "^\\d{18}$";
  String INVALID_SMART_LABEL = "Invalid Smart Label";
  String BACKOUT = "backout";
  String ALLOCATED = "ALLOCATED";
  String FACILITY_COUNTRY_CODE = "US";

  String OPEN = "Open";
  String PENDING = "Pending";

  String STAPLE_STOCK_LABEL = "SSTK";

  String MISSING_ITEM_HANDLING_CODE =
      "Item %s is missing handling method code. Have the QA team resolve this before receiving.";
  String INVENTORY_PUTAWAY_CONFIRMATION_REQUEST_MSG =
      "Response: Inventory call for putawayConfirmation with trackingId={} statusCode={}";
  String INVENTORY_UNEXPECTED_DATA_ERROR = "Unexpected data error while calling Inventory";
  String INVENTORY_PUTAWAY_CONFIRMATION_URI = "/inventory/inventories/putawayConfirmation";
  String LABEL_GENERATION_SCHEDULER_USER_NAME = "label_generation_scheduler";

  String DEFAULT_ITEM_HANDLING_CODE_ENABLED = "isDefaultItemHandlingCodeEnabled";

  // KEEP THIS COMMENT AS END OF FILE PLEASE UPDATE values ABOVE this line to avoid merge conflicts
  // cancel container outbox flag
  String PARSED_UPC_REGEX_PATTERN = "^0+(?!$)";
  int CASE_UPC_LENGTH = 14;
  int CASE_UPC_STARTING_INDEX = 1;
  int CASE_UPC_ENDING_INDEX = 13;
  String RDC_SLOT_UPDATE_EVENT_PROCESSOR = "rdcKafkaSlotUpdateEventProcessor";
  String WRONG_SLOT_UPDATE_MESSAGE_FORMAT = "Message format on Slot update Message is wrong {}";
  String UNABLE_TO_PROCESS_SLOT_UPDATE_EVENT_ERROR_MSG =
      "Unable to process the slot update event message. payload = %s";
  String ITEM_PRIME_DETAILS = "itemPrimeDetails";
  String ITEM_PRIME_DELETE = "itemPrimeDelete";
  String ITEM_CAPACITY_UPDATE = "itemCapacityUpdate";
  String RDC_SLOT_UPDATE_LISTENER = "rdcSlotUpdateListener";
  String ITEM_DEPT = "itemdept";
  String DIVISION_NUMBER = "divisionNumber";
  String DEFAULT_REPRINT_LABEL_HANDLER = "defaultReprintLabelHandler";
  String IS_ATLAS_RDC_PALLET_FORMAT_ENABLED = "isAtlasRdcPalletFormatEnabled";

  String REPRINT = "REPRINT";
  String LBL_REPRINT_VALUE = "R";
  String SLOTTING_PO_TYPE_MISMATCH_ERROR_CODE = "GLS-SMART-SLOTING-4000042";
  String SLOTTING_INACTIVE_SLOT_ERROR_CODE = "GLS-SMART-SLOTING-4000044";
  String SLOTTING_FROZEN_SLOT_ERROR_CODE = "GLS-SMART-SLOTING-4000043";
  String SLOTTING_LOCATION_NOT_CONFIGURED_ERROR_CODE = "GLS-SMART-SLOTING-4000041";
  String SLOTTING_CONTAINER_ITEM_LOCATION_NOT_FOUND_ERROR_CODE = "GLS-SMART-SLOTING-4000039";

  String SSTK_FREIGHT_TYPE = "SSTK";
  String DA_FREIGHT_TYPE = "DA";

  String SLOTTING_PO_TYPE_DA_MISMATCH_ERROR_DESC =
      "Entered location is not configured to be a DA slot";
  String SLOTTING_PO_TYPE_SSTK_MISMATCH_ERROR_DESC =
      "Entered location is not configured to be a SSTK slot";

  String PUBLISH_TO_KAFKA_TOPIC_RESTRICTED_ERROR_MSG = "Publish to kafka topic %s is restricted";
  String RESOLUTION_ON_BPO_CHECK_ENABLED = "isResolutionOnBpoCheckEnabled";

  String WFS_INSTRUCTION_ERROR_CODE = "WFS_ERROR";
  String IS_REINDUCT_ROUTING_LABEL = "reInductRoutingLabel";

  String PIPE_DELIMITER = "|";
  String PENDING_AUDIT = "PENDING_AUDIT";

  String ORIGIN_DC_NBR = "originDcNbr";

  // Constants for Delivery Event Processor
  String PURCHASE_ORDER_PARTITION_SIZE_KEY = "purchaseOrderPartitionSize";
  Integer PURCHASE_ORDER_PARTITION_SIZE = 100;
  String RECEIVING_TYPE = "receivingType";
  String FREIGHTBILL_QTY = "freightBillQty";
  String EVENT_STATUS = "eventStatus";

  String AUTO_SWITCH_EPCIS_TO_ASN = "autoSwitchEpcisToAsn";
  String ENABLE_PALLET_FLOW_IN_MULTI_SKU = "enablePalletFlowInMultiSku";
  String IS_DC_RDS_RECEIPT_ENABLED = "enableRDSReceipt";
  String LABEL_PRINT_FORMAT_CONFIG = "labelPrintFormatName";
  String ENABLE_OUTBOX_CREATE_MOVES = "enableOutboxCreateMoves";

  List<Integer> transferPosTypes = Arrays.asList(28, 29);
  String GDM_DATA_NOT_FOUND =
      "Scanned barcode is not found on this delivery. Please quarantine this freight and submit a problem ticket.";
  String ERROR_GETTING_SIBLINGS_DATA =
      "Error from GDM while getting the details of other cases/units associated with this barcode. Please contact support.";
  String ERROR_UPDATING_GDM =
      "Error in updating the Receiving status at GDM. Please contact support.";
  String ALTERNATE_GTIN = "alternateGtin";

  // Codes for Auto Receive Failure Reasons
  interface ASNFailureReasonCode {
    String TCL_ALREADY_SCANNED_UNABLE_TO_SCAN_AGAIN = "TCL already scanned. Unable to scan again.";
    String INVALID_DIVERT_STATUS = "Invalid divert status";
    String INVALID_GDM_ASN_RESPONSE = "Invalid GDM ASN response.";
    String POLINE_EXHAUSTED = "POLine exhausted.";
    String AUDIT_CHECK_REQUIRED = "Audit check required";
    String CASE_WEIGHT_CHECK_FAILED = "Case weight check failed.";
    String WFS_CHECK_FAILED = "WFS PO received.";
    String MULTIPLE_SELLER_ASSOCIATED = "Multiple seller associated with UPC and delivery number.";
    String PALLET_DIVERTED_TO_RECEIVE = "Pallet diverted to receive.";
  }

  // KEEP THIS COMMENT AS END OF FILE PLEASE UPDATE values ABOVE this line to avoid merge conflicts
}
