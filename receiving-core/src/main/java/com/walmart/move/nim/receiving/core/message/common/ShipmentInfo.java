package com.walmart.move.nim.receiving.core.message.common;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentInfo {

  private String documentId;

  private String shipmentNumber;

  private String documentType;

  private String transactionStatement;

  private String purchaseInformation;

  private Set<PackData> packs;

  private String receivingStatus;

  private Set<PalletData> pallets;
}
