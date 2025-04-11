package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.event.processor.unload.DeliveryUnloadingProcessor;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class EndGameUnloadingCompleteDeliveryProcessor implements DeliveryUnloadingProcessor {

  private static Logger Logger =
      LoggerFactory.getLogger(EndGameUnloadingCompleteDeliveryProcessor.class);

  @Autowired private EndGameLabelingService labelingService;

  @Override
  public void doProcess(DeliveryInfo deliveryInfo) {
    if (Objects.nonNull(deliveryInfo.getDeliveryStatus())
        && deliveryInfo
            .getDeliveryStatus()
            .equalsIgnoreCase(DeliveryStatus.UNLOADING_COMPLETE.name())) {
      labelingService.updateStatus(
          LabelStatus.SENT,
          LabelStatus.DELETED,
          "deleted unused delivery labels ",
          deliveryInfo.getDeliveryNumber());
      Logger.info(
          "Unloading status details are updated to deleted status for deliveryNumber={}, trailerNumber={}",
          deliveryInfo.getDeliveryNumber(),
          deliveryInfo.getTrailerNumber());
    }
  }
}
