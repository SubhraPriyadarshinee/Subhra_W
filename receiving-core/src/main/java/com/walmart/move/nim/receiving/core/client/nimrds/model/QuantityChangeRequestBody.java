package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class QuantityChangeRequestBody {

  @SerializedName(value = "scan_tag")
  private String scanTag;

  private Integer quantity;

  @SerializedName(value = "user_id")
  private String userId;
}
