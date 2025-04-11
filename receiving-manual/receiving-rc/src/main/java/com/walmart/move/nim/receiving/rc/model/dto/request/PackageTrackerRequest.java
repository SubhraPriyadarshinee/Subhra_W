package com.walmart.move.nim.receiving.rc.model.dto.request;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class PackageTrackerRequest {
  @NotEmpty private String scannedLabel;
  @NotEmpty private String scannedLabelType;
  private Double packageCost;
  @NotNull private Boolean isHighValue;
  // TODO : @NotNull will be added in next commit when UI will be deployed to have
  // isSerialScanRequired mandatory
  private Boolean isSerialScanRequired;
  @NotEmpty private String reasonCode;
}
