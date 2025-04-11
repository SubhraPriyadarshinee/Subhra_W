package com.walmart.move.nim.receiving.acc.constants;

public enum ACLError {
  HOST_LATE(
      3,
      "Communication between BOSS and GLS is slow. Solution: If the problem persists, contact maintenance.",
      2),
  GAPPING_ERROR(
      8,
      "The gap between cases is too small or loose tape is present. Solution: Remove case in Zone 1, check for and remove loose tape if present, and re-induct.",
      1),
  CARTON_TOO_SHORT(
      9,
      "Case too short - Solution: First, validate case is conveyable by scanning the case via receiving application. If case shows conveyable, then create a ticket via FIXIT application and contact QA. If case shows non-conveyable, remove case from conveyor, palletize, receive and label via DA_NC process.",
      1),
  CARTON_TOO_LONG(
      10,
      "Case too long. Solution: First, validate case is conveyable by scanning the case via receiving application. If case shows conveyable, then create a ticket via FIXIT application and contact QA. If case shows non-conveyable, remove case from conveyor, palletize, receive and label via DA_NC process.",
      1),
  HEIGHT_ERROR(
      11,
      "Case too tall. Solution: First, validate case is conveyable by scanning the case via receiving application. If case shows conveyable, then create a ticket via FIXIT application and contact QA. If case shows non-conveyable, remove case from conveyor, palletize, receive and label via DA_NC process.",
      1),
  SKEW_DIMENSION_ERROR(
      12,
      "Case is misaligned. Solution: Remove case from zone 1 and re-induct so it is properly aligned.",
      1),
  CARTON_SIDE_BY_SIDE(
      13,
      "Carton side by side - Multiple cases inducted next to each other. Solution: Remove the cases and re-induct them one at a time. If only one case is present, inspect for loose packaging or tape and contact quality inspector.",
      1),
  NO_READ_INDUCT(
      14,
      "No barcode found. Solution: Remove and re-induct case with the barcode on top. If the barcode is missing, then open the case, manually receive and label using the item’s UPC via the receiving application. ",
      1),
  NO_DATA(
      15,
      "The Cognex scanner is not transmitting data to the BOSS controller. Solution: Contact Maintenance",
      1),
  MAX_SCANNER_ERR(
      16,
      "If configured, cases will continue to flow and only stop when the max is achieved (Reject Line Must Be configured).",
      1),
  HOST_BOX_STS(17, "Host has commanded to stop.", 2),
  NO_DIM_DATA(
      18,
      "The Dimensioner is not sending any data to BOSS. Solution: If problem persists, contact Maintenance.",
      -1),
  LABELER_OFFLINE(
      26, "Communication was lost between Panther and BOSS.  Solution: Contact Maintenance.", 3),
  PRINT_ENGINE_POWER(28, "The printer has lost power. Solution: Contact Maintenance.", -1),
  ACL_BYPASS(
      29, "Incorrect printer position. Solution: Check that printer is locked into place", 3),
  TRACKING(
      34,
      "BOSS is tracking a case that did not pass through the Cognex scanner.  Solution: Remove this case and re-induct prior to the Cognex scan tunnel.",
      3),
  LOST_BOX(
      35,
      "BOSS expected a case at the print head and no case was found. Solution: First, remove the label from the tamp head and dispose of it. Then, press the Blue Printer Reset button. Finally, press the Orange Alarm Reset button.",
      3),
  MAX_TRACKING_ERR(
      36,
      "If configured, miss tracked cartons will continue to flow, if the limit of consecutive errors is achieved the line will stop at the affected eye. (Reject Line Must Be configured)",
      3),
  STRAY_BOX(
      37,
      "A case that was expected to be removed was detected on the line. Solution: Remove all cases in zone 4 and re-induct.",
      4),
  OUT_OF_SEQUENCE(
      42,
      "Cases are out of sequence. Solutions: Remove the cases from Zone 1, 2, 3 and 4. If a label was applied, remove it and dispose of it. Clear the tamp head of labels by pressing \"Reset\" & \"Apply\" on the Panther display screen until no more labels are generated. Press the Blue \"Printer Reset\" button then press the Orange \"Alarm Reset\" button to restart the line.",
      4),
  NO_READ_VERIFY(
      43,
      "The verification scanner did not read a label. Solution: Check to see if the label was applied.  Remove the case, dispose of the label if found, and re-induct.",
      4),
  NO_DATA_VERIFY(
      44,
      "The verification scanner and BOSS are not communicating.  Solution: First, try removing the label on the box and re-induct it. If error persists, contact Maintenance.",
      4),
  MAX_VERIFY_ERR(
      45,
      "If configured, verify scanner errors will continue to flow, if the limit of consecutive errors is achieved the line will stop at the affected eye. (Reject Line Must Be configured)",
      4),
  MAX_HOST_ERR(
      50,
      "The communication between BOSS and GLS is slow.  Solution: Contact Manager if this error continues to occur.",
      4),
  HOST_LINK_DOWN(
      51, "Lost Communication to Host at Induction/Verify. Solution: Contact Manager.", -1),
  NO_VER_ACK(
      52,
      "BOSS has not received an acknowledgement for the previous verification message.  Solution:  Press the Orange Alarm Reset button. Contact Manager if this error continues to occur.",
      4),
  UNDERSPEED_ERROR(56, "The conveyor is running under speed. Solution: Contact Maintenance.", -1),
  MOTOR_OVERLOAD(57, "The conveyor motor is overloaded. Solution: Contact Maintenance.", -1),
  JAM(
      58,
      "There is a jam at one of the sensors.  Solution: Remove the obstruction if it is visible. If it is not visible call Maintenance.",
      -1),
  ESTOP_ERROR(
      59,
      "The conveyor is stopped due to an ESTOP being triggered.  Solution: If it is not an emergency, reset the e-stop to continue receiving.",
      -1),
  TEST_MODE(62, "Test mode is enabled. Solution: Contact Maintenance.", -1),
  SCAN_TEST_MODE(63, "Test mode is enabled. Solution: Contact Maintenance.", -1),
  NONCON_SSTK(
      1001,
      "This case is not DA Conveyable.  Solution: Remove case from conveyor, palletize, receive and label via DA_NC or SSTK process.",
      2),
  INVALID_REQUEST(
      1002,
      "BOSS and GLS communication is corrupted and not working properly. Solution: Contact Maintenance.",
      2),
  NO_DATA_ASSOC(
      1003,
      "No trailer has been associated to this ACL.  Solution: Scan the dock door using the Receiving application located in Myapps on the TC70.",
      2),
  BLOCKED_ITEM(
      1004, "This item is \"Blocked\" and unable to be received.  Solution: Contact QA", 2),
  MAX_OVERAGE_LIMIT(
      1005,
      "The maximum amount of allowed overage has been received.  Solution: Create a problem ticket via FIXIT app and contact QA.",
      2),
  BREAKOUT(
      1006,
      "Solution: Open the case, remove inner cases and place on conveyor.  Contact Quality Inspector.",
      2),
  MASTERPACK(1007, "Solution: Open the case, remove inner cases and place on conveyor.", 2),
  PROBLEM_ASN(1008, "No ASN available. Solution: STOP and do not receive.  Contact Manager.", 2),
  NO_OVERAGE_LABEL(
      1009,
      "GLS ran out of exception labels. Solution: Create a problem ticket via FIXIT app and contact QA.",
      2),
  SYSTEM_ERROR(1010, "GLS encountered a system error. Solution: Contact Manager.", 2),
  UPC_NOT_FOUND(
      1011,
      "UPC not found. Solution: First, try UPC catalog. If unsuccessful, contact quality inspector and receive the remaining cases using the manual receipt process.",
      2),
  NO_BARCODE_READ(
      1012, "Unable to read barcode. Solution: Remove this case and receive it manually.", 2);

  private final Integer code;
  private final String message;
  private final Integer zone;

  public Integer getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }

  public Integer getZone() {
    return zone;
  }

  ACLError(Integer code, String message, Integer zone) {
    this.code = code;
    this.message = message;
    this.zone = zone;
  }
}
