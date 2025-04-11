package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class CancelContainerResponse {
  private String trackingId;
  private String errorCode;
  private String errorMessage;

  public CancelContainerResponse(String trackingId, String errorCode, String errorMessage) {
    this.trackingId = trackingId;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}
