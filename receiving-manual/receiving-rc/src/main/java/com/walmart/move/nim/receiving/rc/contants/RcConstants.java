package com.walmart.move.nim.receiving.rc.contants;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;

public interface RcConstants extends ReceivingConstants {

  String RETURNS_BASE_URI = "returns/";
  String RETURNS_CONTAINER_URI = RETURNS_BASE_URI + "container";
  String RETURNS_TEST_URI = RETURNS_BASE_URI + "test";
  String RETURNS_PACKAGE_URI = RETURNS_BASE_URI + "package";
  String RETURNS_ITEM_URI = RETURNS_BASE_URI + "item";
  String RETURNS_WORKFLOW_URI = RETURNS_BASE_URI + "workflow";
  String RETURNS_PRODUCT_CATEGORY_GROUP_URI = RETURNS_BASE_URI + "productCategoryGroup";
  String PACKAGE_TRACKER_URI = "/tracker";
  String ITEM_TRACKER_URI = "/tracker";
  String RECEIVE_URI = "/receive";
  String UPDATE_URI = "/update";
  String RETURNS_CONTAINER_BY_GTIN_URI = "/gtin/{gtin}";
  String WORKFLOW_ID_URI = "/{workflowId}";
  String WORKFLOW_IMAGE_URI = "/{workflowId}/image";
  String WORKFLOW_COMMENT_URI = "/{workflowId}/comment";
  String TRACKING_ID_URI = "/{trackingId}";
  String FETCH_TRACKING_ID_URI = "/receivingWorkflow/{itemTrackingId}";
  String RC_TRACKING_ID_URI = "/{rcTrackingId}";
  String PRODUCT_TYPE_URI = "/{productType}";
  String UPDATE_RO_URI = "/ro";
  String UPDATE_BY_TRACKING_ID_URI = UPDATE_URI + TRACKING_ID_URI;
  String UPDATE_RO_BY_TRACKING_ID_URI = UPDATE_URI + UPDATE_RO_URI + RC_TRACKING_ID_URI;
  String WORKFLOW_SEARCH_URI = "/search";
  String PUBLISH_URI = "/publish";
  String PACKAGE_BAR_CODE_URI = "/package/{packageBarCode}";
  String SO_NUMBER_URI = "/package/soNumber/{soNumber}";
  String WORKFLOW_STATS_URI = "/stats";
  String RETURNS_RECEIPT_EVENT_TYPE = "headlessTransfer";

  // Default Values
  Long DEFAULT_DELIVERY_NUMBER = 8888888L;
  String DEFAULT_SALES_ORDER = "7777777";
  Integer DEFAULT_SALES_ORDER_LINE = Integer.valueOf(1);
  String DEFAULT_CHANNEL = "SSTKR";
  Long DEFAULT_ITEM_NUMBER = -1L;
  Integer RETURN_PURCHASE_COMPANY_ID = Integer.valueOf(1001);
  Integer ALLOWED_DELIVERY_NUMBER_LENGTH = Integer.valueOf(10);
  String RTV = "RTV";
  String RESTOCK = "RESTOCK";
  String DISPOSE = "DISPOSE";
  String POTENTIAL_FRAUD = "POTENTIAL_FRAUD";

  String PACKAGE_BARCODE_VALUE = "packageBarcodeValue";

  String CATEGORY_C = "C";

  String DEFAULT_OFFSET = "1";
  int MAX_PAGE_SIZE = 1000;
  int MAX_COMMENT_SIZE = 1024;
  String DEFAULT_PAGE_SIZE = "50";
  int BATCH_SIZE = 50;
  String WORKFLOW_ID = "workflowId";
  String PRODUCT_TYPE = "productType";
  String TYPE = "type";
  String ACTION = "action";
  String STATUS = "status";
  String ITEM_TRACKING_ID = "itemTrackingId";
  String WORKFLOW_ITEMS = "workflowItems";
  String RECEIVING_WORKFLOW = "receivingWorkflow";
  String CREATE_TS = "createTs";
  String UTC_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
  int MIN_OFFSET = 1;
  String FROM_CREATE_TS = "fromCreateTs";
  String TO_CREATE_TS = "toCreateTs";
  String FRAUD = "FRAUD";

  // package barcode types
  String RMA = "RMA";
  String RMAT = "RMAT";
  String SO = "SO";
  String SOT = "SOT";
  String PO = "PO";

  String[] PRODUCT_DETAILS_HEADERS = {"l0", "l1", "l2", "l3", "product_type", "group"};
}
