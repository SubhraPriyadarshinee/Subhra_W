package com.walmart.move.nim.receiving.core.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageHeader {
  private Long timeStamp;
  private String correlationId;
  private String userId;

  @Override
  public String toString() {
    return "MessageHeader{"
        + "timeStamp = '"
        + timeStamp
        + '\''
        + ",correlationId = '"
        + correlationId
        + '\''
        + ",userId = '"
        + userId
        + '\''
        + "}";
  }
}
