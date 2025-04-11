package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RdsReceiptsRequest {

  private List<OrderLines> orderLines = new ArrayList<>();
}
