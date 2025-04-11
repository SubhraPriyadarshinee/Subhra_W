package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** @author v0k00fe */
@Data
@AllArgsConstructor
public class ContainerSummary {
  private static final Logger LOG = LoggerFactory.getLogger(ContainerSummary.class);

  private String trackingId;
  private String parentTrackingId;
  private String serial;
  private String lotNumber;
  private int quantity;
  private String quantityUOM;
}
