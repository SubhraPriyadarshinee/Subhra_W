package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DABackoutLabel {
  @SerializedName(value = "scan_tag")
  private String scanTag;

  @SerializedName(value = "return_code")
  private String returnCode;

  @SerializedName(value = "return_text")
  private String returnText;
}
