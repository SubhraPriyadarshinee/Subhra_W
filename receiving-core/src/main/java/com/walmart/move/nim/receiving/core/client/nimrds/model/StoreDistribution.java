package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class StoreDistribution {

  private Integer storeNbr;

  @SerializedName(value = "whpk_order_qty", alternate = "whpkOrderQty")
  private Integer whpkOrderQty;

  @SerializedName(value = "whpk_distrib_qty", alternate = "whpkDistribQty")
  private Integer whpkDistribQty;

  private String convertedStoreNbr;
}
