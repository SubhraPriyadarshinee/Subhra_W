package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.symbotic.LabelGroupUpdateCompletedEventMessage;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.constants.SymTagType;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_KAFKA_LABEL_GROUP_UPDATE_COMPLETED_EVENT_PROCESSOR)
public class RdcLabelGroupUpdateCompletedEventProcessor implements EventProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(RdcLabelGroupUpdateCompletedEventProcessor.class);
  @ManagedConfiguration private AppConfig appConfig;

  @Resource(name = RdcConstants.RDC_COMPLETE_DELIVERY_PROCESSOR)
  private RdcCompleteDeliveryProcessor rdcCompleteDeliveryProcessor;

  @Resource(name = RdcConstants.RDC_DOCKTAG_SERVICE)
  private RdcDockTagService rdcDockTagService;

  @Override
  public void processEvent(MessageData messageData) {
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        (LabelGroupUpdateCompletedEventMessage) messageData;
    HttpHeaders httpHeaders = labelGroupUpdateCompletedEventMessage.getHttpHeaders();
    String facilityNumber = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    if (!appConfig
        .getHawkeyeMessageListenerEnabledFacilities()
        .contains(Integer.valueOf(facilityNumber))) {
      LOGGER.info(
          "Hawkeye message listener is not enabled for facility: {}, skipping this symbotic completion message",
          facilityNumber);
      return;
    }
    if (Objects.isNull(labelGroupUpdateCompletedEventMessage.getInboundTagId())) {
      // Complete the delivery
      try {
        rdcCompleteDeliveryProcessor.completeDelivery(
            Long.valueOf(labelGroupUpdateCompletedEventMessage.getDeliveryNumber()),
            false,
            httpHeaders);
      } catch (Exception e) {
        LOGGER.info("Error while completing delivery {}", e.getMessage());
      }
    } else {
      // MCPIB Scenario - DOCK_TAG(not received), PALLET_TAG(Received, no action needed as of now)
      switch (SymTagType.getSymTagType(labelGroupUpdateCompletedEventMessage.getTagType())) {
        case SYM_MCPIB_TAG_TYPE_DOCKTAG:
          LOGGER.info(
              "MCPIB Complete Event for Dock Tag, completing the docktag {}.",
              labelGroupUpdateCompletedEventMessage.getInboundTagId());
          rdcDockTagService.completeDockTagById(
              labelGroupUpdateCompletedEventMessage.getInboundTagId(), httpHeaders);
          break;
        case SYM_MCPIB_TAG_TYPE_PALLET:
          LOGGER.info(
              "MCPIB Complete Event for Pallet Tag {} - No action needed.",
              labelGroupUpdateCompletedEventMessage.getInboundTagId());
          break;
        default:
          LOGGER.info(
              "Invalid TagType {} for MCPIB Complete Event Pallet {}",
              labelGroupUpdateCompletedEventMessage.getTagType(),
              labelGroupUpdateCompletedEventMessage.getInboundTagId());
      }
    }
  }
}
