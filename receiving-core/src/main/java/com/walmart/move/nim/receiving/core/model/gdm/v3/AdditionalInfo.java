package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AdditionalInfo {

  private String eventType;
  private List<SsccScanResponse.Container> containers;
}
