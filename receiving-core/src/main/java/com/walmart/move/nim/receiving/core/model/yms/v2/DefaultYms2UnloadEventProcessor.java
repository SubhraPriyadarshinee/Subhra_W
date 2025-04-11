package com.walmart.move.nim.receiving.core.model.yms.v2;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_YMS2_UNLOAD_EVENT_PROCESSOR;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value = DEFAULT_YMS2_UNLOAD_EVENT_PROCESSOR)
public class DefaultYms2UnloadEventProcessor implements Yms2UnloadEventProcessor {
  @Autowired private ProcessInitiator processInitiator;
  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultYms2UnloadEventProcessor.class);

  @Override
  public void processYMSUnloadingEvent(Long deliveryNumber) throws ReceivingException {
    LOGGER.info(
        "Sending Unloading message during deliveryComplete is processing for delivery={}",
        deliveryNumber);

    ProgressUpdateDTO progressUpdateDTO =
        ProgressUpdateDTO.builder()
            .deliveryNumber(deliveryNumber)
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .build();

    Map<String, Object> additionalAttribute = new HashMap<>();

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(ReceivingUtils.stringfyJson(progressUpdateDTO))
            .name(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .processor(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
            .build();
    LOGGER.info("Going to initiate delivery update progress for delivery={}", deliveryNumber);
    processInitiator.initiateProcess(receivingEvent, additionalAttribute);

    LOGGER.info(
        "Sending Unloading message during deliveryComplete is completed for delivery={}",
        deliveryNumber);
  }
}
