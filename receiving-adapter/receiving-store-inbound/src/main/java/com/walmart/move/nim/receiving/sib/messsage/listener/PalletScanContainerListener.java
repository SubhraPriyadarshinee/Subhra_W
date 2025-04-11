package com.walmart.move.nim.receiving.sib.messsage.listener;

import static com.walmart.move.nim.receiving.sib.utils.Constants.ISO_FORMAT_STRING_REQUEST;
import static com.walmart.move.nim.receiving.sib.utils.Constants.PALLET;
import static com.walmart.move.nim.receiving.sib.utils.Constants.PALLET_TYPE;
import static com.walmart.move.nim.receiving.sib.utils.Constants.STORE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PROBLEM_NA;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.walmart.atlas.global.config.annotation.EnableInPrimaryRegionNode;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.service.EventRegistrationService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.configuration.annotation.ManagedConfiguration;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.Payload;

public class PalletScanContainerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(PalletScanContainerListener.class);
  private Gson gsonIn = new GsonBuilder().setDateFormat(ISO_FORMAT_STRING_REQUEST).create();

  @Autowired private EventRegistrationService eventRegistrationService;

  @ManagedConfiguration SIBManagedConfig sibManagedConfig;

  @Timed(
      name = "sibPalletScanListenerTimed",
      level1 = "uwms-receiving-api",
      level2 = "palletScanContainerListener")
  @ExceptionCounted(
      name = "sibPalletScanListenerExceptionCount",
      level1 = "uwms-receiving-api",
      level2 = "palletScanContainerListener")
  @KafkaListener(
      topics = "${container.receiving.receipt}",
      errorHandler = "kafkaErrorHandler",
      containerFactory = ATLAS_SECURED_KAFKA_LISTENER_CONTAINER_FACTORY)
  public void listen(@Payload String message, @Headers Map<String, byte[]> kafkaHeaders) {

    if (!sibManagedConfig
        .getContainerEventListenerEnabledFacilities()
        .contains(TenantContext.getFacilityNum())) {
      LOGGER.info("Facility disabled so ignoring pallet scan event: {}", message);
      return;
    }

    LOGGER.info("[MultiContainerListener] list of Containers text message received : {}", message);
    List<ContainerDTO> containers =
        gsonIn.fromJson(message, new TypeToken<List<ContainerDTO>>() {}.getType());
    List<ContainerDTO> listPalletContainerDTO =
        containers
            .stream()
            // remove shortage containers
            .filter(containerDTO -> !PROBLEM_NA.equals(containerDTO.getContainerStatus()))
            // process only store pallets
            .filter(
                containerDTO ->
                    PALLET.equals(containerDTO.getContainerType())
                        && STORE.equals(
                            String.valueOf(containerDTO.getContainerMiscInfo().get(PALLET_TYPE))))
            .collect(Collectors.toList());

    eventRegistrationService.processEventOperation(listPalletContainerDTO, null);
  }
}
