package com.walmart.move.nim.receiving.core.helper;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Map;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GdmDeliveryUpdateListenerHelper {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(GdmDeliveryUpdateListenerHelper.class);
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private Gson gson;

  @ManagedConfiguration private AppConfig appConfig;

  public void doProcess(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {
    if (StringUtils.isEmpty(message)) {
      LOGGER.error(ReceivingConstants.WRONG_DELIVERY_UPDATE_MESSAGE_FORMAT, message);
      return;
    }

    LOGGER.info("Received delivery update message from GDM, message: {}", message);
    try {
      DeliveryUpdateMessage deliveryUpdateMessage =
          JacksonParser.convertJsonToObject(message, DeliveryUpdateMessage.class);
      deliveryUpdateMessage.setHttpHeaders(
          ReceivingUtils.populateHttpHeadersFromKafkaHeaders(kafkaHeaders));
      Integer facilityNum = TenantContext.getFacilityNum();

      if (!appConfig.getGdmDeliveryUpdateKafkaListenerEnabledFacilities().contains(facilityNum)) {
        LOGGER.info(
            "Consuming GDM delivery update from kafka listener is not enabled for facility: {}, skipping this delivery update message",
            facilityNum);
        return;
      }

      EventProcessor deliveryEventProcessor =
          tenantSpecificConfigReader.getDeliveryEventProcessor(String.valueOf(facilityNum));

      deliveryEventProcessor.processEvent(deliveryUpdateMessage);

      LOGGER.info(
          "Successfully consumed GDM delivery update for delivery:{}",
          deliveryUpdateMessage.getDeliveryNumber());
    } catch (ReceivingException excp) {
      LOGGER.error(
          "Unable to process GDM delivery update message - {}", ExceptionUtils.getStackTrace(excp));
      throw new ReceivingInternalException(
          ExceptionCodes.GDM_ERROR,
          String.format(ReceivingConstants.UNABLE_TO_PROCESS_DEL_UPDATE_ERROR_MSG, message),
          excp);
    }
  }
}
