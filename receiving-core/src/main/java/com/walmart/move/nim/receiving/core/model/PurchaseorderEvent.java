package com.walmart.move.nim.receiving.core.model;

import java.time.Instant;
import java.util.List;
import lombok.*;

@Data
public class PurchaseorderEvent {

  private String poNumber;

  private String event;

  private Instant timestamp;
  private String user;
  private List<AdditionalInfo> additionalInfo;
}
