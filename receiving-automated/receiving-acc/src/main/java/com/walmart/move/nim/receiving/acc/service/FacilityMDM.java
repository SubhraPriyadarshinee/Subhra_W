package com.walmart.move.nim.receiving.acc.service;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;

public interface FacilityMDM {
  public Map<String, Integer> getStoreToDCMapping(List<String> storeLis, HttpHeaders httpHeaders);
}
