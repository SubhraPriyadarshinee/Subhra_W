package com.walmart.move.nim.receiving.core.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DCFinPOCloseRequestBody {

  @NotBlank private String txnId;
  @NotNull private DCFinPOCloseDocumentRequestBody document;
}
