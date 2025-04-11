package com.walmart.move.nim.receiving.endgame.model;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class LabelResponse {
  // Required by UI for 'headers' to be present in the response body
  private Map<String, String> headers;
  private String clientId;
  private List<PrintRequest> printRequests;
}
