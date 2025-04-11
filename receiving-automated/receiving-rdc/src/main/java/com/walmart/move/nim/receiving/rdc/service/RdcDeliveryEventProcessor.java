package com.walmart.move.nim.receiving.rdc.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.event.processor.update.BaseDeliveryProcessor;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.repositories.DeliveryMetaDataRepository;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.utils.RdcLabelGenerationUtils;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_DELIVERY_EVENT_PROCESSOR)
public class RdcDeliveryEventProcessor extends BaseDeliveryProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(RdcDeliveryEventProcessor.class);

  @Autowired private RdcLabelGenerationService rdcLabelGenerationService;
  @Autowired private DeliveryMetaDataRepository deliveryMetaDataRepository;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @ManagedConfiguration private RdcManagedConfig rdcManagedConfig;
  @Autowired private RdcLabelGenerationUtils rdcLabelGenerationUtils;

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    if (!(messageData instanceof DeliveryUpdateMessage)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, messageData);
      return;
    }
    DeliveryUpdateMessage deliveryUpdateMessage = (DeliveryUpdateMessage) messageData;
    if (!validateDeliveryUpdateMessageEvent(deliveryUpdateMessage)) return;
    performDeliveryMetaDataOperation(deliveryUpdateMessage);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            String.valueOf(TenantContext.getFacilityNum()),
            ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED,
            false)
        && (!rdcLabelGenerationUtils.isSSTKPilotDeliveryEnabled()
            || rdcLabelGenerationUtils.isAtlasSSTKPilotDelivery(
                Long.valueOf(deliveryUpdateMessage.getDeliveryNumber())))) {
      TenantContext.get().setOverallSSTKLabelGenerationStart(System.currentTimeMillis());
      LOGGER.info(
          "SSTK Label Generation Started for Delivery {}",
          deliveryUpdateMessage.getDeliveryNumber());
      try {
        if (rdcManagedConfig.isPublishLabelsToHawkeyeByAsyncEnabled()) {
          rdcLabelGenerationService.processDeliveryEventAsync(deliveryUpdateMessage);
        } else {
          rdcLabelGenerationService.processDeliveryEvent(deliveryUpdateMessage);
        }
      } catch (Exception exception) {
        LOGGER.error("Error occurred while processing SSTK Label Generation", exception);
      }
      TenantContext.get().setOverallSSTKLabelGenerationEnd(System.currentTimeMillis());
      LOGGER.info(
          "SSTK Label Generation Completed for Delivery {}, Total Timetaken = {}",
          deliveryUpdateMessage.getDeliveryNumber(),
          ReceivingUtils.getTimeDifferenceInMillis(
              TenantContext.get().getOverallSSTKLabelGenerationStart(),
              TenantContext.get().getOverallSSTKLabelGenerationEnd()));
    }
  }

  public boolean validateDeliveryUpdateMessageEvent(DeliveryUpdateMessage deliveryUpdateMessage) {
    if (StringUtils.isEmpty(deliveryUpdateMessage.getDeliveryNumber())
        || StringUtils.isEmpty(deliveryUpdateMessage.getEventType())
        || (tenantSpecificConfigReader.getConfiguredFeatureFlag(
                String.valueOf(TenantContext.getFacilityNum()),
                ReceivingConstants.RDC_DELIVERY_EVENT_TYPE_CONFIG_ENABLED,
                false)
            && (CollectionUtils.isNotEmpty(rdcManagedConfig.getDeliveryUpdateMessageEventTypes())
                && !rdcManagedConfig
                    .getDeliveryUpdateMessageEventTypes()
                    .contains(deliveryUpdateMessage.getEventType())))) {
      LOGGER.error("Invalid Delivery Update Message Event:{}", deliveryUpdateMessage);
      return false;
    }
    return true;
  }

  private void performDeliveryMetaDataOperation(DeliveryUpdateMessage deliveryUpdateMessage) {
    Optional<DeliveryMetaData> deliveryMetaData =
        deliveryMetaDataRepository.findByDeliveryNumber(
            String.valueOf(deliveryUpdateMessage.getDeliveryNumber()));
    if (deliveryMetaData.isPresent()) {
      LOGGER.info(
          "Delivery: {} is already present in DELIVERY_METADATA table, so skipping this update",
          deliveryUpdateMessage.getDeliveryNumber());
      DeliveryMetaData _dm = deliveryMetaData.get();
      boolean updateDeliveryMetadata = false;

      // Allowed only Receiving status in Delivery Metadata
      if (Objects.nonNull(deliveryUpdateMessage.getDeliveryStatus())
          && !ReceivingUtils.isValidStatus(
              DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()))
          && !StringUtils.equalsIgnoreCase(
              deliveryUpdateMessage.getDeliveryStatus(), _dm.getDeliveryStatus().toString())) {
        // Update Delivery Metadata
        _dm.setDeliveryStatus(DeliveryStatus.valueOf(deliveryUpdateMessage.getDeliveryStatus()));
        updateDeliveryMetadata = true;
        LOGGER.info(
            "Updating the delivery status in deliveryMetadata for delivery = {} to status = {} ",
            _dm.getDeliveryNumber(),
            deliveryUpdateMessage.getDeliveryStatus());
      }
      if (deliveryUpdateMessage.getEventType().equals(ReceivingConstants.EVENT_DOOR_ASSIGNED)
          && Objects.nonNull(deliveryUpdateMessage.getDoorNumber())
          && (Objects.isNull(_dm.getDoorNumber())
              || !StringUtils.equals(deliveryUpdateMessage.getDoorNumber(), _dm.getDoorNumber()))) {
        _dm.setDoorNumber(deliveryUpdateMessage.getDoorNumber());
        updateDeliveryMetadata = true;
        LOGGER.info(
            "Updating the door number {} in deliveryMetadata for delivery = {}",
            deliveryUpdateMessage.getDoorNumber(),
            _dm.getDeliveryNumber());
      }
      if (updateDeliveryMetadata) {
        deliveryMetaDataRepository.save(_dm);
      }
    } else {
      Delivery delivery = super.getDelivery(deliveryUpdateMessage);
      LOGGER.info(
          "Persisting delivery: {} information into DELIVERY_METADATA table",
          deliveryUpdateMessage.getDeliveryNumber());
      super.createMetaData(delivery);
    }
  }
}
