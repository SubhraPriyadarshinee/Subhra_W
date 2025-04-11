package com.walmart.move.nim.receiving.endgame.model;

import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class DeliveryMetaDataRequest {
  @NotEmpty(message = "CarrierName cannot be empty")
  private String carrierName;

  @NotEmpty(message = "CarrierScacCode cannot be empty")
  private String carrierScacCode;

  private String billCode;
  private String trailerNumber;
}
