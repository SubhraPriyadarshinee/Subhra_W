package com.walmart.move.nim.receiving.endgame.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REPLEN_CASE;
import static org.springframework.util.ObjectUtils.isEmpty;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Map;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class DivertAckHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(DivertAckHelper.class);

  @Autowired private Gson gson;

  @Resource(name = EndgameConstants.ENDGAME_DIVERT_ACK_EVENT_PROCESSOR)
  private EventProcessor eventProcessor;

  @Autowired private TenantSpecificConfigReader configUtils;

  public void doProcess(String message, Map<String, byte[]> kafkaHeaders) {
    if (!configUtils.isFacilityEnabled(TenantContext.getFacilityNum())) {
      LOGGER.debug(
          "Facility number [facilityNum={}] is not served in this cluster.",
          TenantContext.getFacilityNum());
      return;
    }
    if (REPLEN_CASE.equalsIgnoreCase(TenantContext.getEventType())) {
      LOGGER.warn(
          "Ignoring receiving of Replen case for event type = {}", TenantContext.getEventType());
      return;
    }
    if (isEmpty(message)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, EndgameConstants.EMPTY_STRING);
      return;
    }
    ReceivingRequest scanEventData = gson.fromJson(message, ReceivingRequest.class);
    LOGGER.info(EndgameConstants.LOG_SCAN_EVENT, scanEventData);

    try {
      setSCTHeaders();
      eventProcessor.processEvent(scanEventData);
    } catch (ReceivingException re) {
      throw new ReceivingInternalException(
          ExceptionCodes.UNABLE_TO_CREATE_CONTAINER,
          String.format(
              EndgameConstants.UNABLE_TO_CREATE_CONTAINER_ERROR_MSG,
              scanEventData.getTrailerCaseLabel()),
          re);
    }
  }

  /** Setting attributes for SCT */
  private void setSCTHeaders() {
    TenantContext.setAdditionalParams("caseAutoReceived", Boolean.TRUE);
  }
}
