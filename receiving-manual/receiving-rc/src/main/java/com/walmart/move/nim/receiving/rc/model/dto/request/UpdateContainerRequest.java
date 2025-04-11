package com.walmart.move.nim.receiving.rc.model.dto.request;

import com.walmart.move.nim.receiving.rc.model.container.Location;
import com.walmart.move.nim.receiving.rc.model.gad.Answers;
import com.walmart.move.nim.receiving.rc.model.gad.Disposition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdateContainerRequest {
  private String scannedBin;
  private String scannedCart;
  private String containerTag;
  private Disposition disposition;
  private Answers answers;
  private Location location;
  private String workflowCreateReason;
  private String workflowId;
  private String scannedItemLabel;
  private String scannedLabel;
}
