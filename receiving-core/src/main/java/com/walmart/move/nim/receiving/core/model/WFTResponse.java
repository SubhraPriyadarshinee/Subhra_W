package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Data
@ToString
@Getter
@Setter
public class WFTResponse {

  private String activityName;
  private String userName;

  @NotNull private Long receivedQty;
  private Instruction instruction;
  private Container container;

  public WFTResponse() {}

  public WFTResponse(String activityName, Long receivedQty) {
    this.activityName = activityName;
    this.receivedQty = receivedQty;
  }

  public WFTResponse(String userName, String activityName, Long receivedQty) {
    this.userName = userName;
    this.receivedQty = receivedQty;
  }
}
