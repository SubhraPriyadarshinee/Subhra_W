package com.walmart.move.nim.receiving.core.model.label.hawkeye;

import com.walmart.move.nim.receiving.core.contract.prelabel.model.LabelData;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class HawkEyeLabelData extends LabelData {
  private String doorNumber;
  private String trailer;
  private String defaultTCL;
  private String defaultDestination;
  private String tclTemplate;

  public HawkEyeLabelData(
      String clientId,
      String formatName,
      String user,
      String deliveryNumber,
      String doorNumber,
      String trailer,
      String defaultTCL,
      String defaultDestination,
      String tclTemplate) {
    super(clientId, formatName, user, deliveryNumber);
    this.doorNumber = doorNumber;
    this.trailer = trailer;
    this.defaultTCL = defaultTCL;
    this.defaultDestination = defaultDestination;
    this.tclTemplate = tclTemplate;
  }
}
