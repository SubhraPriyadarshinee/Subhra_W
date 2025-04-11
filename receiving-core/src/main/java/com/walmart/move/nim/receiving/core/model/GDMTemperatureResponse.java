package com.walmart.move.nim.receiving.core.model;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GDMTemperatureResponse {
  private String reasonCode;

  private Set<String> finalizedPos;
}
