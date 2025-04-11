package com.walmart.move.nim.receiving.fixture.common;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;

public final class FixtureConstants {
  public static final String MULTIPLE_PALLETS_FOUND =
      "Multiple pallets found for pallet ID %s. Please contact your supervisor.";
  public static final String INVALID_DESTINATION =
      "Store number cannot be empty. Please enter a valid store number.";
  public static final String INVALID_ITEM_DETAILS =
      "Item details are missing. Please provide itemNumber, receivedQty & itemDescription.";
  public static final String LPN_ALREADY_MAPPED_TO_DIFF_PALLET =
      "LPN %s is already used. Please provide a different LPN.";
  public static final String LPN_ALREADY_MAPPED = "LPN %s is already mapped for this pallet %s.";
  public static final List<String> CONTAINER_STATUS_WO_ASN =
      Arrays.asList(
          ReceivingConstants.STATUS_ACTIVE_NO_ASN, ReceivingConstants.STATUS_COMPLETE_NO_ASN);
  public static final String IS_CT_ENABLED = "isCTPostingEnabled";
  public static final String CONTAINER_STATUS_MAPPED_LPN = "LPN_MAPPED";
  public static final List<String> CONTAINER_STATUS_WO_ACTIVE_LIST =
      Arrays.asList(ReceivingConstants.STATUS_ACTIVE_NO_ASN, ReceivingConstants.STATUS_ACTIVE);

  private FixtureConstants() {}

  public static final String CT_POST_INVENTORY =
      "/api/integration/v1/warehousing/receive-inventory/batch";
  public static final String CT_GET_INVENTORY =
      "/api/integration/v1/warehousing/receive-inventory/batch/{batchId}";
  public static final String CT_AUTHENTICATE_URL = "/api/Authentication";
  public static final String FIXTURE_DELIVERY_EVENT_PROCESSOR = "fixtureDeliveryEventProcessor";
  public static final String BATCH_ID = "batchId";

  // constants for inventory container creation
  public static final Integer DEFAULT_VNPK_QTY = 1;
  public static final Integer DEFAULT_WHPK_QTY = 1;
  public static final Float DEFAULT_VNPK_WEIGHT_QTY = 1f;
  public static final Float DEFAULT_VNPK_CUBE_QTY = 1f;
  public static final String DEFAULT_VNPK_WEIGHT_UOM = "KG";
  public static final String DEFAULT_VNPK_CUBE_UOM = "M3";

  public static final String SHIPMENT_RECONCILE_PROCESSOR_BEAN = "shipmentReconcileProcessor";
  public static final String SHIPMENT_PERSIST_PROCESSOR_BEAN = "shipmentPersistProcessor";
  public static final String SHIPMENT_UPDATE_PROCESSOR_BEAN = "shipmentUpdateProcessor";
  public static final String SHIPMENT_EVENT_PROCESSOR_STRATEGY = "shipmentEventProcessStrategy";

  public static final String DATE_TIME_YYYY_MM_DD_HH_MM_SS = "yyyy-MM-dd hh:mm:ss";
  public static final String ITEM_MDM_DIMENSION = "dimensions";
  public static final String PALLET_SIZE = "palletSizeType";
  public static final String ITEM_TRADE_DETAILS = "tradeItems";
  public static final String GTIN = "gtin";
  public static final String ITEM_MDM_DC_PROPERTIES = "dcProperties";
  public static final String ITEM_MDM_HEIGHT = "height";
  public static final String WAREHOUSE = "warehouse";
  public static final String WAREHOUSEAREA = "warehouseArea";
  public static final String ITEM_MDM_WIDTH = "width";

  public static final String ITEM_MDM_WEIGHT = "weight";

  public static final String ITEM_MDM_DEPTH = "depth";

  public static final String ITEM_MDM_UOM = "uom";

  public static final String ITEM_MDM_WAREHOUSE_CODE = "uom";

  public static final String ITEM_MDM_AMOUNT = "amount";

  public static final String ITEM_MDM_SUPPLY_ITEM = "supplyItem";

  public static final int SUBCENTER_ID = 1;

  public static final String SLOT_CREATED_BY = "SYSTEM";
  public static final String DEFAULT_ITEM_UPC_REGIONAL = "DEFAULT_UPC_REG";

  public static final String ITEM_WORKLOCATION_ID = "RFC";

  public static final String ITEM_UOM = "EACHES";

  public static final String UOM = "EA";

  public static final String InstructionType = "SLOTTING";

  public static final String DATE_FORMAT_FOR_UTC = "yyyy-MMM-dd HH:mm:ss";

  public static final String UTC = "UTC";

  public static final Integer DEFAULT_WAREHOUSE_TI = 2;
  public static final Integer DEFAULT_WAREHOUSE_HI = 1;

  public static final Integer INSTRUCTION_SOFT_LIMIT = 0;

  public static final Integer INSTRUCTION_HARD_LIMIT = 0;
  public static final String ITEM_MDM_ROTATE_DATE = "9999-11-12T12:41:55.603";

  public static final String ReceivingMethod = "SSTK";

  public static final String CONTAINER_TYPE = "PALLET";

  public static final String REPORTING_GROUP = "us";
  public static final String BASE_DIVISION_CODE = "wm";
  public static final String ITEM_MDM_PALLET_CODE = "code";
  public static final String RFC_LPN_SERVICE = "rfcLpnService";

  public static final String RFC_LPN_CACHE_SERVICE = "RfcLPNCacheService";

  public static final String ITEM_NUMBER = "number";

  public static class PrintingConstants {
    public static final String ITEM_NBR = "ITEM_NBR";
    public static final String ITEM_DESCRIPTION = "ITEM_DESCRIPTION";
    public static final String PO_NBR = "PO_NBR";
    public static final String LPN = "LPN";
    public static final String USER = "USER";
    public static final String DATE_RECEIVED = "DATE_RECEIVED";
    public static final String QTY = "QTY";
  }
}
