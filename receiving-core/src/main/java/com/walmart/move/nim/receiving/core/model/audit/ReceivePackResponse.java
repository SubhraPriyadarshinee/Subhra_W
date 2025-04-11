package com.walmart.move.nim.receiving.core.model.audit;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ReceivePackResponse {

  private String deliveryNumber;
  private String packNumber;
  private String asnNumber;
  private String trackingId;
  private String auditStatus;
  private String receivingStatus;
  private Map<String, Object> printJob;
}
