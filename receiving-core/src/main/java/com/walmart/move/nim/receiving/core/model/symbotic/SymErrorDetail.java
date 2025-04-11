package com.walmart.move.nim.receiving.core.model.symbotic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SymErrorDetail {
  private String code;
  private Integer quantity;
}
