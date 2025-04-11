package com.walmart.move.nim.receiving.rdc.model.wft;

import lombok.Getter;

@Getter
public enum WFTInstruction {

  // activityCode,activityName,instructionCode,instructionMsg,instructionStatus
  CASEPACK(0, "CasePack", "Receiving - CP", "Receiving - CP", "Updated"),
  CASEPACK_INDUCT(1, "CPInduct", "Receiving - CP Induct", "Receiving - CP Induct", "Updated"),
  BREAKPACK(2, "BreakPack", "Receiving - BP", "Receiving - BP", "Updated"),
  NON_CON_CASEPACK(3, "DANonCon", "Receiving - Non-Con CP", "Receiving - Non-Con CP", "Updated"),
  BREAKPACK_CONVEY(4, "BreakPackCon", "Receiving - Conv BP", "Receiving - Conv BP", "Updated"),
  NON_CON(5, "NonCon", "Receiving - Non Con", "Receiving - Non Con", "Updated"),
  DSDC(6, "DSDC", "Receiving - DSDC", "Receiving - DSDC", "Updated"),
  VOICE_FP(7, "VoiceFP", "Receiving - VOICE FP", "Receiving - VOICE FP", "Updated"),
  VOICE(8, "Voice", "Receiving - VOICE", "Receiving - VOICE", "Updated"),
  SSTK(13, "SSTK", "Receiving - SSTK", "Receiving - SSTK", "Updated"),
  DA(10, "DA", "Receiving - DA", "Receiving - DA", "Updated"),
  DA_PALLET_REMOVAL(
      11, "Removal - DA Pallet", "Removal - DA Pallet", "Removal - DA Pallet", "Updated"),
  DA_LABEL_BACKOUT(12, "Backout - DA Label", "Backout - DA Label", "Backout - DA Label", "Updated"),
  PALLET_REMOVAL(11, "PalletRemoval", "Removal - Pallet", "Removal - Pallet", "Updated"),
  LABEL_BACKOUT(12, "LabelBackOut", "Backout - Label", "Backout - Label", "Updated"),
  DOCKTAG(14, "Dock Tag", "Docktag created", "Docktag created", "Updated"),
  ACL(20, "ACL", "Receiving - ACL", "Receiving - ACL", "Updated");

  private Integer activityCode;
  private String activityName;
  private String code;
  private String message;
  private String status;

  WFTInstruction(
      Integer activityCode, String activityName, String code, String message, String status) {
    this.activityCode = activityCode;
    this.activityName = activityName;
    this.code = code;
    this.message = message;
    this.status = status;
  }
}
