package com.walmart.move.nim.receiving.endgame.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.UpdateAttributesData;
import com.walmart.move.nim.receiving.endgame.model.ExpiryDateUpdatePublisherData;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Objects;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class EndgameExpiryDateProcessor implements EventProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndgameExpiryDateProcessor.class);

  @Autowired private EndgameContainerService endgameContainerService;
  @Autowired private EndGameSlottingService endGameSlottingService;

  @Override
  @TimeTracing(component = AppComponent.ENDGAME, type = Type.MESSAGE, flow = "Expiry")
  public void processEvent(MessageData messageData) throws ReceivingException {

    if (!(messageData instanceof UpdateAttributesData)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }

    UpdateAttributesData updateAttributesData = (UpdateAttributesData) messageData;

    if (!ObjectUtils.allNotNull(
            updateAttributesData,
            updateAttributesData.getSearchCriteria(),
            updateAttributesData.getSearchCriteria().getDeliveryNumber(),
            updateAttributesData.getSearchCriteria().getTrackingId(),
            updateAttributesData.getUpdateAttributes())
        || ObjectUtils.isEmpty(updateAttributesData.getSearchCriteria().getItemNumber())
        || ObjectUtils.isEmpty(updateAttributesData.getUpdateAttributes().getRotateDate())) {
      String errorMessage =
          String.format(
              EndgameConstants.INVALID_EXPIRY_DATE_LISTENER_DATA_ERROR_MSG, updateAttributesData);

      LOGGER.error(errorMessage);
      throw new ReceivingBadDataException(
          ExceptionCodes.INVALID_EXPIRY_DATE_LISTENER_DATA, errorMessage);
    }

    LOGGER.info(EndgameConstants.LOG_EXPIRY_DATE_UPDATE, updateAttributesData);
    /*
     Not blocking inventory update after expiry date is captured if hawkeye update, slotting call or item mdm call fails,
     So Catching any @RuntimeException while updating to hawkeye for divert destination for this item number.

    */
    try {
      endGameSlottingService.updateDivertForItemAndDelivery(updateAttributesData);
    } catch (RuntimeException e) {
      LOGGER.error(
          EndgameConstants
              .UNABLE_TO_UPDATE_DIVERT_DESTINATION_TO_HAWKEYE_AFTER_CAPTURING_EXPIRY_DATE,
          updateAttributesData.getSearchCriteria().getItemNumber(),
          updateAttributesData.getSearchCriteria().getTrackingId(),
          ExceptionUtils.getStackTrace(e));
    }
    ExpiryDateUpdatePublisherData expiryDateUpdatePublisherData =
        endgameContainerService.updateRotateDate(
            updateAttributesData.getSearchCriteria().getDeliveryNumber(), updateAttributesData);
    if (Objects.nonNull(expiryDateUpdatePublisherData)) {
      endgameContainerService.publishContainerUpdate(expiryDateUpdatePublisherData);
    }
  }
}
