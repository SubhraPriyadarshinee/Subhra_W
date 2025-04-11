package com.walmart.move.nim.receiving.rdc.model;

import com.walmart.move.nim.receiving.core.model.PartialPalletInfo;
import com.walmart.move.nim.receiving.core.model.ReceivingLoadState;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MirageLabelReceiveRequest {
  private String upcNumber;
  private String deliveryNumber;
  private String doorNumber;
  private Integer ti;
  private Integer hi;
  private Integer quantityToReceive;
  private Integer printerId;
  private boolean caseReceiving;
  private String tag;
  private String tagType;
  private ReceivingLoadState currentLoadStatus;
  private boolean scanToPrint;
  private String equipmentId;
  private String labelIdentifier;
  private String purchaseReferenceNumber;
  private int purchaseReferenceLineNumber;
  private List<PartialPalletInfo> sstkSlotSize;
  private int primeSlotSize;
}
