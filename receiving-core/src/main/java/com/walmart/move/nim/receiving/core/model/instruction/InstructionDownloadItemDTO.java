package com.walmart.move.nim.receiving.core.model.instruction;

import lombok.Data;

@Data
public class InstructionDownloadItemDTO {

  private Long itemNbr;
  private String itemUpc;
  private Integer vnpk;
  private Integer whpk;
  private String itemdept;
  private String baseDivisionCode;
  private String financialReportingGroup;
  private String reportingGroup;
  private String aisle;
  private String zone;
  private String storeZone;
  private String dcZone;
  private String pickBatch;
  private String printBatch;
  private String storeAlignment;
  private Integer shipLaneNumber;
  private Integer divisionNumber;
  private String departmentNumber;
  private String packType;
  private String itemHandlingCode;
  private String messageNumber;
}
