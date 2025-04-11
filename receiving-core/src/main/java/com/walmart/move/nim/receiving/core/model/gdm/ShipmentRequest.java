package com.walmart.move.nim.receiving.core.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class ShipmentRequest {
  private String shipmentNumber;
  private String documentId;
}
