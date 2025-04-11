package com.walmart.move.nim.receiving.core.model;

import java.util.Set;
import javax.validation.Valid;
import lombok.*;

@Data
@NoArgsConstructor
public class GDMDeliveryTrailerTemperatureInfo {

  @Valid Set<TrailerZoneTemperature> zones;

  private Boolean hasOneZone;
  private Boolean isNoRecorderFound = false;
}
