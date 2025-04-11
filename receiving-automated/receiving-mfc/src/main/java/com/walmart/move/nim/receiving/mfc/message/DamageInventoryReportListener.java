package com.walmart.move.nim.receiving.mfc.message;

import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNode;
import com.walmart.move.nim.receiving.mfc.common.MFCConstant;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.service.InventoryAdjustmentHelper;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;

public class DamageInventoryReportListener {
  private static Logger LOGGER = LoggerFactory.getLogger(DamageInventoryReportListener.class);
  @Autowired private InventoryAdjustmentHelper inventoryAdjustmentHelper;

  @ManagedConfiguration private MFCManagedConfig mfcManagedConfig;

  @Timed(
      name = "damageInventoryReportTimed",
      level1 = "uwms-receiving-api",
      level2 = "damageInventoryReportListener")
  @ExceptionCounted(
      name = "damageInventoryReportExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "damageInventoryReportListener")
  @KafkaListener(
      topics = "${damaged.mfc.inventory.state.change}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(
      @Payload String message,
      @Header("eventType") byte[] event,
      @Header(value = MFCConstant.FLOW_DESCRIPTOR, required = false) byte[] flowType) {

    if (!mfcManagedConfig.isEnableAutoMFCExceptionProcessing()) {
      LOGGER.warn("Auto MFC - Exception Processing is disabled. Hence ignoring the message");
      return;
    }
    // Ignore mixed pallet reject
    if (Objects.nonNull(flowType)
        && StringUtils.equalsIgnoreCase(MFCConstant.MIXED_PALLET_REJECT, new String(flowType))) {
      LOGGER.warn("Mixed pallet reject detected and hence, not action on it ");
      return;
    }

    // Flow for Reject / Damage from Mobile App
    String eventType = new String(event);
    LOGGER.info("Entering to DamageInventoryReportListener with eventType = {}", eventType);

    inventoryAdjustmentHelper.processInventoryAdjustment(message, eventType);

    LOGGER.info("Exiting from DamageInventoryReportListener");
  }
}
