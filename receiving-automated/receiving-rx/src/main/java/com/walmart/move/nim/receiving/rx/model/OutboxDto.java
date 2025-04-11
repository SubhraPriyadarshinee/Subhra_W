package com.walmart.move.nim.receiving.rx.model;

import com.walmart.platform.messages.MetaData;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class OutboxDto {
  private Map<String, Object> headers;
  private String body;
  private String eventIdentifier;
  private String publisherPolicyId;
  private MetaData metaData;
  private Instant executionTs;
}
