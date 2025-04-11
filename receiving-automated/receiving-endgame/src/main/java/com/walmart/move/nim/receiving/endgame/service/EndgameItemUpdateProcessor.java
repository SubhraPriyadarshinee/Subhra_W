package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class EndgameItemUpdateProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndgameItemUpdateProcessor.class);

  @Autowired private EndGameSlottingService endGameSlottingService;

  @Override
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.MESSAGE, flow = "FTS-Update")
  public void processEvent(MessageData messageData) throws ReceivingException {

    if (!(messageData instanceof UpdateAttributesData)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }

    UpdateAttributesData updateAttributesData = (UpdateAttributesData) messageData;

    if (!ObjectUtils.allNotNull(
        updateAttributesData,
        updateAttributesData.getSearchCriteria(),
        updateAttributesData.getSearchCriteria().getCaseUPC(),
        updateAttributesData.getSearchCriteria().getItemUPC(),
        updateAttributesData.getSearchCriteria().getItemNumber(),
        updateAttributesData.getItemAttributes())) {
      LOGGER.warn(
          "Item Attributes are not present for FTS operation. [message={}]",
          ReceivingUtils.stringfyJson(updateAttributesData));
      return;
    }

    LOGGER.info(
        EndgameConstants.LOG_ITEM_UPDATE_PROCESSOR_FTS,
        updateAttributesData.getSearchCriteria().getItemNumber());
    endGameSlottingService.updateDivertForItem(updateAttributesData);
  }
}
