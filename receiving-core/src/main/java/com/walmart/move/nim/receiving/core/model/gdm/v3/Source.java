package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {
  private String number;
  private String numberType;
  private String type;
  private String countryCode;
  private String globalLocationNumber;
  private String shipperId;
  private String shipperName;
}
