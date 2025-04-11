package com.walmart.move.nim.receiving.acc.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestSelectPoLineAndReceiveRequest {
  long deliveryNumber;
  String doorNumber;
  String gtin;
  boolean createReceiptsEnabled;
  boolean showReceiptsAfterReceiving;
}
