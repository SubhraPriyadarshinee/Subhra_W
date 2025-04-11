package com.walmart.move.nim.receiving.core.model.decant;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DecantMessagePublishRequest {
  private String scenario;
  private Map<String, String> additionalHeaders;
  private String message;
  private String partionKey;
}
