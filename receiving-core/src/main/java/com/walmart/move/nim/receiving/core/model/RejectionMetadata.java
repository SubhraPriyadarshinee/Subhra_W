package com.walmart.move.nim.receiving.core.model;

import lombok.*;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectionMetadata {

  private boolean isRejectEntireDelivery;
  private String rejectionReason;
  private String claimType;
  private boolean isFullLoadProduceRejection;
}
