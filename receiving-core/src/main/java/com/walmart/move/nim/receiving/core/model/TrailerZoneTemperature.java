package com.walmart.move.nim.receiving.core.model;

import java.util.Set;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailerZoneTemperature {

  private String id;

  @Valid private TrailerTemperature temperature;

  @Valid private Set<String> purchaseOrders;
}
