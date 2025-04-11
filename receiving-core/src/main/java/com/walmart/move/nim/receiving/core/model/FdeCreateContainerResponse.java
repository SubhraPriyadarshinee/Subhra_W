package com.walmart.move.nim.receiving.core.model;

import com.google.gson.internal.LinkedTreeMap;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class FdeCreateContainerResponse {
  private String messageId;
  private String instructionCode;
  private String instructionMsg;
  private String receivingUnit;
  private String activityName;
  private boolean printChildContainerLabels;
  private String projectedQtyUom;
  private int projectedQty;
  private String providerId;
  private LinkedTreeMap move;
  private List<ContainerDetails> childContainers;
  private ContainerDetails container;
}
