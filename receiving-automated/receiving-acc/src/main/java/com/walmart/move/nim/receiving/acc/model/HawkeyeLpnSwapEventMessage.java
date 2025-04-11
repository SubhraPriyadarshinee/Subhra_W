package com.walmart.move.nim.receiving.acc.model;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class HawkeyeLpnSwapEventMessage extends MessageData implements Serializable {

  private static final long serialVersionUID = 1L;

  private SwapContainer finalContainer;
  private SwapContainer swapContainer;
}
