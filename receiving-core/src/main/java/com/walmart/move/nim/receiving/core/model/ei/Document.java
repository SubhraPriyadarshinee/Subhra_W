package com.walmart.move.nim.receiving.core.model.ei;

import lombok.Data;

@Data
public class Document {

  private String businessTransactionType;
  private String businessTransactionRefDocumentNumber;
  private Integer businessTransactionSubType;
}
