package com.walmart.move.nim.receiving.core.client.nimrds.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;

@Data
public class ContainerOrder {

  @SerializedName(value = "_id")
  private String id;

  private String poNumber;
  private Integer poLine;
  private String manifest;
  private String doorNum;
  private String userId;
  private Integer qty;
  private String receivedUomTxt;
  private Integer breakpackRatio;
  private String containerGroupId;
  private Integer sstkSlotSize;
  private boolean slotToPrime;
  private boolean ignoreLabelCase;
  private SlottingOverride slottingOverride;
  private List<LotDetails> lotNumbers;
  private Integer expectedTi;
  private Integer expectedHi;
  private Boolean isLessThanCase;
}
