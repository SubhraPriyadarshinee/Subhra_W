package com.walmart.move.nim.receiving.core.model;

import com.google.gson.annotations.SerializedName;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DCFinPOCloseDocumentLineRequestBody {
  @NotNull private Integer documentLineNo;
  @NotNull private Integer primaryQty;
  @NotBlank private String lineQtyUOM;

  @SerializedName(value = "isDocumentLineClosed")
  @NotNull
  private Boolean documentLineClosed;

  @NotBlank private String docLineClosedTs;
}
