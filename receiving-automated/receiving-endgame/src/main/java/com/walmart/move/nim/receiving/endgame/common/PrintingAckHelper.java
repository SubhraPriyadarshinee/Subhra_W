package com.walmart.move.nim.receiving.endgame.common;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.PrinterACK;
import com.walmart.move.nim.receiving.endgame.model.EndGameLabelData;
import com.walmart.move.nim.receiving.endgame.model.LabelRequestVO;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Arrays;
import java.util.Map;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class PrintingAckHelper {

  private static final Logger LOGGER = LoggerFactory.getLogger(PrintingAckHelper.class);

  @ManagedConfiguration private EndgameManagedConfig endgameManagedConfig;

  @Autowired private Gson gson;

  @Resource(name = EndgameConstants.ENDGAME_DELIVERY_METADATA_SERVICE)
  private DeliveryMetaDataService deliveryMetaDataService;

  @Autowired private EndGameLabelingService labelingService;
  @Autowired private TenantSpecificConfigReader configUtils;

  public void doProcess(String message, Map<String, byte[]> kafkaHeaders) {
    LOGGER.debug("Got PrintAck [message={}]", message);
    if (!configUtils.isFacilityEnabled(TenantContext.getFacilityNum())) {
      LOGGER.error(
          "Facility number [facilityNum={}] is not served in this cluster.",
          TenantContext.getFacilityNum());
      return;
    }

    PrinterACK printerACK = gson.fromJson(message, PrinterACK.class);

    if (printerACK.getStatus() != null && printerACK.getStatus().equalsIgnoreCase("FAILED")) {
      invokeFailedProcess(printerACK);
      return;
    }

    PreLabelData preLabelData =
        labelingService
            .findByTcl(printerACK.getTrailerCaseLabel())
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.TCL_NOT_FOUND,
                        String.format(
                            EndgameConstants.TCL_NOT_FOUND_ERROR_MSG,
                            printerACK.getTrailerCaseLabel())));

    // Return if TCL is already above SENT status
    if (preLabelData != null
        && !preLabelData.getStatus().getStatus().equalsIgnoreCase(LabelStatus.SENT.getStatus())) {
      LOGGER.error(
          "TCL [tcl={}] is already attached so cannot attached again",
          printerACK.getTrailerCaseLabel());
      return;
    }
    preLabelData.setStatus(LabelStatus.ATTACHED);
    labelingService.saveOrUpdateLabel(preLabelData);

    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService
            .findByDeliveryNumber(String.valueOf(preLabelData.getDeliveryNumber()))
            .orElseThrow(
                () ->
                    new ReceivingDataNotFoundException(
                        ExceptionCodes.DELIVERY_METADATA_NOT_FOUND,
                        String.format(
                            EndgameConstants.DELIVERY_METADATA_NOT_FOUND_ERROR_MSG,
                            preLabelData.getDeliveryNumber(),
                            preLabelData.getDeliveryNumber())));

    long attachedQty =
        labelingService.countByStatusInAndDeliveryNumber(
            Arrays.asList(LabelStatus.ATTACHED, LabelStatus.SCANNED, LabelStatus.DELETED),
            Long.valueOf(deliveryMetaData.getDeliveryNumber()));

    long extraLabelQty = calculateThreshold(deliveryMetaData, attachedQty);

    if (extraLabelQty > 0) {

      LabelRequestVO labelRequestVO =
          LabelRequestVO.builder()
              .labelGenMode(LabelGenMode.AUTOMATED)
              .deliveryNumber(String.valueOf(preLabelData.getDeliveryNumber()))
              .door(deliveryMetaData.getDoorNumber())
              .trailerId(deliveryMetaData.getTrailerNumber())
              .quantity(extraLabelQty)
              .type(LabelType.TCL)
              .build();

      EndGameLabelData labelData = labelingService.generateLabel(labelRequestVO);
      LOGGER.debug(
          "Extra TCL got generated for Delivery [delivery={}]", preLabelData.getDeliveryNumber());
      labelingService.persistLabel(labelData);
      String response = labelingService.send(labelData);
      updateStatus(response, Long.valueOf(labelData.getDeliveryNumber()));
    }
  }

  // TODO Need to put Medussa Annotation for Medussa dashboard
  private void invokeFailedProcess(PrinterACK printerACK) {
    LOGGER.error(
        "Got failed event for TCL [tcl={}] and its reason [reason={}]",
        printerACK.getTrailerCaseLabel(),
        printerACK.getReason());
  }

  private long calculateThreshold(DeliveryMetaData deliveryMetaData, long attachedQty) {
    if (deliveryMetaData.getTotalCaseLabelSent() * endgameManagedConfig.getLabelingThresholdLimit()
        <= attachedQty) {
      return endgameManagedConfig.getExtraTCLToSend();
    }
    return 0;
  }

  private void updateStatus(String response, Long deliveryNumber) {
    if (EndgameConstants.SUCCESS_MSG.equalsIgnoreCase(response)) {
      labelingService.updateStatus(
          LabelStatus.GENERATED, LabelStatus.SENT, response, deliveryNumber);
    } else {
      labelingService.updateStatus(
          LabelStatus.GENERATED, LabelStatus.FAILED, response, deliveryNumber);
      throw new ReceivingInternalException(
          ExceptionCodes.KAFKA_NOT_ACCESSABLE,
          String.format(
              ReceivingConstants.KAFKA_NOT_ACCESSIBLE_ERROR_MSG, EndgameConstants.TCL_UPLOAD_FLOW));
    }
  }
}
