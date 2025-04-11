package com.walmart.move.nim.receiving.endgame.message.common;

import com.walmart.move.nim.receiving.utils.common.MessageData;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * * Model for getting printer acknowledgement from Hawkeye
 *
 * @author sitakant
 */
@Getter
@Setter
@ToString
public class PrinterACK extends MessageData {
  private String trailerCaseLabel;
  private String status;
  private String reason;
}
