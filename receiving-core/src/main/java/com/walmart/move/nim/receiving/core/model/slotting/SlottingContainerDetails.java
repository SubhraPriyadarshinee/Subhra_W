package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import java.util.List;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlottingContainerDetails {

  private String containerTrackingId;
  private String containerCreateTs;
  private Integer locationSize;
  private String locationName;
  private String purchaseOrderNum;
  private Integer purchaseOrderLineNum;
  private String containerType;
  private String containerName;
  private String fromLocation;

  @NotEmpty(message = ReceivingException.SLOTTING_VALIDATION_ERROR_CONTAINERDETAILS)
  private List<SlottingContainerItemDetails> containerItemsDetails;
}
