package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrailerTemperature {

  private String value;

  @NotNull private String uom;
}
