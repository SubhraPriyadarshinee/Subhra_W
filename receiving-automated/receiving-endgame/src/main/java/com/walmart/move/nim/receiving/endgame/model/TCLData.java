package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Model to parse the TCL and extract the relevant data into a model using {@link
 * EndGameUtils#extractTCL(String)}
 *
 * @author sitakant
 */
@ToString
@Builder
@Getter
public class TCLData {
  private String deliveryNumber;
  private String doorNumber;
}
