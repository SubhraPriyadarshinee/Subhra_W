package com.walmart.move.nim.receiving.rdc.model.docktag;

import com.walmart.move.nim.receiving.utils.constants.InstructionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DockTagData {

  private Long deliveryNumber;
  private String dockTagId;
  private InstructionStatus status;
  private String scannedLocation;
  private String createUserId;
  private Long createTs;
  private String completeUserId;
  private Long completeTs;
  private String lastChangedUserId;
  private Long lastChangedTs;
  private Integer facilityNum;
  private String facilityCountryCode;
  private String deliveryInfo;
}
