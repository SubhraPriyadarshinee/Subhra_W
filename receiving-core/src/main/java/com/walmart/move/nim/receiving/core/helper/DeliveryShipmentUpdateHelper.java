package com.walmart.move.nim.receiving.core.helper;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DeliveryShipmentUpdateHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryShipmentUpdateHelper.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public void doProcess(@Payload String message) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.LOG_WRONG_MESSAGE_FORMAT, message);
      return;
    }
    LOGGER.info("Got delivery/shipment update from GDM. payload = {} ", message);
    DeliveryUpdateMessage deliveryUpdateMessage =
        JacksonParser.convertJsonToObject(message, DeliveryUpdateMessage.class);
    if (ObjectUtils.allNotNull(
        deliveryUpdateMessage,
        deliveryUpdateMessage.getEvent(),
        deliveryUpdateMessage.getPayload())) {
      ReceivingUtils.setTenantContext(
          deliveryUpdateMessage.getEvent().getFacilityNum().toString(),
          deliveryUpdateMessage.getEvent().getFacilityCountryCode(),
          deliveryUpdateMessage.getEvent().getWMTCorrelationId(),
          this.getClass().getName());
      try {
        EventProcessor deliveryEventProcessor =
            tenantSpecificConfigReader.getConfiguredInstance(
                TenantContext.getFacilityNum().toString(),
                ReceivingConstants.KAFKA_DELIVERY_EVENT_HANDLER,
                EventProcessor.class);

        deliveryEventProcessor.processEvent(deliveryUpdateMessage);

        LOGGER.info("Delivery/Shipment update from GDM processing completed");
      } catch (ReceivingException e) {
        LOGGER.error("Unable to process the gdm update {}", ExceptionUtils.getStackTrace(e));
        throw new ReceivingInternalException(
            ExceptionCodes.GDM_ERROR,
            String.format(ReceivingConstants.UNABLE_TO_PROCESS_DEL_UPDATE_ERROR_MSG, message),
            e);
      }
      TenantContext.clear();
    }
  }
}
