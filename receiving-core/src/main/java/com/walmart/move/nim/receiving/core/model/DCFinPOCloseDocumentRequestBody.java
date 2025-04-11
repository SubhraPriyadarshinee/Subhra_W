package com.walmart.move.nim.receiving.core.model;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDate;
import java.util.List;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DCFinPOCloseDocumentRequestBody {

  @NotBlank private String documentNum;
  @NotBlank private String docType;
  @NotBlank private String deliveryNum;
  @NotBlank private String deliveryGateInTs;
  @NotNull private Integer freightBillQty;
  @NotBlank private String freightBillQtyUom;

  @NotNull private LocalDate docClosedTs;

  @SerializedName(value = "isDocumentClosed")
  @NotNull
  private Boolean documentClosed;

  @NotNull @NotEmpty private List<DCFinPOCloseDocumentLineRequestBody> documentLineItems;
}
