package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ProblemTagResponse {
  private Problem problem;
  private InboundDocument inboundDocument;
  private Item item;
  private DeliveryDocumentLine deliveryDocumentLine;
}
