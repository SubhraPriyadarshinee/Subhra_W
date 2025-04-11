package com.walmart.move.nim.receiving.core.client.hawkeye.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class LabelUpdateRequest {
  private String lpn;
  private String status;
}
