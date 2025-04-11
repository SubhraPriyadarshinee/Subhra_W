package com.walmart.move.nim.receiving.core.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RapidRelayerData {
  private String eventIdentifier;
  private Instant executionTs;
  private String publisherPolicyId;
  private String storagePolicyId;
  private String ref;
  private Map<String, Object> headers;
  private String body;
  private Map<String, Object> metaDataValues;
}
