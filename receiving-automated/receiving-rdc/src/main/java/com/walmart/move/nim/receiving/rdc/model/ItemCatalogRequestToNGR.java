package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemCatalogRequestToNGR {
  String deliveryNumber;
  String number;
  String caseUPC;
}
