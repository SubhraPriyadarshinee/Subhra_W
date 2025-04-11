package com.walmart.move.nim.receiving.core.model.slotting;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceiveContainersResponseBody;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SlottingPalletResponseWithRdsResponse extends SlottingPalletResponse {

  private ReceiveContainersResponseBody rds;
}
