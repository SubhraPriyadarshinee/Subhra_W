package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Mapping class for update instruction payLoad
 *
 * @author pcr000m
 */
@Getter
@Setter
@ToString
public class UpdateInstructionRequest {
  private String doorNumber;
  private String problemTagId;
  private Long deliveryNumber;
  private String containerType;
  private boolean unitScanCompleted;
  private Map<String, String> facility;
  private List<DocumentLine> deliveryDocumentLines;
  private List<ScannedData> scannedDataList;
  private String pbylDockTagId;
  private String pbylLocation;
  private Date expiryDate;
  private String lotNumber;
  private List<ScannedData> userEnteredDataList;
  private String flowDescriptor;
  private String userRole;
  private String eventType;
  private String createUser;
}
