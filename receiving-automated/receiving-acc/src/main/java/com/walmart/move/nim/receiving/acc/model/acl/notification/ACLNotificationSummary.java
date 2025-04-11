package com.walmart.move.nim.receiving.acc.model.acl.notification;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Builder
@Getter
@ToString
@Setter
@AllArgsConstructor
public class ACLNotificationSummary {

  private String equipmentName;

  private String equipmentType;

  private String locationId;

  private EquipmentStatus equipmentStatus;

  private Date updatedTs;
}
