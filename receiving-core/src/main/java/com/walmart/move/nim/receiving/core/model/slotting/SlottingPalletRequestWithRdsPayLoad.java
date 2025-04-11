package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersRequestBody;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingPalletRequestWithRdsPayLoad extends SlottingPalletRequest {

  private Boolean atlasOnboardedItem;
  private ReceiveContainersRequestBody rds;
}
