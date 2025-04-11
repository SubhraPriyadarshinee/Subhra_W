package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Data
@Getter
@Builder
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReceiveInstructionRequest {
  private String doorNumber;
  private String containerType;
  private Integer quantity;
  private String quantityUOM;
  private Date rotateDate;
  private String printerName;
  private SlotDetails slotDetails;
  private List<PalletQuantities> palletQuantities;
  private String messageId;
  private List<DeliveryDocumentLine> deliveryDocumentLines;
  private Long deliveryNumber;
  private String pbylLocation;
  private Date expiryDate;
  private String lotNumber;
  private List<DeliveryDocument> deliveryDocuments;
  private String problemTagId;
  private boolean isReceiveBeyondThreshold;
  private String flowDescriptor;
  private Boolean isLessThanCase;
  private Integer storeNumber;
  private String destType;
  private String userRole;
}
