package com.walmart.move.nim.receiving.acc.service;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.RoboDepalEventMessage;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.docktag.ReceiveDockTagRequest;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.scm.client.shared.logging.Logger;
import com.walmart.platform.scm.client.shared.logging.LoggerFactory;
import io.strati.configuration.annotation.ManagedConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RoboDepalEventProcessor implements EventProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(RoboDepalEventProcessor.class);
  @ManagedConfiguration private AppConfig appConfig;
  @Autowired private RoboDepalEventService roboDepalEventService;

  @Override
  public void processEvent(MessageData messageData) {
    RoboDepalEventMessage roboDepalEventMessage = (RoboDepalEventMessage) messageData;
    HttpHeaders headers = new HttpHeaders();
    headers.add(ReceivingConstants.DOCKTAG_EVENT_TIMESTAMP, roboDepalEventMessage.getMessageTs());
    headers.add(ReceivingConstants.USER_ID_HEADER_KEY, appConfig.getRoboDepalUserId());

    ReceiveDockTagRequest receiveDockTagRequest = new ReceiveDockTagRequest();
    receiveDockTagRequest.setDockTagId(roboDepalEventMessage.getTrackingId());
    receiveDockTagRequest.setWorkstationLocation(roboDepalEventMessage.getSourceLocationId());
    receiveDockTagRequest.setMappedFloorLineLocation(roboDepalEventMessage.getEquipmentName());

    String eventType = roboDepalEventMessage.getMessageType();
    LOGGER.info(
        "Processing Robo Depal event: {} for docktag: {}",
        eventType,
        roboDepalEventMessage.getTrackingId());
    switch (eventType) {
      case ACCConstants.ROBO_DEPAL_ACK_EVENT:
        roboDepalEventService.processDepalAckEvent(receiveDockTagRequest, headers);
        break;
      case ACCConstants.ROBO_DEPAL_FINISH_EVENT:
        roboDepalEventService.processDepalFinishEvent(receiveDockTagRequest, headers);
        break;
      default:
        LOGGER.warn(
            "Invalid event type: {} received for docktag: {}",
            roboDepalEventMessage.getMessageType(),
            roboDepalEventMessage.getTrackingId());
        break;
    }
  }
}
