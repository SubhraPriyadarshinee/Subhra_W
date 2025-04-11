package com.walmart.move.nim.receiving.core.client.gls.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@ToString
@AllArgsConstructor
public class Error {
  public String code;
  public String description;
  public String info;
  public String severity;
}
