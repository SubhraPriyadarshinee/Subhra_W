package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class ScannedData {
  private String key;
  private String applicationIdentifier;
  private String value;

  public String getValue() {
    return StringUtils.trim(value);
  }
}
