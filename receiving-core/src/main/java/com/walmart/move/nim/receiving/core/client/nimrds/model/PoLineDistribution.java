package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PoLineDistribution {
  @SerializedName(value = "buNumber", alternate = "storeNumber")
  private String storeNumber;

  @SerializedName(value = "orderQuantity", alternate = "orderQty")
  private Integer orderQty;

  private Integer receivedQty;
  private String qtyUOM;
}
