package com.walmart.move.nim.receiving.core.model.printlabel;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PrintLabelData {
  private Map<String, String> headers;
  private String clientId;
  private List<PrintLabelRequest> printRequests;
}
