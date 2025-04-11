package com.walmart.move.nim.receiving.core.model;

import java.util.concurrent.BlockingQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantSpecificLPNCache {
  private String facilityNum;
  private String facilityCountryCode;
  private BlockingQueue<String> lpnCache;
  private boolean isRequested;
}
