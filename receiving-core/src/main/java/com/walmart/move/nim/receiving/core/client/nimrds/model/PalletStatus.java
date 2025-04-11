package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class PalletStatus {

  @SerializedName(value = "scan_tag")
  private String scanTag;

  @SerializedName(value = "return_code")
  private Integer returnCode;

  @SerializedName(value = "return_text")
  private String returnText;
}
