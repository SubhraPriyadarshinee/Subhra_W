package com.walmart.move.nim.receiving.acc.model.hawkeye.label;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.walmart.move.nim.receiving.core.model.label.ScanItem;
import com.walmart.move.nim.receiving.core.model.label.hawkeye.HawkEyeLabelData;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HawkEyeLabelDataTO extends HawkEyeLabelData {
  private List<ScanItem> scanItems;

  @JsonProperty(value = "deliveryNumber")
  private Long deliveryNbr;

  @Builder
  public HawkEyeLabelDataTO(
      String clientId,
      String formatName,
      String user,
      String doorNumber,
      String trailer,
      String defaultTCL,
      String defaultDestination,
      String tclTemplate,
      List<ScanItem> scanItems,
      Long deliveryNbr) {
    super(
        clientId,
        formatName,
        user,
        deliveryNbr.toString(),
        doorNumber,
        trailer,
        defaultTCL,
        defaultDestination,
        tclTemplate);
    this.scanItems = scanItems;
    this.deliveryNbr = deliveryNbr;
  }
}
