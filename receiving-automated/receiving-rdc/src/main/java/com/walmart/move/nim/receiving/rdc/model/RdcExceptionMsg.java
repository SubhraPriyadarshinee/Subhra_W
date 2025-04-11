package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum RdcExceptionMsg {
  LABEL_VALIDATED(
      "Place case on conveyor",
      "This label is still valid and the case can be put back on the conveyor.",
      null),
  MATCH_FOUND(
      "Place case on conveyor",
      "This label is still valid and the case can be put back on the conveyor.",
      null),
  ERROR_LPN_NOT_FOUND(
      "Place case on conveyor",
      "This label is still valid and the case can be put back on the conveyor.",
      null),

  LPN_RECEIVED(
      "Place case on conveyor",
      "This label is still valid and the case can be put back on the conveyor.",
      null),
  ERROR_LPN_BACKOUT(
      "Remove existing label",
      "Then place the container back on the conveyor without label.",
      "Label has been backout in the system"),

  DSDC_AUDIT_LABEL(
      "Keep label on case and place case onto pallet",
      "Keep cases of the same item together on the pallet",
      null),

  EXCEPTION_LPN_NOT_FOUND(
      "Remove existing label",
      "Then place the container back on the conveyor without label.",
      null),

  NONCON(
      "Keep label on case and place case onto pallet",
      "Keep cases of the same item together on the pallet",
      null),

  SYSTEM_ERROR(
      "Remove existing label",
      "Place container on conveyor without label. If error label persists, place case on pallet for troubleshooting.",
      null),

  INVALID_REQUEST(
      "Remove existing label",
      "Place container on conveyor without label. If error label persists, place case on pallet for troubleshooting.",
      null),
  NO_DATA_ASSOCIATED(
      "Remove existing label",
      "Place container on conveyor without label. If error label persists, place case on pallet for troubleshooting.",
      null),
  SSTK_ATLAS_ITEM(
      "Remove error labels and palletize cases for slotting",
      "Make sure one case for each item being palletized is kept with the error label still pasted on it.",
      null),
  SSTK(
      "Remove error labels and palletize cases for slotting",
      "Make sure one case for each item being palletized is kept with the error label still pasted on it.",
      null),
  RCV_ERROR(
      "Remove existing label",
      "Place container on conveyor without label. If error label persists, place case on pallet for troubleshooting.",
      null),

  X_BLOCK(
      "Keep label on case and place case onto pallet",
      "Keep cases of the same item together on the pallet",
      null),

  OVERAGE(
      "Scan labels with matching item number and delivery",
      "Print the labels once you’ve finished.",
      null),

  BREAKOUT(
      "Open the case and label the individual inner packs",
      "Once you've applied the new labels,place them back on the conveyor.",
      "Breakpack item with conveyable packs inside"),
  LPN_NOT_RECEIVED_SSTK(
      "Collect all cases on a pallet and process through manual receiving.",
      "This is a Slotted Symbotic Item. Induct received pallet into AIB or MIB",
      null),
  LPN_RECEIVED_SSTK(
      "Received case detected. Re-induct with reject label or process offline.",
      "To process offline take case to LIUI to print a shipping label.",
      null);

  private String title;
  private String description;
  private String info;
}
