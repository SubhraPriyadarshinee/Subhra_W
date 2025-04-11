package com.walmart.move.nim.receiving.core.model.instruction;

import java.util.List;
import lombok.Data;

@Data
public class InstructionDownloadBlobDataDTO {

  private String instructionCode;
  private String instructionMsg;
  private String activityName;
  private boolean shrinkWrap;
  private Long deliveryNbr;
  private String asnNumber;
  private String poNbr;
  private Integer poLineNbr;
  private Long sequence;
  private Integer projectedQty;
  private String projectedQtyUom;
  private InstructionDownloadContainerDTO container;
  private List<InstructionDownloadChildContainerDTO> childContainers;
  private Integer poTypeCode;
  private String sourceFacilityNumber;
  private String poEvent;
  private String palletId;
}
