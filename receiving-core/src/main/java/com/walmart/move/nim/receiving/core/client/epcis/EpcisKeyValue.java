package com.walmart.move.nim.receiving.core.client.epcis;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EpcisKeyValue {
  private String key;
  private String value;
}
