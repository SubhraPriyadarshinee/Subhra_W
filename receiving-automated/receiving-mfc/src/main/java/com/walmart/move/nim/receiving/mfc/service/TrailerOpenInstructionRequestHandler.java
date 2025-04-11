package com.walmart.move.nim.receiving.mfc.service;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.service.InstructionSearchRequestHandler;
import com.walmart.move.nim.receiving.mfc.common.StoreDeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class TrailerOpenInstructionRequestHandler implements InstructionSearchRequestHandler {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(TrailerOpenInstructionRequestHandler.class);
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Autowired private MFCDeliveryMetadataService deliveryMetaDataService;

  @Override
  public List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers) {

    if (Objects.nonNull(instructionSearchRequest.getDeliveryNumber())
        && (DeliveryStatus.ARV
                .toString()
                .equalsIgnoreCase(instructionSearchRequest.getDeliveryStatus())
            || DeliveryStatus.SCH
                .toString()
                .equalsIgnoreCase(instructionSearchRequest.getDeliveryStatus()))) {
      DeliveryMetaData deliveryMetaData =
          deliveryMetaDataService
              .findByDeliveryNumber(instructionSearchRequest.getDeliveryNumber().toString())
              .orElse(
                  DeliveryMetaData.builder()
                      .deliveryNumber(instructionSearchRequest.getDeliveryNumber().toString())
                      .build());
      if (Objects.nonNull(deliveryMetaData.getDeliveryStatus())
          && !StoreDeliveryStatus.isValidDeliveryStatusForUpdate(
              StoreDeliveryStatus.getDeliveryStatus(deliveryMetaData.getDeliveryStatus()),
              StoreDeliveryStatus.OPEN)) {
        LOGGER.info(
            "Current delivery status is {} hence ignore delivery update for status {}",
            deliveryMetaData.getDeliveryStatus(),
            DeliveryStatus.OPEN.name());
        return Collections.emptyList();
      }
      deliveryStatusPublisher.publishDeliveryStatus(
          instructionSearchRequest.getDeliveryNumber(),
          DeliveryStatus.OPEN.toString(),
          null,
          headers);
      deliveryMetaData.setDeliveryStatus(DeliveryStatus.OPEN);
      deliveryMetaDataService.save(deliveryMetaData);
    }
    return Collections.emptyList();
  }
}
