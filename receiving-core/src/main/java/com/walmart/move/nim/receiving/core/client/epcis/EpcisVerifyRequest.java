package com.walmart.move.nim.receiving.core.client.epcis;

import java.io.Serializable;
import lombok.Data;

@Data
public class EpcisVerifyRequest implements Serializable {
  private String code;
  private String type;
  private String gln;
  private String activity;
}
