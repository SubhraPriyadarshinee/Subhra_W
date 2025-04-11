package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DEFAULT_KAFKA_INVENTORY_EVENT_PROCESSOR)
public class DefaultKafkaInventoryEventProcessor implements EventProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultKafkaInventoryEventProcessor.class);

  @Override
  public void processEvent(MessageData messageData) throws ReceivingException {
    LOGGER.warn(
        "No implementation available to process inventory adjustment message in this tenant");
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
