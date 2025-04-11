package com.walmart.move.nim.receiving.core.message.listener;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.MessageHeaders;

/**
 * This is the JMS Listener for InventoryAdjustment
 *
 * @author sitakant
 */
public class InventoryAdjustmentListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAdjustmentListener.class);
  private JsonParser parser = new JsonParser();

  @ManagedConfiguration private AppConfig appConfig;
  @Autowired TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * Listener for inventory adjustments
   *
   * @param message
   * @param messageHeaders
   */
  @JmsListener(
      destination = "${queue.inventory.adjustment}",
      containerFactory = "receivingJMSListener")
  public void inventoryAdjustmentListener(Message message, MessageHeaders messageHeaders) {
    LOGGER.info("Received inventory adjustment with headers:{}", messageHeaders);
    LOGGER.info("Received inventory adjustment with message:{}", message);
    try {
      // Check if the pubsub is enabled
      if (appConfig.getPubsubEnabled() != null && !appConfig.getPubsubEnabled()) {
        LOGGER.info("Ignoring inventory adjustment because pubsub is not enabled");
        return;
      }

      if (!isValidJMSTextMessage(message)) {
        LOGGER.info("Ignoring inventory adjustment because invalid JMS TextMessage");
      }

      // Setting tenant context
      ReceivingUtils.setContextFromMsgHeaders(messageHeaders, this.getClass().getName());

      TextMessage textMessage = (TextMessage) message;
      InventoryAdjustmentTO inventoryAdjustmentTO = new InventoryAdjustmentTO();

      inventoryAdjustmentTO.setHttpHeaders(
          ReceivingUtils.getHttpHeadersFromMessageHeaders(messageHeaders));
      inventoryAdjustmentTO.setJsonObject(
          parser.parse(sanitize(textMessage.getText())).getAsJsonObject());
      EventProcessor inventoryAdjustmentProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.INVENTORY_ADJUSTMENT_PROCESSOR,
              EventProcessor.class);
      inventoryAdjustmentProcessor.processEvent(inventoryAdjustmentTO);
      TenantContext.clear();
    } catch (Exception err) {
      LOGGER.error(
          "Exception occurred processing inventory adjustment : {}",
          ExceptionUtils.getStackTrace(err));
    }
  }

  private boolean isValidJMSTextMessage(Message message) {
    return message instanceof TextMessage;
  }
}
