package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.sib.utils.Constants.ISO_FORMAT_STRING_REQUEST;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNode;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.service.EventRegistrationService;
import com.walmart.move.nim.receiving.sib.service.PackContainerService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;
public class DeliveryStatusListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DeliveryStatusListener.class);
  private Gson gson = new GsonBuilder().setDateFormat(ISO_FORMAT_STRING_REQUEST).create();

  @Autowired private PackContainerService packContainerService;

  @Autowired private EventRegistrationService eventRegistrationService;

  @ManagedConfiguration SIBManagedConfig sibManagedConfig;

  @Timed(
      name = "sibDeliveryStatusListenerTimed",
      level1 = "uwms-receiving-api",
      level2 = "deliveryStatusListener")
  @ExceptionCounted(
      name = "sibDeliveryStatusListenerExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "deliveryStatusListener")
  @KafkaListener(
      topics = "${atlas.delivery.status.topic}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {

    if (!sibManagedConfig
        .getContainerEventListenerEnabledFacilities()
        .contains(TenantContext.getFacilityNum())) {
      LOGGER.info("Facility disabled so ignoring delivery status event: {}", message);
      return;
    }

    LOGGER.info("Received delivery status event: {}", message);
    DeliveryInfo deliveryInfo = gson.fromJson(message, DeliveryInfo.class);

    // Validate delivery status
    if (!validateFinaliseMessage(deliveryInfo)) {
      LOGGER.info(
          "Ignoring delivery message as it is not needed to process case creation. deliveryNumber={} deliveryStatus={}",
          deliveryInfo.getDeliveryNumber(),
          deliveryInfo.getDeliveryStatus());
      return;
    }

    // Create containers
    List<ContainerDTO> containers =
        packContainerService.createCaseContainers(deliveryInfo.getDeliveryNumber());

    eventRegistrationService.processEventOperation(containers, deliveryInfo);
  }

  // Remove delivery complete process flow as problem creation happening at the time of unloading
  // complete
  private boolean validateFinaliseMessage(DeliveryInfo deliveryInfo) {
    return Arrays.asList(DeliveryStatus.UNLOADING_COMPLETE.name())
        .contains(deliveryInfo.getDeliveryStatus());
  }
}
