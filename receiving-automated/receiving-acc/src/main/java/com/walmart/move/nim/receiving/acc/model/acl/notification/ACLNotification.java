package com.walmart.move.nim.receiving.acc.model.acl.notification;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** The equipment status payload. */
@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class ACLNotification extends MessageData {

  /** The equipment name. (Required) */
  private String equipmentName;

  /** The type of equipment. (Required) */
  private String equipmentType;

  /** The location of the equipment. (Required) */
  private String locationId;

  /** A list of status messages emitted by the equipment. (Required) */
  private List<EquipmentStatus> equipmentStatus;

  /** The timestamp of the message when the status was emitted. (Required) */
  private String updatedTs;
}
