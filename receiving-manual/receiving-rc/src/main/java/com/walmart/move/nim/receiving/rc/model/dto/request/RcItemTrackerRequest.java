package com.walmart.move.nim.receiving.rc.model.dto.request;

import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class RcItemTrackerRequest {
  @NotEmpty private String scannedLabel;
  @NotEmpty private String scannedItemLabel;
  @NotEmpty private String reasonCode;
}
