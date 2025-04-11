package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonSerialize
public class Sgtin {
  private String serial;
  private String gtin;
}
