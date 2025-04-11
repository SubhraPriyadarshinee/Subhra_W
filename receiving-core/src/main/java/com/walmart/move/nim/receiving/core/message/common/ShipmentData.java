package com.walmart.move.nim.receiving.core.message.common;

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
public class ShipmentData {

  private String shipmentDocumentId;
  private String shipmentNumber;
}
