package com.walmart.move.nim.receiving.sib.utils;

public interface Constants {
  String STORE_CONTAINER_OVERAGE_EVENT_PROCESSOR = "storeContainerOverageEventProcessor";
  String STORE_CONTAINER_SHORTAGE_PROCESSOR = "storeContainerShortageProcessor";
  String STORE_DELIVERY_METADATA_SERVICE = "storeDeliveryMetadataService";
  String ISO_FORMAT_STRING_REQUEST = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
  String PALLET = "PALLET";
  String DUMMY_PURCHASE_REF_NUMBER = "999999999";
  String MEAT_WHSE_CODE = "1";
  String NEIGHBORHOOD_MARKET = "Neighborhood Market";
  String STORE_TYPE = "store_type";
  String STOCKING = "STOCKING";
  String ORIGINATOR_ID = "originatorId";
  String MESSAGE_TIMESTAMP = "msgTimestamp";
  String USER_ID = "userId";
  String VERSION_1_0_0 = "1.0.0";
  String UNDERSCORE = "_";
  String PALLET_TYPE = "palletType";
  String STORE = "STORE";
  String MFC = "MFC";
  String MARKET_FULFILLMENT_CENTER = "MARKET_FULFILLMENT_CENTER";
  String AVAILABLE_TO_SELL = "AVAILABLE_TO_SELL";
  String CREATE_TS = "createTs";
  String UNLOAD_TS = "unloadTs";
  String CONTAINER_CREATE_DATE = "containerCreateDate";
  String TIMEZONE_CODE = "timeZoneCode";
  String BANNER_CODE = "bannerCode";
  String BANNER_DESCRIPTION = "bannerDescription";
  String DEST_TRACKING_ID = "destTrackingId";
  String PROBLEM = "PROBLEM";
  String FREIGHT_TYPE = "freight_type";
  String EVENT_RECEIVING = "RECEIVING";
  String EVENT_STOCKED = "STOCKED";

  String EVENT_ORIGINATOR = "ATLAS";
  String QUANITY_TYPE_SNAPSHOT = "SNAPSHOT";
  String EVENT_DELIVERY_ARRIVED = "ARRIVED";
  String SHIPPER = "SHIPPER";
  String CID = "cid";
  String PACK_NUMBER = "packNumber";

  String DOCUMENT_INGEST_TIME = "documentIngestTime";
  String DELIVERY_SCH_TS = "scheduleTs";
  String DELIVERY_ARV_TS = "arrivalTs";

  String ENABLE_NGR_PARITY_FOR_CORRECTIONAL_INVOICE = "enable.ngr.parity.for.correctional.invoice";
  String CORRECTIONAL_INVOICE_QTY_TYPE_DELTA = "DELTA";
  String REASON_OVERAGE = "OVERAGE";
  String REASON_SHORTAGE = "SHORTAGE";
  String CASE = "CASE";
  String DEPT_CATEGORY_NBR = "deptCategoryNbr";
  String DEPT_SUB_CATEGORY_NBR = "deptSubcatgNbr";
  String ENABLE_STORE_PALLET_PUBLISH = "enable.store.pallet.publish";
  String ENABLE_NGR_FINALIZATION = "enable.ngr.finalization";
}
