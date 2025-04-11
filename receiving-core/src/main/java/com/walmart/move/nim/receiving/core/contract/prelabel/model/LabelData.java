package com.walmart.move.nim.receiving.core.contract.prelabel.model;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.*;

/**
 * * The parent Label format for all kind of receiving products For more, see EndGameLabelData.java
 *
 * @author sitakant
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public abstract class LabelData extends MessageData {
  private String clientId;
  private String formatName;
  private String user;
  private String deliveryNumber;
}
