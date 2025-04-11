package com.walmart.move.nim.receiving.fixture.model;

import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PalletReceiveResponse {
  private String packNumber;
  private boolean isAuditRequired;
  private List<PalletItem> items;
  private String storeNumber;
  private String loadNumber;
  private String status;
  private String lpn;
  private List<PrintLabelRequest> printRequests;
}
