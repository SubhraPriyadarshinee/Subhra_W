package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.stringfyJson;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.TCL_NOT_FOUND;
import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.VALID_DIVERTS_TO_RECEIVE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.INVALID_DIVERT_STATUS;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.TCL_ALREADY_SCANNED_UNABLE_TO_SCAN_AGAIN;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASN_RECEIVING_ENABLED;
import static org.springframework.util.CollectionUtils.isEmpty;

import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.AttachPurchaseOrderRequest;
import com.walmart.move.nim.receiving.endgame.model.DimensionPayload;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class EndGameDivertAckEventProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(EndGameDivertAckEventProcessor.class);

  @ManagedConfiguration private EndgameManagedConfig endgameManagedConfig;

  @Resource(name = EndgameConstants.ENDGAME_RECEIVING_SERVICE)
  private EndGameReceivingService endGameReceivingService;

  @Resource(name = EndgameConstants.ENDGAME_ASN_RECEIVING_SERVICE)
  private EndGameReceivingService endGameAsnReceivingService;

  @Autowired private EndgameDecantService endgameDecantService;
  @Autowired private EndGameLabelingService endGameLabelingService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Override
  public void processEvent(MessageData messageData) {
    if (!(messageData instanceof ScanEventData)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }
    ScanEventData scanEventData = (ScanEventData) messageData;
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(ReceivingConstants.DYNAMIC_PO_FEATURE_FLAG)
        && !isEmpty(scanEventData.getPoNumbers())) {
      AttachPurchaseOrderRequest request =
          AttachPurchaseOrderRequest.builder()
              .deliveryNumber(scanEventData.getDeliveryNumber())
              .poNumbers(scanEventData.getPoNumbers())
              .build();
      String jsonRequest = stringfyJson(request);
      endGameReceivingService.persistAttachPurchaseOrderRequestToOutbox(jsonRequest);
    }
    PreLabelData preLabelData =
        endGameLabelingService
            .findByTcl(scanEventData.getTrailerCaseLabel())
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        TCL_NOT_FOUND,
                        String.format(
                            EndgameConstants.TCL_NOT_FOUND_ERROR_MSG,
                            scanEventData.getTrailerCaseLabel())));

    if (preLabelData.getStatus().getStatus().equalsIgnoreCase(LabelStatus.SCANNED.getStatus())) {
      preLabelData.setReason(TCL_ALREADY_SCANNED_UNABLE_TO_SCAN_AGAIN);
      endGameLabelingService.saveOrUpdateLabel(preLabelData);
      LOGGER.error(EndgameConstants.LOG_TCL_ALREADY_SCANNED, scanEventData.getTrailerCaseLabel());
      return;
    }
    updateLabelWithDivertAckEvent(preLabelData, messageData);

    if (!isValidDivertAckToReceive(scanEventData)) {
      preLabelData.setReason(INVALID_DIVERT_STATUS);
      preLabelData.setStatus(LabelStatus.SCANNED);
      endGameLabelingService.saveOrUpdateLabel(preLabelData);
      return;
    }

    endGameReceivingService.enrichContainerTag(scanEventData);
    endGameReceivingService.verifyContainerReceivable(scanEventData.getTrailerCaseLabel());
    scanEventData.setPreLabelData(preLabelData);
    boolean isAsnReceiving = validForAsnReceiving(scanEventData);
    ReceiveVendorPack receiveVendorPack =
        (isAsnReceiving ? endGameAsnReceivingService : endGameReceivingService)
            .receiveVendorPack(scanEventData);
    Boolean isContainerReceived = Objects.nonNull(receiveVendorPack.getContainer());

    if (isContainerReceived) {
      preLabelData.setStatus(LabelStatus.SCANNED);
    }
    endGameLabelingService.saveOrUpdateLabel(preLabelData);

    if (endgameManagedConfig.isPublishVendorDimension() && isContainerReceived) {
      publishVendorPackDimensions(scanEventData, receiveVendorPack.getContainer());
    }
  }

  private boolean validForAsnReceiving(ScanEventData scanEventData) {
    return tenantSpecificConfigReader.isFeatureFlagEnabled(ASN_RECEIVING_ENABLED)
        && !CollectionUtils.isEmpty(scanEventData.getBoxIds());
  }

  private void updateLabelWithDivertAckEvent(PreLabelData preLabelData, MessageData messageData) {
    if (!StringUtils.hasLength(preLabelData.getDiverAckEvent())
        && !StringUtils.hasLength(preLabelData.getCaseUpc())) {
      ReceivingRequest receivingRequest = (ReceivingRequest) messageData;
      if (Objects.isNull(receivingRequest.getIsMultiSKU())) {
        receivingRequest.setIsMultiSKU(Boolean.FALSE);
      }
      preLabelData.setCaseUpc(receivingRequest.getCaseUPC());
      preLabelData.setDiverAckEvent(ReceivingUtils.stringfyJson(receivingRequest));
    }
  }

  public void publishVendorPackDimensions(ScanEventData scanEventData, Container container) {
    if (Objects.nonNull(scanEventData.getDimensions())) {
      ContainerItem containerItem = container.getContainerItems().stream().findAny().get();
      DimensionPayload dimensionPayload =
          DimensionPayload.builder()
              .caseUPC(containerItem.getCaseUPC())
              .itemUPC(containerItem.getItemUPC())
              .itemNumber(containerItem.getItemNumber())
              .dimensions(scanEventData.getDimensions())
              .dimensionsUnitOfMeasure(scanEventData.getDimensionsUnitOfMeasure())
              .build();
      endgameDecantService.publish(dimensionPayload);
    }
  }

  private boolean isValidDivertAckToReceive(ScanEventData scanEventData) {
    if (!VALID_DIVERTS_TO_RECEIVE.contains(scanEventData.getDiverted().getStatus())) {
      LOGGER.warn(
          EndgameConstants.LOG_TCL_NOT_RECEIVED,
          scanEventData.getTrailerCaseLabel(),
          scanEventData.getDiverted().getStatus());
      return false;
    }
    return true;
  }
}
