package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.RangeErrorResponse;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * automatic handling of http status codes instead of writing manually in response code. this
 * exception is for api controllers handled using APO aspects.
 *
 * @author a0s01qi
 */
// TODO Needs to validate wheather to extend from RuntimeException or Exception.
// Because, RuntimeException and Error is supported in Spring AOP Transaction
// https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/annotation/Transactional.html#rollbackFor--
@Getter
@Setter
public class ReceivingException extends Exception {

  private static final long serialVersionUID = 1L;

  private HttpStatus httpStatus;
  private ErrorResponse errorResponse;

  // Security management error messages
  public static final String SM_SERVICE_DOWN_ERROR_CODE = "SecurityMgmtServiceDown";
  public static final String SM_SERVICE_DOWN_ERROR_MSG =
      "Security management service is down. Try again.";
  public static final String SM_AUTHENTICATE_ERROR_CODE = "authenticate";
  public static final String SM_AUTHENTICATE_ERROR_HEADER = "Credentials failed";
  public static final String SM_AUTHENTICATE_ERROR_MSG =
      "The system was unable to verify the credentials provided. Please try again.";
  public static final String SM_AUTHORIZE_ERROR_CODE = "authorize";
  public static final String SM_AUTHORIZE_ERROR_HEADER = "Unauthorized";
  public static final String SM_AUTHORIZE_ERROR_MSG =
      "%s is unauthorized to approve this overage. Please have a supervisor enter their credentials to continue.";
  public static final String TOKEN_AUTHORIZE_ERROR_MSG =
      "The QR Code scanned is not the correct code to authorize this override, please scan the correct QR Code.";

  // exception flow for receiving corrections
  public static final String LPN_NOT_FOUND_VERIFY_LABEL_ERROR_MESSAGE =
      "LPN not found, verify label and try again";
  public static final String LPN_NOT_FOUND_VERIFY_LABEL_ERROR_CODE = "LpnNotFoundVerifyLabel";
  public static final String LPN_NOT_FOUND_VERIFY_LABEL_ERROR_HEADER = "Scan the LPN to correct";

  // ADJUST_PALLET_QUANTITY non-functional or tech or unknown errors
  public static final String ADJUST_PALLET_QUANTITY_ERROR_MSG =
      "The LPN quantity was unable to be updated. Please try again. If the problem persists, contact a supervisor.";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_HEADER = "Unable to update";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_CODE = "AdjustPalletError";
  // ADJUST_PALLET_QUANTITY functional errors
  public static final String ADJUST_PALLET_QUANTITY_SAME_ERROR_MSG = "Enter a new quantity";
  // TiHi
  public static final String ADJUST_PALLET_QUANTITY_TIHI_ERROR_MSG =
      "The new LPN quantity entered exceeds the item's Ti x Hi. Please verify the quantity entered.";

  public static final String BLOCK_PRINT_ASYNC_FLOW_ERROR_MSG =
      "Please use other workstation or quantity receiving to receive this freight";
  public static final String ADJUST_PALLET_QUANTITY_TIHI_ERROR_HEADER = "Quantity exceeds Ti x Hi";
  // Overge
  public static final String ADJUST_PALLET_QUANTITY_OVERAGE_ERROR_MSG =
      "The quantity entered is more than the allowable overage. Verify quantity or report a problem if this is an issue.";
  public static final String ADJUST_PALLET_QUANTITY_OVERAGE_ERROR_HEADER = "Overage alert";
  public static final String AUTO_SELECT_PO_LINE_NO_OPEN_QTY =
      "Allowed PO Line quantity has been received.";
  public static final String MOVE_SERVICE_UNABLE_TO_VERIFY_MSG = "Unable to verify move status.";

  public static final String MOVE_SERVICE_DOWN_ERROR_CODE = "MoveServiceDown";
  public static final String BILL_SIGNED_ERROR_MSG =
      "Negative receiving correction can not be performed, bill is signed. Please contact QA to make an adjustment.";
  public static final String RECEIPT_ERROR_MSG =
      "Receiving correction can not be performed after %s days from Receipt.";
  public static final String PALLET_NOT_AVAILABLE_ERROR_MSG =
      "Receiving correction can not be performed as pallet is NOT in available status. Please contact QA to make an adjustment.";
  public static final String CANNOT_CANCEL_PALLET_AVAILABLE_ALLOCATED_QTY_NOT_ZERO_ERROR_MSG =
      "Cancel pallet cannot be performed as pallet is NOT in available status, please contact QA to do an adjustment";
  public static final String MOVE_INVALID_STATUS_MSG =
      "Receiving correction can not be performed while a move is in-progress";
  public static final String NEGATIVE_RC_CANNOT_BE_DONE_PUTAWAY_COMPLETE =
      "Negative receiving correction cannot be performed as pallet is putaway, contact QA to do an adjustment";
  public static final String ADJUST_PALLET_VALIDATION_ERROR_CODE = "AdjustPalletValidationError";
  public static final String PALLET_HAS_BEEN_INDUCTED_INTO_MECH =
      "Pallet has been inducted into Mechanization, Cancel Pallet and Receiving Correction not allowed.";

  public static final String RECEIVE_CORRECTION_VALIDATION_ERROR_MSG =
      "There is an validation error while doing correction.Contact support to help get this resolved.";
  // Input validations
  public static final String INVENTORY_ADJUST_PALLET_QUANTITY_INVALID =
      "Quantity cannot be null"; // can be +ve/-ve
  public static final String ADJUST_PALLET_QUANTITY_INVALID = "Quantity cannot be negative";
  public static final String ADJUST_PALLET_QUANTITY_PRINTER_ID_INVALID =
      "Printer Quantity cannot be 0";
  // po finalize & backout
  public static final String ADJUST_PALLET_QUANTITY_ERROR_HEADER_PO_NOT_FINALIZE =
      "PO not confirmed";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_CODE_PO_NOT_FINALIZE = "poNotConfirmed";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE =
      "Please confirm PO %s before correcting this LPN.";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_CODE_CONTAINER_BACKOUT =
      "containerIsBackout";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_MSG_CONTAINER_BACKOUT =
      "This LPN is cancelled and cannot be updated";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_HEADER_CONTAINER_BACKOUT =
      "Container is backout";

  // Open instruction error code and messages
  public static final String OPEN_INSTRUCTION_ERROR_CODE = "OpenInstructions";
  public static final String UPDATE_INSTRUCTION_ERROR_CODE = "updateInstruction";
  public static final String RECEIVE_INSTRUCTION_ERROR_CODE = "receiveInstruction";
  public static final String BACKOUT_CONTAINER_ERROR_CODE = "backoutContainer";
  public static final String OPEN_INSTRUCTION_ERROR_MESSAGE =
      "There are open pallets that need to be finished before you can complete this delivery. Please finish working those first.";

  public static final String DELIVERY_NOT_FOUND = "No delivery found";
  public static final String ASN_DETAILS_NOT_FOUND = "Asn details not found";
  public static final String PURCHASE_ORDER_NOT_FOUND = "No purchase order found";
  public static final String PURCHASE_ORDER_LINE_NOT_FOUND =
      "No purchase order line found for given request";
  public static final String LPN_DETAILS_NOT_FOUND = "No container details found";
  public static final String GDM_GET_DELIVERY_ERROR =
      "Error occurred while fetching delivery from GDM";
  public static final String NO_PO_FOUND = "No Po found for Po Number: [%s]";
  public static final String NO_DELIVERY_DOCUMENTS_FOUND =
      "No delivery documents found in GDM for delivery number: [%s]";
  public static final String NO_PO_LINES_FOUND = "No Po Lines found for Po Number: [%s]";
  public static final String RECEIVED_QTY_SUMMARY_BY_PO_ERROR_CODE = "receiptSummaryByPo";
  public static final String RECEIVED_QTY_SUMMARY_BY_PO_ERROR_MESSAGE =
      "Error occurred while fetching PO receipts summary for delivery number %s. Error Message [%s]";
  public static final String RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_CODE = "receiptSummaryByPoLine";
  public static final String RECEIVED_QTY_SUMMARY_BY_PO_LINE_ERROR_MESSAGE =
      "Error occurred while fetching receipts summary for PO Number: %s. Error Message [%s]";

  public static final String DELIVERY_NOT_FOUND_HEADER = "No Deliveries Found";
  public static final String DELIVERY_NOT_FOUND_ERROR_MESSAGE = "No delivery found for %s";

  //   Create instruction error code and messages
  public static final String CREATE_INSTRUCTION_ERROR_CODE = "createInstruction";
  public static final String CREATE_OR_RECEIVE_INSTRUCTION_ERROR_CODE =
      "createOrReceiveInstruction";
  public static final String CREATE_INSTRUCTION_NO_PO_LINE = "No PO/POLine information found.";
  public static final String PO_LINE_EXHAUSTED = "PO/POLine exhausted.";
  public static final String ITEM_NOT_IN_PO_LINE = "Item not present in PO/POLine.";
  public static final String NO_PO_LINE_AUTOMATED_FLOW =
      "No po/poline information found for UPC= [%s] and Delivery = [%s]";

  public static final String DELIVERY_HEADER_DETAILS_NOT_FOUND =
      "No deliveries found in the given timeframe.";
  public static final String DELIVERY_HEADER_DETAILS_NOT_FOUND_BY_DELIVERY_NUMBERS =
      "No deliveries found in GDM for the given delivery numbers: %s";

  public static final String CREATE_INSTRUCTION_NOT_FOUND =
      "No instruction found for provided input.";
  public static final String CREATE_INSTRUCTION_ASYNC_SAVE_FETCH_FAILED =
      "Error in getting instruction saved asynchronously {}.";
  public static final String ASYNC_PO_POL_FETCH_FAILED = "Error in getting PO/POL asynchronously";
  public static final String OVERAGE_ERROR_CODE = "overage";
  public static final String CREATE_INSTRUCTION_FOR_ASN_ERROR_CODE =
      "Create instruction for ASN error";
  public static final String CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_CODE = "No matching ASN";
  public static final String CREATE_INSTRUCTION_FOR_NO_MATCHING_ASN_ERROR_MESSAGE =
      "There was no ASN found for this item. Please let your supervisor know so they can correct it";
  public static final String CREATE_INSTRUCTION_FOR_ASN_LABEL_ALREADY_RECEIVED_ERROR_MESSAGE =
      "Label#%s has already been received";

  // Complete instruction error code and messages
  public static final String COMPLETE_INSTRUCTION_ERROR_CODE = "completeInstruction";
  public static final String COMPLETE_INSTRUCTION_ERROR_CODE_FOR_ALREADY_COMPLETED =
      "Instruction is complete";
  public static final String COMPLETE_INSTRUCTION_ERROR_MSG =
      "There is an error while completing instruction.";
  public static final String RECEIVE_INSTRUCTION_ERROR_MSG =
      "There is an error while receiving the instruction.";
  public static final String RECEIVE_EXCEPTION_ERROR_MSG =
      "There is an error while receiving the exception.";
  public static final String COMPLETE_INSTRUCTION_ALREADY_COMPLETE =
      "This pallet was completed by %s, please start a new pallet to continue receiving.";
  public static final String COMPLETE_INSTRUCTION_PALLET_CANCELLED =
      "This pallet was cancelled by %s, please start a new pallet to continue receiving.";

  // Canceled Instruction error messages
  public static final String CANCEL_INSTRUCTION_ERROR_CODE = "cancelInstruction";
  public static final String CANCEL_INSTRUCTION_ERROR_MSG =
      "There is an error while cancelling instruction.";
  public static final String CANCEL_INSTRUCTION_ERROR_HEADER = "Error while cancelling instruction";
  public static final String INSTRUCTION_IS_ALREADY_COMPLETED =
      "This instruction is already completed, so it cannot be cancelled";
  public static final String PARTIAL_INSTRUCTION_CANCEL_ERROR =
      "This instruction is partially received, so it cannot be cancelled";

  // DA Receiving instruction error message
  public static final String DA_RECEIVE_INSTRUCTION_ERROR_MSG =
      "There is an error while completing instruction for DA freight";

  public static final String DSDC_RECEIVE_INSTRUCTION_ERROR_MSG =
      "There is an error while receiving SSCC: %s for DSDC freight";
  public static final String DA_RECEIVE_INSTRUCTION_ERROR_CODE = "DAReceiveInstruction";
  public static final String DA_RTS_PUT_NOT_ALLOWED_IN_WORKSTATION =
      "This is RTS PUT freight, please use handheld in quantity receiving mode.";
  public static final String NO_ALLOCATIONS_FOR_DA_FREIGHT =
      "No Allocations found. Please report this to your supervisor.";
  public static final String ATLAS_DA_SLOTTING_NOT_ALLOWED_FOR_CONVEYABLE_ITEMS =
      "Conveyable DA items can not be slotted, please contact QA to change the handling code for itemNumber: %s if you need to slot this item.";
  public static final String LABEL_COUNT_NOT_MATCHED =
      "Number of labels requested for itemNumber: %s is not available. Please report this to your supervisor.";

  public static final String INVALID_LABEL_STATUS_FOR_HAWKEYE_LPNS =
      "Invalid label status found for the Lpns acquired from Hawkeye for deliveryNumber: %s and itemNumber: %s. Please report this to your supervisor.";
  public static final String LABEL_COUNT_EXCEEDED_FOR_DSDC_SSCC =
      "Number of available labels for scanned SSCC: %s is more thant the expected. Please report this to your supervisor.";
  public static final String NO_ALLOCATIONS_FOR_DSDC_FREIGHT =
      "No Allocations found for DSDC freight. Please report this to your supervisor.";
  public static final String ATLAS_DA_AUTOMATION_SLOTTING_NOT_ALLOWED =
      "Item not allowed for induction to the chosen/entered slot. Please choose another one";
  public static final String DA_QUANTITY_RECEIVING_NOT_ALLOWED =
      "Quantity receiving is not supported for itemNumber:{}. Please receive 1 quantity at a time";
  public static final String ATLAS_DA_CONVENTIONAL_SLOTTING_NOT_ALLOWED_FOR_NON_CON_ITEMS =
      "DA Conventional slotting is not allowed for Non Conveyable item handling codes. Please verify the item handling code before slotting the item.";
  // Receiving utils error code and messages
  public static final String RECEIVING_UTILS_ERROR_CODE = "receivingUtils";
  public static final String RECEIVING_UTILS_UNKNOWN_UOM = "Unknown UOM";

  // Container request validations
  public static final String INVALID_CONTAINER_REQUEST_ERROR_CODE = "invalidContainerRequest";
  public static final String INVALID_CONTAINER_REQUEST_ERROR_MSG = "%s should not be empty or null";
  public static final String INVALID_CONTAINER_TYPE_ERROR_MSG = "Invalid container type";
  public static final String RECEIVE_CONTAINER_ERROR_CODE = "receiveContainer";
  public static final String TENANT_CONFIG_ERROR = "Error while getting tenant specific configs";
  public static final String MARKET_CONFIG_ERROR = "Error while getting market specific configs";

  // FDE error code and messages
  public static final String FDE_RECEIVE_ERROR_CODE = "fdeCreateContainerRequest";
  public static final String FDE_RECEIVE_UNKNOWN_PURCHASE_REF_TYPE =
      "Unknown purchase reference type: %s.";
  public static final String FDE_RECEIVE_FDE_CALL_FAILED =
      "%s. Please try again or report this to your supervisor.";
  public static final String FDE_RECEIVE_NO_MATCHING_CAPABILITY_FOUND =
      "No matching capability found for purchase reference type: %s. Please try again or report this to your supervisor if it continues.";
  public static final String FDE_RECEIVE_EMPTY_PURCHASE_REF_TYPE =
      "No purchase reference type found. Please try again or report this to your supervisor if it continues.";
  public static final String OF_NETWORK_ERROR_MSG =
      "We’re having trouble reaching OF now. Please try again or report this to your supervisor if it continues.";

  // Delivery service error code and messages
  public static final String GDM_ASN_DOC_NOT_FOUND = "No container Found";
  public static final String GDM_SERVICE_DOWN =
      "We’re having trouble reaching GDM now. Please try again or report this to your supervisor if it continues.";
  public static final String GDM_SEARCH_DOCUMENT_ERROR_CODE = "searchDocument";
  public static final String GDM_SEARCH_DELIVERY_ERROR_CODE = "searchDelivery";
  public static final String GDM_SEARCH_HEADER_DETAILS_ERROR_CODE = "searchHeaderDetails";
  public static final String GDM_GET_DELIVERY_BY_URI = "getDeliveryByURI";
  public static final String GDM_GET_DELIVERY_BY_DELIVERY_NUMBER_ERROR_CODE =
      "getDeliveryByDeliveryNumber";

  public static final String GDM_GET_LPN_DETAILS_BY_LPN_NUMBER_ERROR_CODE =
      "getLpnDetailsByLpnNumber";
  public static final String GDM_GET_DELIVERY_BY_STATUS_CODE_ERROR = "getDeliveryByStatusCode";
  public static final String NOT_IMPLEMENTED_EXCEPTION = "Method not implemented.";
  public static final String INVALID_DELIVERY_NUMBER =
      "Delivery number should be a positive number";

  // Complete delivery error code and messages
  public static final String COMPLETE_DELIVERY_ERROR_CODE = "completeDelivery";
  public static final String COMPLETE_DELIVERY_NO_RECEIVING_ERROR_MESSAGE =
      "Can't complete delivery as no receiving happened.";
  public static final String COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE = "OpenInstructions";
  public static final String COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE =
      "There are open pallets that need to be finished before you can complete this delivery. Please finish working those first.";
  public static final String COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE = "UnconfirmedPOs";
  public static final String COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER = "Unconfirmed POs";
  public static final String COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE =
      "There are %s unconfirmed POs. Please confirm all POs to complete the delivery.";
  public static final String COMPLETE_DELIVERY_DEFAULT_ERROR_MESSAGE =
      "Cannot complete delivery %s due to error: %s";

  public static final String DELIVERY_UNLOADER_PUBLISH_DEFAULT_ERROR_MESSAGE =
      "Unable to publish delivery unloader event for delivery %s due to error: %s";

  public static final String DELIVERY_UNLOADER_PERSIST_DEFAULT_ERROR_MESSAGE =
      "Unable to persist unloaderinfo for delivery %s due to error: %s";

  public static final String DELIVERY_UNLOADER_GET_DEFAULT_ERROR_MESSAGE =
      "Unable to get unloaderinfo for delivery %s due to error: %s";
  public static final String UNABLE_TO_GET_DELIVERY_RECEIPTS =
      "Error while fetching delivery receipts for deliveryNumber: %s. ErrorMessage: %s";

  public static final String VENDOR_UPDATE_DATE_BAD_REQUEST =
      "At least one verified date is mandatory";

  // Update instruction error messages
  public static final String UPDATE_INSTRUCTION_ALREADY_COMPLETE =
      "Instruction is already complete";
  public static final String UPDATE_INSTRUCTION_EXCEEDS_QUANTITY = "Update exceeds quantity needed";
  public static final String RECEIVE_INSTRUCTION_EXCEEDS_QUANTITY =
      "Receive exceeds quantity needed";
  public static final String UPDATE_INSTRUCTION_EXCEEDS_PALLET_QUANTITY =
      "All cases are received by one or more instructions, please receive Pallet.";
  public static final String UPDATE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD =
      "Reached maximum receivable quantity threshold for this PO and POLine combination, Please cancel/Finish this pallet.";
  public static final String RECEIVE_INSTRUCTION_REACHED_MAXIMUM_THRESHOLD =
      "Reached maximum receivable quantity threshold for this PO and POLine combination, Please cancel this pallet.";
  public static final String UPDATE_INSTRUCTION_NEAR_OVERAGE_LIMIT =
      "Near overage Limit. You may only receive %s cases for this pallet";
  public static final String RECEIVE_INSTRUCTION_NEAR_OVERAGE_LIMIT =
      "Near overage Limit. You may only receive %s cases for this pallet";
  public static final String NEAR_OVERAGE_LIMIT_ERROR_CODE = "Near overage limit";
  public static final String TOTAL_RECEIVE_QTY_EXCEEDS_OVERAGE_LIMIT = "Exceed overage limit";
  public static final String RECEIVE_INSTRUCTION_EXCEEDS_OVERAGE_LIMIT =
      "Total receive quantity exceeds maximum receivable quantity threshold for this PO: %s and POLine: %s combination, Please cancel/finish this pallet.";
  public static final String RECEIVE_INSTRUCTION_EXCEEDS_SSCC_LIMIT =
      "You cannot receive label %s as it is already received. Please scan UPC to receive the remaining freight";
  public static final String ASN_PO_NO_OPEN_QTY = "Allowed PO Line quantity has been received.";
  public static final String ASN_PO_OVERAGES =
      "The allowed number of cases for this item have been received. Please report the remaining as overage by scanning UPC.";

  public static final String INSTRUCTION_NOT_FOUND = "Instruction does not exist";
  public static final String FREIGHT_ALREADY_RCVD = "Freight already received";

  // error when invalid expiry date
  public static final String INVALID_EXP_DATE_ERROR_CODE = "InvalidExpirationDate";
  public static final String CLOSE_DATE_ERROR_HEADER = "Close date alert";
  public static final String INVALID_EXP_DATE_ERROR_MSG =
      "The expiration date entered, %s, doesn't meet this item's minimum life expectancy threshold of %s and requires supervisor approval to receive.";
  public static final String INVALID_EXP_DATE_FORMAT = "MM/dd/yyyy";
  public static final String EXPIRY_THRESHOLD_DATE = "expiryThresholdDate";
  public static final String EXPIRED_PRODUCT_HEADER = "Expired product";
  public static final String PAST_DATE_MSG =
      "The expiration date entered, %s, is earlier than today's date, %s. Please reject this freight or submit a ticket.";
  public static final String UPDATE_DATE_ERROR_CODE = "DateError";
  public static final String UPDATE_DATE_ERROR_HEADER = "Date alert";
  public static final String UPDATE_DATE_ERROR_MSG =
      "The pack date cannot be in the future. Acceptable dates must be between %s and %s. Please reject this freight or submit a ticket.";
  public static final String UPDATE_DATE_ERROR_MGR_MSG =
      "The pack date doesn't meet the date limit for this item. Acceptable dates must be between %s and %s and requires supervisor approval to receive.";

  public static final String INVALID_ITEM_ERROR_CODE = "InvalidItem";
  public static final String INVALID_ITEM_ERROR_HEADER = "Missing item details";
  public static final String INVALID_ITEM_ERROR_MSG =
      "Item %s cannot be received. Required information is missing in the system. Contact support to help get this resolved.";
  public static final String INVALID_EXP_DATE = "Expiration date should not be empty or null";
  public static final String INVALID_EXPIRY_DATE_ERROR_CODE = "InvalidExpiryDate";
  public static final String INVALID_EXPIRY_DATE_ERROR_MSG = "Not a valid expiration date";
  public static final String WAREHOUSE_MIN_LIFE_NULL_ERROR_CODE = "WarehouseMinLifeNull";
  public static final String WAREHOUSE_MIN_LIFE_NULL_ERROR_MSG =
      "Warehouse Minimum Life Remaining is Null";
  public static final String STORE_MIN_LIFE_NULL_ERROR_CODE = "StoreMinLifeNull";
  public static final String STORE_MIN_LIFE_NULL_ERROR_MSG = "Store Minimum Life Remaining is Null";

  // Problem receiving error messages
  public static final String GET_PTAG_ERROR_CODE = "getProblemTagDetails";
  public static final String RESOLUTION_ON_BPO = "Resolution raised on Bpo";
  public static final String PTAG_NOT_FOUND = "No containers exists";
  public static final String POLINE_NOT_FOUND = "No poline found";
  public static final String PO_POLINE_NOT_FOUND = "No po/poLine found for %s in delivery %s";
  public static final String PTAG_NOT_READY_TO_RECEIVE = "Problem is not ready to receive.";
  public static final String COMPLETE_PTAG_ERROR = "Error while completing problem-tag";
  public static final String FIT_SERVICE_DOWN =
      "We’re having trouble reaching FIT/FIXIT now. Please try again or report this to your supervisor if it continues.";
  public static final String FIXIT_SERVICE_DOWN =
      "We’re having trouble reaching FIXIT now. Please try again or report this to your supervisor if it continues.";
  public static final String PTAG_RESOLVED_BUT_LINE_ALREADY_RECEIVED =
      "Problem is resolved but allowed quantity for this item has been received. Please check if the line adjusted in GDM";
  public static final String PTAG_RESOLVED_BUT_LINE_REJECTED =
      "Problem is resolved but this item has been rejected. Please see a supervisor for assistance with this item.";
  public static final String CREATE_PTAG_ERROR_CODE_FIXIT = "createProblemInFixit";
  public static final String CREATE_PTAG_ERROR_CODE_FIT = "createProblemInFit";
  public static final String CREATE_PTAG_ERROR_MESSAGE_INVALID_QTY =
      "Error while creating problem: Quantity is not valid.";
  public static final String CREATE_PTAG_ERROR_MESSAGE = "Error while creating problem.";
  public static final String REPORT_PROBLEM_ERROR_MESSAGE =
      "Error while reporting overage to Fixit";
  public static final String REPORT_PROBLEM_ERROR_CODE_FIXIT = "reportProblemInFixit";
  public static final String MAX_QUANTITY_REACHED_FOR_PROBLEM =
      "Received maximum allowable quantity threshold for problem label: %s, Please check with your supervisor";
  public static final String MAX_QUANTITY_REACHED_ERROR_HEADER_FOR_PROBLEM =
      "ProblemMaxReceiveQuantityIsReached";
  public static final String MAX_QUANTITY_REACHED_ERROR_FOR_PROBLEM =
      "Maximum allowable quantity threshold reached for problem label: %s";

  // Error headers
  public static final String ERROR_HEADER_PALLET_CANCELLED = "Pallet was cancelled";
  public static final String ERROR_HEADER_PALLET_COMPLETED = "Pallet was completed";
  public static final String RECEIPT_NOT_FOUND =
      "no record's found for this delivery number in receipt table";
  public static final String NO_PRINTJOBS_FOR_DELIVERY =
      "no record's found for this delivery number in printjob table";
  public static final String NO_PRINTJOBS_FOR_INSTRUCTION =
      "no record's found for this instruction id in printjob table";
  public static final String NO_INSTRUCTIONS_FOR_DELIVERY =
      "no record's found for this delivery number in instruction table";
  public static final String NO_INSTRUCTIONS_FOR_MESSAGE_ID =
      "No record's found for this message id in Instruction table";
  public static final String MANUAL_RCV_MANDATORY_FIELD_MISSING =
      "MANUAL_RCV_MANDATORY_FIELD_MISSING";
  // error containers
  public static final String CONTAINER_EXCEEDS_QUANTITY =
      "Quantity entered is more than the available container";
  public static final String MATCHING_CONTAINER_NOT_FOUND = "No matching container found";
  public static final String CONTAINER_IS_NULL = "Container is null";

  // Multi-user error
  public static final String MULTI_USER_ERROR_CODE = "Instruction is not owned by current user";
  public static final String MULTI_USER_ERROR_MESSAGE =
      "Instruction is owned by %s. Please transfer ownership of the instruction before proceeding.";
  public static final String MULTI_USER_ERROR_HEADER_UPDATE =
      "Update instruction with userId that is not owner";
  public static final String MULTI_USER_ERROR_HEADER_COMPLETE =
      "Complete instruction with userId that is not owner";
  public static final String MULTI_USER_ERROR_HEADER_CANCEL =
      "Cancel instruction with userId that is not owner";
  public static final String MULTI_USER_ERROR_HEADER_RECEIVE =
      "Receive instruction with userId that is not owner";
  public static final String MULTI_USER_UNABLE_TO_VERIFY = "Unable to validate user.";
  public static final String MULTI_USER_ITEM_INSTRUCTION_NA_CODE =
      "multiUserItemInstructionNotAvailable";

  public static final String NO_TRANSFERRABLE_INSTRUCTIONS =
      "There are no transferable instructions for users %s on delivery %s.";
  public static final String TRANSFER_ERROR_CODE = "transferInstructionError";
  public static final String TRANSFER_ERROR_HEADER = "Unable to transfer";

  public static final String CANCELLED_PO_INSTRUCTION_ERROR_CODE = "CancelledPOs";
  public static final String CANCELLED_PO_INSTRUCTION_ERROR_HEADER = "PO Cancelled";
  public static final String CANCELLED_PO_INSTRUCTION =
      "The PO/PO Line %s%s for this item %s has been cancelled. Please report this to your supervisor for assistance.";
  public static final String CANCELLED_PO_INSTRUCTION_MORE = " more ";

  // Cancel container error code and messages
  public static final String CONTAINER_NOT_FOUND_ERROR_CODE = "containerNotFound";
  public static final String CONTAINER_NOT_IN_DELIVERY_ERROR_CODE = "containerNotInDelivery";
  public static final String CONTAINER_NOT_FOUND_ERROR_MSG = "Label wasn’t found on this delivery";
  public static final String CONTAINER_ALREADY_CANCELED_ERROR_CODE = "containerAlreadyCanceled";
  public static final String CONTAINER_ALREADY_CANCELED_ERROR_MSG =
      "Label has already been cancelled";
  public static final String CONTAINER_WITH_PARENT_ERROR_CODE = "containerWithParent";
  public static final String CONTAINER_WITH_PARENT_ERROR_MSG =
      "Container is associated to parent container %s. Unable to backout.";

  // Swap cancel container error code and messages
  public static final String SOURCE_CONTAINER_NOT_FOUND =
      "Unable to swap container: %s. Container not found";
  public static final String SOURCE_CONTAINER_NOT_ELIGIBLE =
      "Unable to swap container: %s. Invalid container status.";

  public static final String CONTAINER_ALREADY_SLOTTED_ERROR_CODE = "containerAlreadySlotted";
  public static final String CONTAINER_ALREADY_SLOTTED_ERROR_MSG = "Label has already been slotted";
  public static final String CONTAINER_WITH_CHILD_ERROR_CODE = "containerWithChild";
  public static final String CONTAINER_WITH_CHILD_ERROR_MSG = "Pallet labels cannot be cancelled";
  public static final String CONTAINER_WITH_NO_CONTENTS_ERROR_CODE = "containerWithNoContents";
  public static final String CONTAINER_WITH_NO_CONTENTS_ERROR_MSG =
      "Unable to cancel a container without contents";
  public static final String CONTAINER_ON_UNFINISHED_PALLET_ERROR_CODE =
      "containerOnUnfinishedPallet";
  public static final String CONTAINER_ON_UNFINISHED_PALLET_ERROR_MSG =
      "Container is on unfinished pallet";
  public static final String INVENTORY_SERVICE_DOWN_ERROR_CODE = "inventoryServiceDown";
  public static final String INVENTORY_SERVICE_DOWN_ERROR_MSG =
      "Inventory services are unavailable. Try again.";
  public static final String INVENTORY_ERROR_CODE = "inventoryError";
  public static final String INVENTORY_QUANTITY_ERROR = "Quantity check";
  public static final String INVENTORY_ERROR_MSG = "Unable to update Inventory";
  public static final String INVENTORY_INSUFFICIENT_ERROR_MSG =
      "You entered a quantity that exceeds the amount of inventory available in Outside Storage. Please check the quantity entered to continue or contact your supervisor or QA.";
  public static final String LABEL_ALREADY_ONHOLD_ERROR_CODE = "labelAlreadyOnhold";
  public static final String LABEL_ALREADY_ONHOLD_ERROR_MSG = "Label has already been put on hold";
  public static final String LABEL_ALREADY_OFF_HOLD_ERROR_CODE = "labelAlreadyOffHold";
  public static final String LABEL_ALREADY_OFF_HOLD_ERROR_MSG = "Label is already off hold";
  public static final String LABEL_ON_HOLD_REQUEST_IN_GLS_INSTEAD_OF_ATLAS =
      "This pallet cannot be put on hold in Atlas Receiving. Please use GLS.";

  public static final String DC_FIN_SERVICE_DOWN = "We are having trouble reaching DC Fin now";
  public static final String DC_FIN_POST_RECEIPT_ERROR =
      "Error while posting to DC Fin. DC Fin response {status:%s, body:%s}";
  public static final String CONTAINER_IS_NOT_A_PARENT_CONTAINER_ERROR =
      "Container is not a parent container : %s";
  public static final String CONTAINER_IS_NOT_COMPLETE_ERROR =
      "Container is yet not completed : %s";
  public static final String UNABLE_TO_POST_RECEIPTS_TO_DC_FIN_PARTIAL_ERROR =
      "Unable to post receipts to DC Fin for following containers\\n. {%s}";

  public static final String UNABLE_TO_REOPEN_DELIVERY =
      "Delivery status doesn't allow to reopen delivery";

  public static final String UNABLE_TO_FIND_DELIVERY_TO_REOPEN = "Unable to find delivery in gdm";

  public static final String RE_OPEN_DELIVERY_ERROR =
      "Something went wrong while reopening delivery in GDM";

  public static final String RE_OPEN_DELIVERY_ERROR_CODE = "ReopenDelivery";

  public static final String ERROR_HEADER_REOPEN_DELIVERY_FAILED = "Reopen Delivery Error";

  public static final String NO_RECEIPT_FOUND_FOR_DELIVERY_NUM_PO_REF_PO_LINE_NUM =
      "No matching Receipt found for "
          + "delivery number %1$s, purchase reference number %2$s and purchase reference line number %3$s.So creating Receipt with default values";

  public static final String INVALID_OSDR_REASON_CODES = "Invalid '%1$s' reason code";

  public static final String INSUFFICIENT_OVERAGE_DETAILS = "Insufficient Overage details";

  public static final String INSUFFICIENT_SHORT_DETAILS = "Insufficient Short details";

  public static final String INSUFFICIENT_DAMAGE_DETAILS = "Insufficient Damage details";

  public static final String INSUFFICIENT_REJECT_DETAILS = "Insufficient Reject details";

  public static final String INVALID_REJECT_QUANTITY = "rejectedQty cannot be negative";

  public static final String INSUFFICIENT_CONCEALED_SHORT_DETAILS =
      "Insufficient Concealed Short details";

  public static final String FIND_DELIVERY_ERROR_CODE = "findDelivery";

  public static final String CONFIRM_PO_ERROR_CODE = "confirmPO";

  public static final String RECORD_OSDR_ERROR_CODE = "recordOSDR";

  // Generic exception for get delivery with OSDR
  public static final String GET_DELIVERY_MISSING_DAMAGE_CODE = "Missing Damage Code";
  public static final String GET_DELIVERY_MISSING_CLAIM_TYPE = "Missing Claim Type";
  public static final String GET_DELIVERY_MISSING_DAMAGE_CODE_CLAIM_TYPE =
      "Missing Damage Code and Claim Type";
  public static final String GET_DELIVERY_WITH_OSDR_ERROR =
      "There were issues loading this information. Report this to your supervisor if it continues.";
  public static final String GET_DELIVERY_WITH_OSDR_ERROR_CODE = "GetDeliveryOSDRData";
  public static final String GET_DELIVERY_WITH_OSDR_ERROR_HEADER = "Could not load information";

  public static final String INVALID_PO_NUMBER = "Invalid PO Number";

  public static final String INVALID_UNLOADER_EVENT_TYPE = "Invalid unloader event type";
  public static final String INVALID_LPN_NUMBER = "Invalid LPN Number";
  public static final String LPN_ALREADY_RECEIVED = "This lpn: %s is already received";

  public static final String RECORD_OSDR_FIT_OR_DAMAGE_DATA_OBSOLETE =
      "Change in ProblemQty or DamageQty in FIT or Damage Applications, reload receiving data";

  public static final String PO_ALREADY_FINALIZED = "PO already confirmed, cannot cancel label";

  public static final String PO_PO_LINE_VERSION_MISMATCH =
      "Version mismatch for this Purchase Reference Number and Purchase Reference Line Number";

  public static final String PO_VERSION_MISMATCH =
      "Version mismatch for this Purchase Reference Number. Please try again. If the problem persists, contact a supervisor.";

  public static final String PO_HASH_KEY_MISMATCH =
      "PO Hash Key mismatch for this Purchase Reference Number";

  public static final String TEMPORARY_PALLET_TI_ERROR = "Pallet Ti should be greater than 0";
  public static final String TEMPORARY_PALLET_HI_ERROR = "Pallet Hi should be greater than 0";
  public static final String VERSION_NOT_NULL = "Version cannot be null";

  // Labelling util error messages
  public static final String LABELLING_CREATION_ERROR = "Error while creating label %s";
  public static final String LABELLING_REFRESH_ERROR = "Error while refreshing labelling library";

  public static final String TEMPORARY_PALLET_TIHI_VERSION_ERROR_MESSAGE =
      "The item information you are viewing has changed. Please refresh to view the changes.";
  public static final String TEMPORARY_PALLET_TIHI_VERSION_ERROR_CODE = "palletTiHiVersionMismatch";
  public static final String TEMPORARY_PALLET_TIHI_VERSION_ERROR_HEADER = "Refresh item attributes";

  // added error constants
  public static final String NETWORK_ERROR_HEADER = "Network Error";
  public static final String INVALID_NO_ALLOCATION_HEADER = "Invalid allocations";
  public static final String ERROR_IN_OF = "Error in OF";

  public static final String OF_NETWORK_ERROR = "OF_NETWORK_ERROR";
  public static final String GDM_NETWORK_ERROR = "GDM_NETWORK_ERROR";

  public static final String OVERAGE_ERROR = "OVERAGE_ERROR";
  public static final String MULTI_USER_ERROR = "MULTI_USER_ERROR";
  public static final String MUTLI_USER_ERROR_SPLIT_PALLET = "MUTLI_USER_ERROR_SPLIT_PALLET";
  public static final String MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING =
      "MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING";
  public static final String NO_PURCHASE_REF_TYPE_ERROR = "NO_PURCHASE_REF_TYPE_ERROR";
  public static final String NO_MATCHING_CAPABALITY_ERROR = "NO_MATCHING_CAPABALITY_ERROR";
  public static final String ITEM_NOT_FOUND_ERROR = "ITEM_NOT_FOUND_ERROR";
  public static final String MISSING_ITEM_INFO_ERROR = "MISSING_ITEM_INFO_ERROR";
  public static final String MISSING_DSDC_INFO_ERROR = "MISSING_DSDC_INFO_ERROR";
  public static final String NEW_ITEM_ERROR = "NEW_ITEM_ERROR";
  public static final String PO_FINALIZED_ERROR = "PO_FINALIZED_ERROR";
  public static final String PO_LINE_REJECTION_ERROR = "PO_LINE_REJECTION_ERROR";
  public static final String MISSING_FREIGHT_BILL_QTY = "MISSING_FREIGHT_BILL_QTY";
  public static final String NO_UPC_ERROR = "NO_UPC_ERROR";
  public static final String PO_POL_NOT_FOUND_ERROR = "PO_POL_NOT_FOUND_ERROR";
  public static final String OF_GENERIC_ERROR = "OF_GENERIC_ERROR";
  public static final String ITEM_NOT_CONVEYABLE_ERROR = "ITEM_NOT_CONVEYABLE_ERROR";
  public static final String ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE =
      "ITEM_NOT_CONVEYABLE_ERROR_FOR_AUTO_CASE_RECEIVE";
  public static final String PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE =
      "PO_CON_NOT_ALLOWED_FOR_AUTO_CASE_RECEIVE";
  public static final String DSDC_FEATURE_FLAGGED_ERROR = "DSDC_FEATURE_FLAGGED_ERROR";
  public static final String DSDC_PO_INFO_ERROR = "DSDC_PO_INFO_ERROR";
  public static final String POCON_FEATURE_FLAGGED_ERROR = "POCON_FEATURE_FLAGGED_ERROR";
  public static final String POCON_PO_INFO_ERROR = "POCON_PO_INFO_ERROR";
  public static final String INVALID_LPN_ERROR = "INVALID_LPN_ERROR";

  public static final String VENDOR_COMPLAINT_ITEM_MISSING = "VENDOR_COMPLAINT_ITEM_MISSING";

  public static final String BAD_REQUEST = "BAD_REQUEST";

  // Slotting Validtion Error messages
  public static final String SLOTTING_VALIDATION_ERROR_MESSAGES = "messages cannot be empty";
  public static final String SLOTTING_VALIDATION_ERROR_TYPE = "type cannot be empty or blank";
  public static final String SLOTTING_VALIDATION_ERROR_CODE = "code cannot be empty or blank";
  public static final String SLOTTING_VALIDATION_ERROR_DESC = "desc cannot be empty or blank";
  public static final String SLOTTING_VALIDATION_ERROR_MESSAGEID =
      "messageId cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_DOORNUMBER =
      "doorNumber cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_CONTAINER =
      "slotting container cannot be null";
  public static final String SLOTTING_VALIDATION_ERROR_CONTENTS =
      "slotting container contents cannot be empty";
  public static final String SLOTTING_VALIDATION_ERROR_CONTAINERSTATUS =
      "containerStatus cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_CONTAINERTRACKINGID =
      "containerTrackingId cannot be null or blank";
  public static final String SMART_SLOTTING_NOT_ENABLED_FOR_ATLAS_DA_ITEMS =
      "Smart Slotting not enabled for Atlas DA items";
  public static final String SLOTTING_VALIDATION_ERROR_QTY = "UpdateQty cannot be null or blank";

  public static final String SLOTTING_VALIDATION_ERROR_ITEMNBR = "itemNbr cannot be null";
  public static final String SLOTTING_VALIDATION_ERROR_GTIN = "gtin cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_PROFILEDWAREHOUSEAREA =
      "profiledWarehouseArea cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_GROUPCODE =
      "groupCode cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_WAREHOUSEAREACODE =
      "warehouseAreaCode cannot be null or blank";
  public static final String SLOTTING_VALIDATION_ERROR_DIVERTLOCATION =
      "divertLocation cannot be null or blank";

  public static final String SLOTTING_VALIDATION_ERROR_CONTAINERDETAILS =
      "containerDetails cannot be blank";
  public static final String SLOTTING_VALIDATION_ERROR_CONTAINERITEMDETAILS =
      "containerItemDetails cannot be blank";
  public static final String SLOTTING_VALIDATION_ERROR_RXDIVERTLOCATIONS =
      "locations cannot be null or blank";

  public static final String PRE_GEN_DATA_NOT_FOUND =
      "Pre generated data for delivery and UPC not found";
  public static final String UNABLE_TO_PUBLISH = "Exception while publishing data";

  public static final String GDM_BAD_RESPONSE = "No mapped Pack to PO/Line association.";
  public static final String GDM_RESPONSE_MAPPING_ERROR =
      "Exception while parsing response V3 to V2";
  public static final String GDM_RESPONSE_PARSE_ERROR =
      "Exception while parsing response to V2 to json";
  public static final String CHANNEL_METHOD_UNKNOWN = "Channel method not found for {}";
  public static final String EPCIS_SERIALIZED_CODE_INVALID =
      "Serialized data is invalid or not present in EPCIS";
  public static final String NO_SSCC_ERROR = "NO_SSCC_ERROR";
  public static final String NOT_ENOUGH_SERIALIZED_DATA =
      "No Valid data has been provided to validate against EPCIS";
  public static final String SERIALIZED_FIELDS_NOT_AVAILABLE =
      "Enough Serialized data not present in Request";
  public static final String ALLOWED_CASES_RECEIVED = "ALLOWED_CASES_RECEIVED";
  public static final String INSERT_CONTAINER_FAILED =
      "Unable to process container, case might have been received already.";
  public static final String PROBLEM_ITEM_DOES_NOT_MATCH =
      "Scanned item did not match with Problem ticket. Please scan the same item matching Problem.";
  public static final String PROBLEM_TICKET_MISSING_ITEM =
      "Item number is missing in the problem ticket. Add the item number in the problem ticket using FIXit to proceed.";

  public static final String PROBLEM_NOT_FOUND =
      "Problem Not found in Receiving. Please scan valid Problem ticket to continue.";

  public static final String INVLID_D40_RECEIVING_FLOW_DESC =
      "Scan the UPC for this item to continue.";
  public static final String MULTI_SKU_CASE_EPCIS_FLOW =
      "The scanned case has multiple SKUs, please receive using partial case flow.";
  public static final String MULTI_LOTS_CASE_EPCIS_FLOW =
      "The scanned case has multiple LOTs, please receive using partial case flow.";
  public static final String EPCIS_DATA_UNAVAILABLE =
      "EPCIS validation is unavailable for this barcode. Scan the case or unit directly to receive. Otherwise, quarantine this freight.";

  public static final String CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_CODE =
      "CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS";
  public static final String CANCEL_LABEL_NOT_ALLOWED_BY_DELIVERY_STATUS_ERROR_MESSAGE =
      "Delivery Status in GDM doesn't allow to cancel this Label";
  public static final String LABEL_QUANTITY_ADJUSTMENT_ERROR_MSG_FOR_FINALIZED_DELIVERY =
      "Label correction/cancellation is not allowed as delivery is Finalized. Please reopen the delivery and try again";
  public static final String
      CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_LESS_THAN_A_CASE_RECEIVED_CONTAINER =
          "Label correction/cancellation is not allowed for Less than a case received container.Please contact your supervisor.";
  public static final String CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DSDC_CONTAINER =
      "Label correction/cancellation is not allowed for DSDC container. Please contact your supervisor.";
  public static final String CONTAINER_ADJUSTMENT_NOT_ALLOWED_FOR_DA_CONTAINER =
      "Label correction/cancellation is not allowed for DA container. Please contact your supervisor.";
  public static final String DELIVERY_STATE_NOT_RECEIVABLE =
      "This delivery can not be received as the status is in %s in GDM .Please contact your supervisor";
  public static final String DELIVERY_STATE_NOT_RECEIVABLE_REOPEN =
      "This delivery is in %s status in GDM. Please reopen this delivery by entering the delivery number";
  public static final String DELIVERY_DOC_MISSING_WGT_CUBE =
      "The scanned item, UPC: %s number: %s does not have weight and cube";

  public static final String HACCP_ITEM_ALERT = "HACCP_ITEM_ALERT";
  public static final String MISSING_ITEM_DETAILS = "MISSING_ITEM_DETAILS";
  public static final String RCV_AS_CORRECTION_ERROR = "RCV_AS_CORRECTION_ERROR";
  public static final String INVALID_TI_HI = "INVALID_TI_HI";
  public static final String PO_POL_CANCELLED_ERROR = "PO_POL_CANCELLED_ERROR";
  public static final String PO_LINE_CLOSED_ERROR = "PO_LINE_CLOSED_ERROR";

  public static final String NO_RECEIPTS_FOUND_ERROR_MESSAGE =
      "No delivery or receipts found for delivery number: %s";
  public static final String D38_PALLET_CORRECTION_NOT_ALLOWED =
      "Pallet Correction for D38 Items are not allowed.";

  public static final String INVALID_PALLET_CORRECTION_QTY =
      "Invalid correction quantity received. Pallet Correction quantity should be greater than zero.";

  public static final String EXCEED_WHITE_WOOD_PALLET_WIGHT =
      "Weight exceeds the limit for this pallet type. Please select and transfer cases to another type of pallet.";
  public static final String INVALID_WEIGHT_ERROR_CODE = "InvalidWeight";
  public static final String INVALID_WEIGHT_ERROR_HEADER = "Invalid weight on pallet";

  public static final String MULTIPLE_ITEM_FOUND_BY_UPC =
      "The scanned upc: %s is mapped against multiple items. Please use NGR app to receive this item";
  public static final String NO_ACTIVE_PO_LINES_TO_RECEIVE =
      "There is no active PO lines to receive for delivery: %s and UPC: %s";

  public static final String ITEM_CONFIG_ITEM_SEARCH_ERROR =
      "Error while checking atlas converted items. Error Message = %s";
  public static final String ITEM_CONFIG_ERROR = "ITEM_CONFIG_ERROR";
  public static final String UPC_CATALOG_NODE_RT_ERROR =
      "Error updating upc catalog to node rt. Error Message = %s";

  public static final String NEW_ITEM_ERROR_MSG =
      "Item No. %d cannot be received until item setup has been completed. Please contact QA team to resolve this.";
  public static final String NEW_ITEM_ERROR_HEADER = "Item Setup Required";

  public static final String TRAILER_TEMPERATURE_NOT_FOUND_ERROR_MESSAGE =
      "No Trailer temperature found for %s";

  public static final String INVALID_MANIFEST_FILE_CODE = "INVALID_MANIFEST_FILE";

  public static final String INVALID_MANIFEST_FILE_ERROR = "Error while reading MANIFEST FILE";
  public static final String NO_ACTIVE_PO_AVAILABLE_TO_RECEIVE =
      "There is no active PO available to receive for UPC: %s and delivery: %s combination";

  public static final String INVALID_PALLET_CORRECTION_TRACKING_IDS =
      "Invalid tracking ids received. Rollback Tracking Ids should be associated to parent tracking id.";

  public static final String DELIVERY_DOCUMENT_NOT_FOUND_FOR_DELIVERY_PO_POL_ERROR =
      "Delivery document not found in GDM for the given delivery: %s, PO: %s and POL: %s";

  public static final String PROBLEM_RESOLUTION_DOC_DOCLINE_NOT_FOUND_IN_DELIVERY_ERROR =
      "Problem resolution PO and POL not found in GDM for the given delivery";

  public static final String PRODATE_CONVERSION_ERROR = "Error in converting proDate value";
  public static final String ITEM_DETAILS_NOT_FOUND_IN_RDS =
      "Item details not found in RDS for item: %s and error message: [%s]";

  public static final String THREE_SCAN_DOCKTAG_MIXED_ITEM_ERROR_MSG = "Item is %s";
  public static final String THREE_SCAN_DOCKTAG_DSDC_SCAN_UPC_ERROR_MSG =
      "Scanned UPC is Type 73 DSDC. Please scan a SSCC for DSDC freight.";
  public static final String THREE_SCAN_DOCKTAG_HISTORY_PO_ERROR_MSG =
      "Item: %s belongs to a History PO, please report this to your supervisor for assistance.";
  public static final String THREE_SCAN_DOCKTAG_CANCELLED_POL_ERROR_MSG =
      "Item: %s  is either cancelled or rejected, please report this to your supervisor for assistance.";
  public static final String THREE_SCAN_DOCKTAG_XBLOCK_ITEM_ERROR_MSG =
      "Item: %s is showing as blocked, please report this to your supervisor for assistance.";
  public static final String THREE_SCAN_DOCKTAG_FTS_ITEM_ERROR_MSG =
      "Item: %s is showing as FTS, please report this to your supervisor for assistance.";
  public static final String THREE_SCAN_DOCKTAG_MAX_ALREADY_RECEIVED_ERROR_MSG =
      "Item: %s has no available quantity to receive against, please remove the cases before proceeding with creating the dock tag.";
  public static final String GLS_DELIVERY_ERROR_MSG =
      "This delivery will need to be received in GLS";

  public static final String VTR_ERROR_CODE = "vtrError";
  public static final String VTR_ERROR_MSG = "Unable to back out container";
  public static final String VTR_MOVE_INVALID_STATUS_CODE = "Invalid Move status";
  public static final String VTR_MOVE_INVALID_STATUS_MSG =
      "Pallet has been moved off of the dock and cancel pallet can not be performed.";

  public static final String GLS_SERVICE_DOWN_CODE = "glsDeliveryDetailsDown";
  public static final String GLS_SERVICE_DOWN_MSG =
      "We’re having trouble reaching GLS now. Please try again or report this to your supervisor if it continues.";

  public static final String MAX_ALLOWED_TIME_FOR_CONTAINER_BACKOUT_EXPIRED =
      "Case can not have a VTR completed because it is older than the set time to allow this process. "
          + "Please adjust if needed using another process.";

  public static final String INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_CODE =
      "INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT";
  public static final String INVALID_CHANNEL_METHOD_FOR_LABEL_BACKOUT_MSG =
      "This Channel method: %s is not supported now for label backout";
  public static final String ADJUST_PALLET_QUANTITY_ERROR_MSG_PO_NOT_FINALIZE_DCFIN =
      "Please try once DcFin confirms the PO %s closure.";
  public static final String RDS_DSDC_RECEIVE_VALIDATION_ERROR_MSG =
      "Unable to receive DSDC pack: %s in RDS. Error Message: %s.";

  public static final String GDM_UPDATE_STATUS_API_ERROR =
      "Unable to update pack status in GDM . Please try again";

  public static final String GDM_UPDATE_STATUS_API_ERROR_HEADER = "Unable to update";
  public static final String GDM_UPDATE_STATUS_API_ERROR_CODE = "GDMPackUpdateStatusError";

  public static final String NO_SCANNED_CASE_OR_EACHES_TO_PUBLISH =
      "No Scanned case or eaches available to publish";
  public static final String UOM_MISSING = "No UOM provided";
  public static final String FIXIT_EVENT_DIPOSITION_MISSING = "No disposition provided";
  public static final String FIXIT_EVENT_EXCEPTIONINFO_MISSING = "No exceptioninfo provided";
  public static final String FIXIT_EVENT_REASON_CODE_MISSING = "No reasonCode provided";
  public static final String FIXIT_EVENT_VENDOR_MISSING = "No vendor Number provided";
  public static final String FIXIT_EVENT_PO_MISSING = "No purchaseReference provided";

  public static final String FIXIT_EVENT_SHIPMENT_NUMBER_MISSING = "No Shipment Number provided";

  public static final String FIXIT_EVENT_DOCUMENT_ID_MISSING = "No Document Id provided";

  public static final String FIXIT_EVENT_DOCUMENT_PACK_ID_MISSING = "No Document Pack ID provided";

  public static final String GET_STORE_DISTR_ERROR_MESSAGE =
      "Error occurred while fetching Store Distribution for Po Number: %s. Error message [%s]";
  public static final String STORE_DISTRIBUTION_SERVER_ERROR =
      "storeDistributionByDeliveryNumberPoLinePoLineNumber";

  public static final String INVENTORY_BOH_ERROR =
      "Unable to verify BOH quantity from Inventory for item %s, please contact your supervisor or QA.";

  public static final String INVENTORY_INSUFFICIENT_BOH_ERROR =
      "No inventory available in Outside Storage for item %s, please contact your supervisor or QA";

  public static final String OVERAGE_ERROR_OSS =
      "The Quantity entered to receive exceeds the Out Side Storage Balance on Hand of %s.";

  public static final String OSS_TRANSFER_PO_FINALIZED_CORRECTION_ERROR =
      "Receiving correction not allowed on Outside Storage PO's";
  public static final String OSS_TRANSFER_PO_VTR_ERROR = "VTR not allowed on Outside Storage PO's";
  public static final String INVALID_OSS_TRANSFER_PO_ERROR =
      "subcenter info missing on Outside Storage PO";

  public static final String INVALID_SUBCENTER_ID = "INVALID_SUBCENTER_ID";
  public static final String ITEM_CONFIG_ERROR_CODE = "itemConfig";
  public static final String ITEM_NOT_CONVERTED_TO_ATLAS_ERROR_MSG =
      "Item is not converted to Atlas";
  public static final String ITEM_CONFIG_SERVICE_NOT_ENABLED_ERROR_MSG =
      "Item Config Service is not enabled. Please contact support";
  public static final String GDM_RECEIVE_EVENT_ERROR_CODE = "gdmReceiveEvent";
  public static final String GDM_GET_LPN_LIST = "gdmGetLPNList";
  public static final String PENDING_RECEIVING_LPN =
      "%s Two Tier LPNs are still pending receiving for item with UPC: %s."
          + " Please receive all Two Tier freight for this item first to continue";
  public static final String LABELS_NOT_FOUND = "Labels are not generated for delivery %s";
  public static final String DELIVERY_NUMBER_NOT_FOUND = "Delivery number not found";
  public static final String GLS_RCV_ITEM_IS_NOT_ATLAS_SUPPORTED =
      "Item: %s with packTypeHandlingCode: %s is not supported in Atlas. Please contact your supervisor to change the item handling code to proceed.";
  public static final String DSDC_SSCC_ALREADY_RECEIVED =
      "SSCC - %s already received with container Id: %s. Please follow reprint options.";

  public static final String INTERRUPTED_EXCEPTION_ERROR =
      "Interrupted exception occurred while fetching container details for missing tracking ids Exception message: {} for tracking ids {}";
  public static final String RECEIVING_CONTAINER_DETAILS_ERROR_MESSAGE =
      "Error occurred while fetching item details from iqs and location details for transaction ids {}, Error {}";
  public static final String RECEIVING_LABEL_PREPARATION_ERROR_MESSAGE =
      "Error occurred while preparing label data";

  public static final String SSCC_NOT_FOUND = "SSCC and ASN combination does not exist";
  public static final String SSCC_ALREADY_RECEIVED = "SSCC: %s already received.";

  // Iqs service error code and messages
  public static final String IQS_ITEM_NOT_FOUND = "No Item Found";
  public static final String IQS_SERVICE_DOWN =
      "We’re having trouble reaching IQS now. Please try again or report this to your supervisor if it continues.";
  public static final String IQS_SEARCH_ITEM_ERROR_CODE = "searchIqsItem";

  public static final String CONTAINER_NOT_FOUND = "Container not found %s";
  public static final String GDM_SSCC_SCAN_NOT_FOUND_ERROR_CODE = "GLS-RCV-GDM-SSCC-SCAN-500";

  public static final String ORDERWELL_ERROR_MESSAGE =
      "Error Occured while getting Order by Zone Distribution from Order Well"
          + "for delivery number %s.and for Store Number %s";

  public static final String ORDERWELL_ERROR_CODE = "orderWellSummaryByPoBuNbr";

  public static final String INVALID_PO_TYPE_FOR_DA_ORDERS =
      "Invalid PO Type  %s for DA Orders [%s]";

  public static final String ORDERWELL_INVALID_POTYPE_FOR_DA_ORDER =
      "orderWellInvalidPotypeForDaOrder";

  public static final String ORDERWELL_INVALID_RESPONSE =
      "Having more than 1 Store and MFC distrubutions from Order Well"
          + "for delivery number %s. Error Message [%s]";

  public static final String ORDERWELL_INVALID_RESPONSE_ERROR_CODE = "orderWellSummaryByPoBuNbr";

  public static final String ORDERWELL_ORDERED_ERROR_MESSAGE =
      "Error occurred while preparing Ordered Qty data from OrderWell ";

  public static final String PROBLEM_TAG_FOUND_FOR_SCANNED_UPC_ERROR_CODE =
      "Problem tag found for upc: %s, Please scan the problem tag first";

  public static final String DA_NON_ATLAS_ITEM_HANDLING_CODE_NOT_SUPPORTED =
      "Item %s has an invalid handling method code. Have the QA team resolve this before receiving.";

  public static final String ATLAS_DA_SLOTTING_AND_PALLET_PULL_NOT_ALLOWED_FOR_PACK_HANDLING_CODES =
      "This Item %s with handling code %s is not authorized for slotting or pallet pull. Please contact QA manager to change handling code if you still need to slot this item.";

  public static final String ATLAS_DA_SLOTTING_NOT_SUPPORTED_HANDLING_CODES =
      "Item can not be slotted with current handling method {0}, please contact QA to change the handling code for itemNumber:{1} if you need to slot this item";

  public static final String RESOLUTION_BPO_NOT_ALLOWED =
      "Resolution cannot be raised on booking po number";

  // GDM hints exceptions
  public static final String HINT_PERFORM_UNIT_RCV = "Perform receiving by Unit scan (partial-case receiving).";
  public static final String HINT_SCAN_CASES = "Package has a partial case, scan individual cases.";
  public static final String HINT_SCAN_CASE_2D = "Package has a 2D barcode, scan the case 2D barcode.";
  public static final String CASE_RECEIVING_NOT_ALLOWED = "Case receiving not allowed for this item, scan the pallet.";
  public static final String UNIT_RECEIVING_NOT_ALLOWED = "Unit receiving not allowed for this item, scan the pallet.";
  public static final String NO_RECEIVING_TYPE_SPECIFIED = "No Receiving type in the request from client";
  public static final String UNIT_2D_NOT_ALLOWED =
          "Unit 2D scan not allowed. Receive through partial flow.";
  public static final String BARCODE_PARTIALLY_RECEIVED =
          "Some cases/units have been already Received. Please scan individual cases/units to receive the remaining";
  public static final String BARCODE_UNABLE_TO_SCAN =
          "Full Pallet can't be received. Please scan individual cases/units to receive.";
  public static final String SCAN_CASES_OR_UNITS =
          "Please scan individual cases/units to receive.";
  public static final String PERFORM_PALLET_RECEIVING = "Only Pallet receiving is allowed for this item, scan the pallet sscc.";

  public ReceivingException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  @Builder
  public ReceivingException(HttpStatus httpStatus, ErrorResponse errorResponse) {
    super(String.valueOf(errorResponse.getErrorMessage()));
    this.httpStatus = httpStatus;
    this.errorResponse = errorResponse;
  }

  public ReceivingException(Object errorMessage, HttpStatus httpStatus, String errorCode) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorCode, errorMessage);
  }

  public ReceivingException(
      Object errorMessage, HttpStatus httpStatus, String errorCode, String errorHeader) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorCode, errorMessage, errorHeader);
  }

  public ReceivingException(
      Object errorMessage,
      HttpStatus httpStatus,
      String errorCode,
      String errorHeader,
      Object object) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorCode, errorMessage, errorHeader, object);
  }

  public ReceivingException(
      Object errorMessage,
      HttpStatus httpStatus,
      String errorCode,
      String errorHeader,
      Object errorInfo,
      String errorKey,
      Object... values) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse =
        new ErrorResponse(errorCode, errorMessage, errorHeader, errorInfo, errorKey, values);
  }
  /**
   * This constructor returns an error response that qualifies for overage reporting to keep the
   * error responses same
   *
   * @param errorMessage
   * @param httpStatus
   * @param errorCode
   * @param rcvdqtytilldate
   * @param maxReceiveQty
   */
  public ReceivingException(
      Object errorMessage,
      HttpStatus httpStatus,
      String errorCode,
      int rcvdqtytilldate,
      int maxReceiveQty,
      DeliveryDocument deliveryDocument) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse =
        new RangeErrorResponse(
            errorCode, errorMessage, rcvdqtytilldate, maxReceiveQty, deliveryDocument);
  }

  /**
   * Range exception thrown in update instruction flow
   *
   * @param errorMessage
   * @param httpStatus
   * @param errorCode
   * @param quantityCanBeReceived
   */
  public ReceivingException(
      Object errorMessage, HttpStatus httpStatus, String errorCode, int quantityCanBeReceived) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new RangeErrorResponse(errorCode, errorMessage, quantityCanBeReceived);
  }

  public ReceivingException(String errorMessage) {
    super(String.valueOf(errorMessage));
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public ErrorResponse getErrorResponse() {
    return this.errorResponse;
  }
}
