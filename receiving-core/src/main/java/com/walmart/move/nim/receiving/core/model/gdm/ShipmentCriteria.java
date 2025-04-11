package com.walmart.move.nim.receiving.core.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ShipmentCriteria {
  ShipmentRequest shipment;
}
