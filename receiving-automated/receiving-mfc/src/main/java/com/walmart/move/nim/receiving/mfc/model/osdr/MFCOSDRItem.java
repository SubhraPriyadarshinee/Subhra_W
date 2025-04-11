package com.walmart.move.nim.receiving.mfc.model.osdr;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MFCOSDRItem {
  private Long itemNumber;
  private String gtin;
  private String invoiceNumber;
  private Integer invoiceLineNumber;
  private Integer vnpk;
  private Integer whpk;
  private Integer quantity;
  private String uom;
  private List<MFCOSDRReceipt> receipt;
}
