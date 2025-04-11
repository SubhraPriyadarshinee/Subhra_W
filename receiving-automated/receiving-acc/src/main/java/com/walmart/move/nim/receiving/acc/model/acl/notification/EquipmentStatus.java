package com.walmart.move.nim.receiving.acc.model.acl.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** A status message emitted by the equipment. */
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class EquipmentStatus {
  /** An equipment status code. (Required) */
  private Integer code;

  /** An equipment status. (Required) */
  private String value;

  /** Combination of SequenceNumber- cameraId. */
  private String msgSequence;

  /** Following values are populated before sending to sumo */
  private String message;

  private Integer zone;

  /**
   * Following fields are for new hawkeye contract
   * https://collaboration.wal-mart.com/display/GLSIN/ACL+WMS+Contract#ACLWMSContract-6.EquipmentStatusNotification*
   */
  private String statusTimestamp;

  private String status;
  private String severity;
  private String componentId;
  private String displayMessage;
  private Boolean cleared;
}
