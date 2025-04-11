package com.walmart.move.nim.receiving.core.model.printlabel;

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
public class ReprintLabelResponseBody {
  private Map<String, Object> headers;
  private String clientId;
  private List<PrintLabelRequest> printRequests;
}
