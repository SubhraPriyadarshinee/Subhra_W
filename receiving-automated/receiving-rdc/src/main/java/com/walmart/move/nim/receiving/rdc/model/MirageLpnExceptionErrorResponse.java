package com.walmart.move.nim.receiving.rdc.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MirageLpnExceptionErrorResponse {
  private String title;
  private String message;
  private String code;
}
