package com.walmart.move.nim.receiving.endgame.model;

import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.model.label.hawkeye.HawkEyeLabelData;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * * This is the contract (model) class to send Label to Hawkeye
 *
 * @see <a
 *     href="https://collaboration.wal-mart.com/display/NGRCV/Flow-1%3A+Pre-generating+TCLs+%2C+UPC+binding%2C+Diverts+for+a+Delivery">Reference
 *     docs</a>
 * @author sitakant
 */
@Setter
@Getter
@ToString(callSuper = true)
@NoArgsConstructor
public class EndGameLabelData extends HawkEyeLabelData {
  private Set<String> trailerCaseLabels;
  private String labelGenMode;
  private LabelType type;

  @Builder
  public EndGameLabelData(
      String clientId,
      String formatName,
      String user,
      String deliveryNumber,
      String doorNumber,
      String trailer,
      String defaultTCL,
      String defaultDestination,
      String tclTemplate,
      Set<String> trailerCaseLabels,
      String labelGenMode,
      LabelType type) {
    super(
        clientId,
        formatName,
        user,
        deliveryNumber,
        doorNumber,
        trailer,
        defaultTCL,
        defaultDestination,
        tclTemplate);
    this.trailerCaseLabels = trailerCaseLabels;
    this.labelGenMode = labelGenMode;
    this.type = type;
  }
}
