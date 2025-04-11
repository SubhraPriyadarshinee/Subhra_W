package com.walmart.move.nim.receiving.witron.model;

import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ContainerLabel {
  private String clientId;
  private Map<String, String> headers;
  private List<PrintLabelRequest> printRequests;
}
