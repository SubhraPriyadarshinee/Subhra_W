package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BOLDatum {
  private long freightBillQuantity;
  private String bolNumber;
}
