package com.walmart.move.nim.receiving.core.model;

import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReceivingProgressMessage {
  ReceivingProgressPayload payload;
  public Map<String, Object> header;
}
