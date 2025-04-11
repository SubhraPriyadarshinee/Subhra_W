package com.walmart.move.nim.receiving.core.message.listener;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.sanitize;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.UUID;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.StringUtils;

/**
 * This is a {@link JmsListener} which listen the GDM DeliveryUpdate topic. And will listen only
 * {@link ReceivingConstants#EVENT_PO_LINE_UPDATED}, {@link ReceivingConstants#EVENT_PO_UPDATED},
 * {@link ReceivingConstants#EVENT_PO_ADDED}, {@link ReceivingConstants#EVENT_PO_LINE_ADDED}, {@link
 * ReceivingConstants#EVENT_DOOR_ASSIGNED} events from GDM.
 *
 * @see <a href="https://collaboration.wal-mart.com/display/GDM/GDM+-+Events">Reference docs</a>
 * @author sitakant
 */
public class DeliveryUpdateListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryUpdateListener.class);

  @Autowired private Gson gson;
  @Autowired private TenantSpecificConfigReader configUtils;
  @ManagedConfiguration private AppConfig appConfig;

  @JmsListener(
      destination = "${queue.gdm.delivery.update}",
      containerFactory = "receivingJMSListener")
  public void listen(Message message, MessageHeaders messageHeaders)
      throws JMSException, ReceivingException {

    String facilityNumber =
        sanitize(message.getStringProperty(ReceivingConstants.TENENT_FACLITYNUM));
    String countryCode =
        sanitize(message.getStringProperty(ReceivingConstants.TENENT_COUNTRY_CODE));
    String correlationIdFromHeaders =
        sanitize(message.getStringProperty(ReceivingConstants.JMS_CORRELATION_ID));
    String correlationId =
        StringUtils.isEmpty(correlationIdFromHeaders)
            ? UUID.randomUUID().toString()
            : correlationIdFromHeaders;

    if (!(message instanceof TextMessage)) {
      LOGGER.warn("Message format on DeliveryUpdate Listener is wrong");
      return;
    }

    if (appConfig
        .getGdmDeliveryUpdateKafkaListenerEnabledFacilities()
        .contains(Integer.valueOf(facilityNumber))) {
      LOGGER.info(
          "Consuming GDM delivery update from kafka listener is enabled for facility: {}, skipping this delivery update message",
          facilityNumber);
      return;
    }

    String deliveryMessage = sanitize(((TextMessage) message).getText());
    LOGGER.info("Got delivery update from GDM. payload = {} ", deliveryMessage);
    DeliveryUpdateMessage deliveryUpdateMessage =
        gson.fromJson(deliveryMessage, DeliveryUpdateMessage.class);

    ReceivingUtils.setTenantContext(
        facilityNumber, countryCode, correlationId, this.getClass().getName());

    EventProcessor deliveryEventProcessor = configUtils.getDeliveryEventProcessor(facilityNumber);

    deliveryEventProcessor.processEvent(deliveryUpdateMessage);

    LOGGER.info("Delivery update from GDM processing completed");
    TenantContext.clear();
  }
}
