package com.walmart.move.nim.receiving.core.model.gdm.v3;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDetail {
  private String invoiceNumber;
  private Integer invoiceLineNumber;
}
