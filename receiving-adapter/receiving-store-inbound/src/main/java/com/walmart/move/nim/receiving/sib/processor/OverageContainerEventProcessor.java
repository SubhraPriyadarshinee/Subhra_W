package com.walmart.move.nim.receiving.sib.processor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.core.common.GsonUTCDateAdapter;
import com.walmart.move.nim.receiving.core.framework.message.processor.ProcessExecutor;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.service.EventRegistrationService;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryMetadataService;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

public class OverageContainerEventProcessor implements ProcessExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(OverageContainerEventProcessor.class);

  private static final String ORIGINAL_DELIVERY_NUMBER = "originalDeliveryNumber";

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private EventRegistrationService eventRegistrationService;

  @Resource(name = Constants.STORE_DELIVERY_METADATA_SERVICE)
  private StoreDeliveryMetadataService deliveryMetaDataService;

  private Gson gson;

  public OverageContainerEventProcessor() {
    this.gson =
        gson = new GsonBuilder().registerTypeAdapter(Date.class, new GsonUTCDateAdapter()).create();
  }

  @Override
  public void doExecute(ReceivingEvent receivingEvent) {
    if (StringUtils.isEmpty(receivingEvent.getPayload())) {
      LOGGER.info("Event payload cannot be empty for eventType {}", receivingEvent.getName());
      return;
    }

    ContainerDTO containerDTO = gson.fromJson(receivingEvent.getPayload(), ContainerDTO.class);

    LOGGER.info(
        "Overage container with trackingId = {} registration is starting ",
        containerDTO.getTrackingId());
    eventRegistrationService.processEventOperation(Collections.singletonList(containerDTO), null);
    LOGGER.info(
        "Overage container with trackingId = {} is registed successfully",
        containerDTO.getTrackingId());
  }

  @Override
  public boolean isAsync() {
    return false;
  }
}
