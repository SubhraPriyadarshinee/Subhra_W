package com.walmart.move.nim.receiving.core.client.nimrds.model;

import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ReceivedQuantityResponseFromRDS {
  Map<String, Long> receivedQtyMapByPoAndPoLine;
  Map<String, String> errorResponseMapByPoAndPoLine;
}
