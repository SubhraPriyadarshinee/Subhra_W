package com.walmart.move.nim.receiving.core.model.instruction;

import java.util.List;
import lombok.Data;

@Data
public class InstructionDownloadChildContainerDTO {

  private String trackingId;
  private String prevTrackingId;
  private String poNbr;
  private Integer poLineNbr;
  private List<InstructionDownloadDistributionsDTO> distributions;
  private String ctrType;
  private String inventoryTag;
  private InstructionDownloadCtrDestinationDTO ctrDestination;
  private String labelType;
  private String channelMethod;
}
