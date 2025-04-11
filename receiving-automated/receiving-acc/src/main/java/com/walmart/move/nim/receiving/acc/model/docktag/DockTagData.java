package com.walmart.move.nim.receiving.acc.model.docktag;

import com.walmart.move.nim.receiving.utils.constants.DockTagType;
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
  private Long id;
  private String dockTagId;
  private Long deliveryNumber;
  private String createUserId;
  private Long createTs;
  private String completeUserId;
  private Long completeTs;
  private InstructionStatus dockTagStatus;
  private String lastChangedUserId;
  private Long lastChangedTs;
  private String scannedLocation;
  private DockTagType dockTagType;
  private String facilityCountryCode;
  private Integer facilityNum;
}
