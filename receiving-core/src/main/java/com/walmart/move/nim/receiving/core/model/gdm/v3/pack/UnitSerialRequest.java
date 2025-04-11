package com.walmart.move.nim.receiving.core.model.gdm.v3.pack;

import java.util.List;
import lombok.Data;

@Data
public class UnitSerialRequest {
  private String deliveryNumber;
  private List<UnitRequestMap> identifier;
}
