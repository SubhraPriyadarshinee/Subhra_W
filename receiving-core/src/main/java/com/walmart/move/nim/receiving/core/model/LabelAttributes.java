package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabelAttributes {

  private String userId;
  private Integer palletTi;
  private Integer palletHi;
  private String slotSize;
  private String vendorStockNumber;
}
