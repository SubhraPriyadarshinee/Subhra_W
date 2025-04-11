package com.walmart.move.nim.receiving.core.model;

import lombok.*;

/**
 * POJO holds aggregate quantity for given PO, Po Line item in Container Item Table
 *
 * @author vn50o7n
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContainerPoLineQuantity {
  String po;
  int line;
  Long quantity;
}
