package com.walmart.move.nim.receiving.core.client.epcis;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EpcisReceiveIdentity {

  private String type;
  private String value;
}
