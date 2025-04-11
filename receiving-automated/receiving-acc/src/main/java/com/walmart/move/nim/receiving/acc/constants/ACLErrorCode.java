package com.walmart.move.nim.receiving.acc.constants;

import java.util.HashMap;
import java.util.Map;

public final class ACLErrorCode {

  private ACLErrorCode() {}

  private static final Map<Integer, ACLError> errorCodeValueMap = new HashMap<>();

  static {
    errorCodeValueMap.put(3, ACLError.HOST_LATE);
    errorCodeValueMap.put(8, ACLError.GAPPING_ERROR);
    errorCodeValueMap.put(9, ACLError.CARTON_TOO_SHORT);
    errorCodeValueMap.put(10, ACLError.CARTON_TOO_LONG);
    errorCodeValueMap.put(11, ACLError.HEIGHT_ERROR);
    errorCodeValueMap.put(12, ACLError.SKEW_DIMENSION_ERROR);
    errorCodeValueMap.put(13, ACLError.CARTON_SIDE_BY_SIDE);
    errorCodeValueMap.put(14, ACLError.NO_READ_INDUCT);
    errorCodeValueMap.put(15, ACLError.NO_DATA);
    errorCodeValueMap.put(16, ACLError.MAX_SCANNER_ERR);
    errorCodeValueMap.put(17, ACLError.HOST_BOX_STS);
    errorCodeValueMap.put(18, ACLError.NO_DIM_DATA);
    errorCodeValueMap.put(26, ACLError.LABELER_OFFLINE);
    errorCodeValueMap.put(28, ACLError.PRINT_ENGINE_POWER);
    errorCodeValueMap.put(29, ACLError.ACL_BYPASS);
    errorCodeValueMap.put(34, ACLError.TRACKING);
    errorCodeValueMap.put(35, ACLError.LOST_BOX);
    errorCodeValueMap.put(36, ACLError.MAX_TRACKING_ERR);
    errorCodeValueMap.put(37, ACLError.STRAY_BOX);
    errorCodeValueMap.put(42, ACLError.OUT_OF_SEQUENCE);
    errorCodeValueMap.put(43, ACLError.NO_READ_VERIFY);
    errorCodeValueMap.put(44, ACLError.NO_DATA_VERIFY);
    errorCodeValueMap.put(45, ACLError.MAX_VERIFY_ERR);
    errorCodeValueMap.put(50, ACLError.MAX_HOST_ERR);
    errorCodeValueMap.put(51, ACLError.HOST_LINK_DOWN);
    errorCodeValueMap.put(52, ACLError.NO_VER_ACK);
    errorCodeValueMap.put(56, ACLError.UNDERSPEED_ERROR);
    errorCodeValueMap.put(57, ACLError.MOTOR_OVERLOAD);
    errorCodeValueMap.put(58, ACLError.JAM);
    errorCodeValueMap.put(59, ACLError.ESTOP_ERROR);
    errorCodeValueMap.put(62, ACLError.TEST_MODE);
    errorCodeValueMap.put(63, ACLError.SCAN_TEST_MODE);
    errorCodeValueMap.put(1001, ACLError.NONCON_SSTK);
    errorCodeValueMap.put(1002, ACLError.INVALID_REQUEST);
    errorCodeValueMap.put(1003, ACLError.NO_DATA_ASSOC);
    errorCodeValueMap.put(1004, ACLError.BLOCKED_ITEM);
    errorCodeValueMap.put(1005, ACLError.MAX_OVERAGE_LIMIT);
    errorCodeValueMap.put(1006, ACLError.BREAKOUT);
    errorCodeValueMap.put(1007, ACLError.MASTERPACK);
    errorCodeValueMap.put(1008, ACLError.PROBLEM_ASN);
    errorCodeValueMap.put(1009, ACLError.NO_OVERAGE_LABEL);
    errorCodeValueMap.put(1010, ACLError.SYSTEM_ERROR);
    errorCodeValueMap.put(1011, ACLError.UPC_NOT_FOUND);
    errorCodeValueMap.put(1012, ACLError.NO_BARCODE_READ);
  }

  public static ACLError getErrorValue(Integer code) {
    return errorCodeValueMap.get(code);
  }
}
