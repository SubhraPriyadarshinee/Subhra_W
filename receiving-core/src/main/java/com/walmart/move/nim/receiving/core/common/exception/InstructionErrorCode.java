package com.walmart.move.nim.receiving.core.common.exception;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;

import java.util.HashMap;
import java.util.Map;

public class InstructionErrorCode {

  private static final Map<String, InstructionError> errorValueMap = new HashMap<>();

  static {
    errorValueMap.put("GLS-OF-BE-00012", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00013", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00024", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00031", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00037", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00044", InstructionError.PBYL_DOCKTAG_NOT_PRINTED);
    errorValueMap.put("GLS-OF-BE-00045", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00046", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00047", InstructionError.OF_SPECIFIC_ERROR);
    errorValueMap.put("GLS-OF-BE-00010", InstructionError.INVALID_ALLOCATION);
    errorValueMap.put("GLS-OF-BE-00009", InstructionError.NO_ALLOCATION);
    errorValueMap.put("GLS-OF-BE-00035", InstructionError.CHANNEL_FLIP);
    errorValueMap.put("GLS-OF-BE-00021", InstructionError.OF_OP_FETCHING_ERROR);
    errorValueMap.put("GLS-OF-BE-00020", InstructionError.OF_OP_FETCHING_ERROR);
    errorValueMap.put("GLS-OF-BE-00039", InstructionError.OF_OP_BLOCKING_ERROR);
    errorValueMap.put("GLS-OF-BE-00032", InstructionError.OF_YMS_ERROR);
    errorValueMap.put("OF_NETWORK_ERROR", InstructionError.OF_NETOWRK_ERROR);
    errorValueMap.put("OVERAGE_ERROR", InstructionError.OVERAGE_ERROR);
    errorValueMap.put("MULTI_USER_ERROR", InstructionError.MUTLI_USER_ERROR);
    errorValueMap.put("RX_MUTLI_USER_ERROR", InstructionError.RX_MUTLI_USER_ERROR);
    errorValueMap.put(
        "MUTLI_USER_ERROR_SPLIT_PALLET", InstructionError.MUTLI_USER_ERROR_SPLIT_PALLET);
    errorValueMap.put(
        "MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING",
        InstructionError.MUTLI_USER_ERROR_FOR_PROBLEM_RECEIVING);
    errorValueMap.put("NO_PURCHASE_REF_TYPE_ERROR", InstructionError.NO_PURCHASE_REF_TYPE_ERROR);
    errorValueMap.put(
        "NO_MATCHING_CAPABALITY_ERROR", InstructionError.NO_MATCHING_CAPABALITY_ERROR);
    errorValueMap.put("NEW_ITEM_ERROR", InstructionError.NEW_ITEM_ERROR);
    errorValueMap.put("NO_UPC_ERROR", InstructionError.NO_UPC_ERROR);
    errorValueMap.put("OF_GENERIC_ERROR", InstructionError.OF_GENERIC_ERROR);
    errorValueMap.put("SLOTTING_GENERIC_ERROR", InstructionError.SLOTTING_GENERIC_ERROR);
    errorValueMap.put("INVALID_BOL_WEIGHT_ERROR", InstructionError.INVALID_BOL_WEIGHT_ERROR);
    errorValueMap.put("MISSING_BOL_WEIGHT_ERROR", InstructionError.MISSING_BOL_WEIGHT_ERROR);
    errorValueMap.put("ITEM_NOT_ON_BOL_ERROR", InstructionError.ITEM_NOT_ON_BOL_ERROR);
    errorValueMap.put("INVALID_LPN_ERROR", InstructionError.INVALID_LPN_ERROR);
    errorValueMap.put("INVALID_PO_ERROR", InstructionError.INVALID_PO_ERROR);
    errorValueMap.put("DSDC_FEATURE_FLAGGED_ERROR", InstructionError.DSDC_FEATURE_FLAGGED_ERROR);
    errorValueMap.put("POCON_FEATURE_FLAGGED_ERROR", InstructionError.POCON_FEATURE_FLAGGED_ERROR);
    errorValueMap.put(
        "MANUAL_RCV_MANDATORY_FIELD_MISSING", InstructionError.MANUAL_RCV_MANDATORY_FIELD_MISSING);
    errorValueMap.put(RCV_AS_CORRECTION_ERROR, InstructionError.RCV_AS_CORRECTION_ERROR);
    errorValueMap.put(MISSING_ITEM_DETAILS, InstructionError.MISSING_ITEM_DETAILS);
    errorValueMap.put(HACCP_ITEM_ALERT, InstructionError.HACCP_ITEM_ALERT);
    errorValueMap.put("ITEM_DATA_MISSING", InstructionError.ITEM_DATA_MISSING);
    errorValueMap.put(
        "WEIGHT_FORMAT_TYPE_CODE_MISSING", InstructionError.WEIGHT_FORMAT_TYPE_CODE_MISSING);
    errorValueMap.put(
        "WEIGHT_FORMAT_TYPE_CODE_MISMATCH", InstructionError.WEIGHT_FORMAT_TYPE_CODE_MISMATCH);
    errorValueMap.put("PO_ITEM_PACK_ERROR", InstructionError.PO_ITEM_PACK_ERROR);
    errorValueMap.put(INVALID_TI_HI, InstructionError.INVALID_TI_HI);
    errorValueMap.put(INVALID_SUBCENTER_ID, InstructionError.INVALID_SUBCENTER_ID);
  }

  public static InstructionError getErrorValue(String errorCode) {
    return errorValueMap.get(errorCode);
  }
}
