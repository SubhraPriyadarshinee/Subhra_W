package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Capability {
  private String abbr;
  private Integer code;
  private String desc;
  private String capName;
  private String wmLanguageCode;
  private String applicationName;
  private Integer productId;
  private String productName;
  private char obsoleteInd;
}
