package com.walmart.move.nim.receiving.endgame.constants;

import static com.walmart.move.nim.receiving.endgame.constants.DivertStatus.DECANT;
import static com.walmart.move.nim.receiving.endgame.constants.DivertStatus.DECANT2;
import static com.walmart.move.nim.receiving.endgame.constants.DivertStatus.FTS_BUFFER;
import static com.walmart.move.nim.receiving.endgame.constants.DivertStatus.IB_BUFFER;
import static com.walmart.move.nim.receiving.endgame.constants.DivertStatus.PALLET_BUILD;

import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;

public interface EndgameConstants extends ReceivingConstants {

  String ENDGAME_DELIVERY_SERVICE = "endGameDeliveryService";
  String ENDGAME_ATTACH_PO_SERVICE = "endGameAttachPOService";
  String ENDGAME_DELIVERY_METADATA_SERVICE = "endGameDeliveryMetaDataService";
  String ENDGAME_RECEIPT_SUMMARY_PROCESSOR = "endGameReceiptSummaryProcessor";
  String ENDGAME_DELIVERY_STATUS_EVENT_PROCESSOR = "endgameDeliveryStatusProcessor";
  // Overwritten the Constant for GDM document search  API Endpoint.
  String GDM_DOCUMENT_SEARCH_URI_V3 = "/api/deliveries/{deliveryNumber}/item-scan?gtin={upcNumber}";
  String GDM_DOCUMENT_SEARCH_V3_ACCEPT_TYPE = "application/vnd.ItemScanResponse1+json";
  String GDM_DOCUMENT_GET_BY_DELIVERY_V3_ACCEPT_TYPE = "application/vnd.DeliveryResponse1+json";
  String GDM_DOCUMENT_GET_BY_SHIPMENT_ACCEPT_TYPE =
      "application/vnd.DeliveryShipmentScanResponse2+json";
  String ENDGAME_DIVERT_ACK_EVENT_PROCESSOR = "endGameDivertAckEventProcessor";
  String ENDGAME_OSDR_PROCESSOR = "endgameOsdrProcessor";
  String ENDGAME_RECEIVING_SERVICE = "endGameReceivingService";
  String ENDGAME_ASN_RECEIVING_SERVICE = "endGameAsnReceivingService";
  String ENDGAME_MANUAL_RECEIVING_SERVICE = "endGameManualReceivingService";
  String ENDGAME_RECEIVING_HELPER_SERVICE = "endGameReceivingHelperService";
  String ENDGAME_ITEM_UPDATE_PROCESSOR = "endgameItemUpdateProcessor";
  String ENDGAME_EXPIRY_DATE_PROCESSOR = "endgameExpiryDateProcessor";

  String TPL = "TPL";
  String TCL = "TCL";
  String DUMMY_VAL = "DUMMY_VAL";
  String ROTATE_DATE = "rotateDate";
  String DIVERT_DESTINATION = "divertDestination";
  String IS_CASE_FLAGGED = "isCaseFlagged";
  String IS_PALLET_FLAGGED = "isPalletFlagged";
  String PALLET_SELLABLE_UNITS = "palletSellableUnits";

  String ORG_UNIT_ID = "orgUnitId";
  String CLIENT_ID = "RCV";
  String DEFAULT_USER = "RCV_ACL";
  String ENDGAME_OSDR_EVENT_TYPE_KEY = "eventType";
  String ENDGAME_OSDR_EVENT_TYPE_VALUE = "rcv_osdr";

  String TCL_LABEL_FORMAT_NAME = "rcv_tcl_eg_zeb";
  String TPL_LABEL_FORMAT_NAME = "rcv_tpl_eg_zeb";
  String UI_CLIENT_ID = "receiving";
  int LABEL_TTL = 72;
  int ONE = 1;
  String ZERO = "0";
  String SUCCESS_MSG = "success";

  String STATUS = "status";
  int MAX_TCL = 100000000;
  String ENDGAME_LABEL_PREFIX = "T";
  char DEFAULT_TCL_CHAR_ASCII = 'D';
  String COUNTER_TYPE_TCL = "ENDGAME";
  String COUNTER_TYPE_TPL = "ENDGAME_TPL";

  // LOG Messages
  String SLOTTING_BAD_RESPONSE_ERROR_MSG =
      "Client exception from Slotting. HttpResponseStatus= %s ResponseBody = %s";
  String SLOTTING_RESOURCE_RESPONSE_ERROR_MSG = "Resource exception from Slotting. Error MSG = %s";
  String SLOTTING_UNABLE_TO_PROCESS_ERROR_MSG =
      "Resource exception from Slotting. ResponseBody :  = %s";
  String LOG_SLOTTING_DIVERT_DESTINATION_LOG =
      "Got the divert destination for [itemNumber={}] [caseUPC={}] [destination={}]";
  String LOG_SLOTTING_DIVERT_UPDATE_TO_HAWKEYE =
      "Going to update hawkeye for [caseUPC={}] having [oldDestination={}] to [endgameSlottingData={}]";
  String LOG_SLOTTING_DIVERT_SAME_AS_PREVIOUS_NOT_UPDATING_TO_HAWKEYE =
      "Got the divert destination same as previous destination for [caseUPC={}] so not updating to hawkeye";
  String LOG_SLOTTING_ERROR_RESPONSE = "Error type response from slotting [response={}]";
  String LOG_SLOTTING_NO_LOCATION_RESPONSE = "No location for the tracking id from slotting= {}";
  String UNABLE_TO_CREATE_CONTAINER_ERROR_MSG = "Unable to process scan event. TCL= %s";
  String UNABLE_TO_PROCESS_EXPIRY_UPDATION_ERROR_MSG =
      "Unable to process the expiry . payload = %s ";
  String UNABLE_TO_PROCESS_FTS_UPDATION_ERROR_MSG =
      "Unable to process the FTS updation . payload = %s ";
  String UPC_NOT_FOUND_ERROR_MSG = "UPC = %s not found to process Slotting prioritisation";
  String METADATA_NOT_FOUND_ERROR_MSG = "Meta data not found for delivery = %s";
  String TCL_NOT_FOUND_ERROR_MSG = "TCL= %s not found";
  String DELIVERY_METADATA_NOT_FOUND_ERROR_MSG =
      "Unable to get deliveryMetaData for deliveryNumber= %s";
  String PO_PO_LINE_EXHAUSTED_ERROR_MSG =
      "All PO/PO Lines are exhausted for UPC= [%s] and Delivery = [%s]";

  String ASN_PO_PO_LINE_EXHAUSTED_ERROR_MSG =
      "All ASN PO/PO Lines are exhausted for UPC= [%s] and Delivery = [%s]";
  String ITEM_NOT_FOUND_IN_PO_PO_LINE_ERROR_MSG = "Item=[%s] not present in PO/PO Lines.";
  String DIVERT_UPLOAD_FLOW = "DIVERT_UPLOADING";
  String DELIVERY_COMPLETED_FLOW = "DELIVERY_COMPLETED";
  String TCL_UPLOAD_FLOW = "TCL_UPLOADING";
  String VENDOR_PACK_DIMENSIONS_FLOW = "VENDOR_PACK_DIMENSIONS";
  String NO_LABEL_FOUND_ERROR_MSG = "No Label Data found for delivery = %s";
  String LOG_TCL_GENERATED_FOR_DELIVERY_NUMBER =
      "Tcl generated for the deliveryNumber {}. Total label count is {}";
  String LOG_TCL_PERSISTED_SUCCESSFULLY = "TCL got persisted successfully for deliveryNumber {}";
  String LOG_TCL_SENT_SUCCESSFULLY_TO_HAWKEYE =
      "TCL for deliveryNumber {} successfully sent to Hawkeye";
  String LOG_TCL_NOT_RECEIVED =
      "Tcl {} is not received against any po/poline as divert destination is {}";
  String LOG_SCAN_EVENT = "Got Scan Event from auto scan tunnel {}";
  String LOG_TCL_ALREADY_SCANNED = "TCL {} is already scanned so cannot scan again";
  String LOG_EXPIRY_DATE_UPDATE = "Got Container Update event from decant-api {}";
  String UNABLE_TO_UPDATE_DIVERT_DESTINATION_TO_HAWKEYE_AFTER_CAPTURING_EXPIRY_DATE =
      "Unable to update divert destination to hawkeye for [itemNumber={}] while capturing the expiry date for [tcl={}] [exceptionMessage={}]";
  String UNABLE_TO_PUBLISH_CONTAINER_DCFIN = "Unable to publish container=%s to DCFin";
  String INVALID_EXPIRY_DATE_LISTENER_DATA_ERROR_MSG = "Invalid expiry date listener data %s";
  String CONTAINERS_NOT_FOUND_FOR_DELIVERY_NUMBER = "Containers not found for delivery number {}";
  String CALLING_ITEM_MDM_SERVICE = "Going to call item MDM for itemNumber {}";
  String LOG_ITEM_UPDATE_PROCESSOR_FTS = "Going to update FTS operation for itemNumber {}";
  String OSDR_DETAILS_NOT_FOUND_ERROR_MSG =
      "OSDR details not available for [deliveryNumber={}] [exception={}]";
  String SLOTTING_DIVERT_DESTINATION_NOT_FOUND =
      "No suitable divert destination for [itemNumber={}] [caseUPC={}]";
  String MULTIPLE_SELLER_ID_IN_PURCHASE_ORDER =
      "Multiple seller id found for [caseUPC={}] in [delivery={}] not updating divert destination to hawkeye";
  String MULTIPLE_SELLER_ID_IN_UPC_DESTINATION_TABLE =
      "Multiple seller id found for [caseUPC={}] in ENDGAME_UPC_DESTINATION not updating divert destination to hawkeye";
  String ATTRIBUTES_FTS = "isFTS";
  String HAWKEYE_SCAN_USER = "Hawkeye-Scanned";
  String AT = "@";
  String UOM_LB = "LB";
  String UOM_CF = "CF";
  String ENDGAME_PALLET_LABEL_PREFIX = "P";
  // Non-Sort endgame constant
  String NONSORT_ENDGAME_DELIVERY_STATUS_EVENT_PROCESSOR = "nonSortEndgameDeliveryStatusProcessor";
  String END_GAME_UNLOADING_COMPLETE_DELIVERY_PROCESSOR =
      "endGameUnloadingCompleteDeliveryProcessor";
  String EMPTY_CONTAINER_ITEM_LIST = "Container item list can not be null or empty";
  String INVALID_QUANTITY = "Quantity/Case Quantity value is invalid";

  String TCLPREFIX = "TCLPREFIX";
  String TCLSUFFIX = "TCLSUFFIX";
  String DESTINATION = "DESTINATION";
  String QTY = "Qty";
  String DATE = "Date";
  String DELIVERY_NUMBER = "deliveryNumber";
  String ITEM = "ITEM";
  String DESCRIPTION = "DESC";
  String UPCBAR = "UPCBAR";
  String USER = "user";
  String TRAILER = "trailer";

  String NO_SLOTS_AVAILABLE = "There are no slots available for some of the pallets";
  String ALL = "ALL";
  String DEFAULT_DELIVERY_PROCESSOR = "defaultDeliveryProcessor";
  String INBOUND_PREP_TYPE_PATH = "$.dcProperties.fcAttributes.inboundPrepType";

  interface ItemProperties {
    String FC_ATTRIBUTES = "fcAttributes";
    String TOTABLE = "totable";
    String GTIN_HAZMAT = "gtinHazmat";
    String SLOTTING = "slotting";
    String CODE = "code";
  }

  List<String> VALID_DIVERTS_TO_RECEIVE =
      Arrays.asList(
          DECANT.getStatus(),
          PALLET_BUILD.getStatus(),
          IB_BUFFER.getStatus(),
          DECANT2.getStatus(),
          FTS_BUFFER.getStatus());

  List<String> PALLET_DIVERTS_TO_RECEIVE =
      Arrays.asList(PALLET_BUILD.getStatus(), IB_BUFFER.getStatus());
}
