package com.walmart.move.nim.receiving.sib.service;

import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.utils.CoreUtil;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.Constants;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;

public class ManualFinalizationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(ManualFinalizationService.class);

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private EventRepository eventRepository;

  @Autowired private StoreDeliveryService storeDeliveryService;

  @Autowired private ProcessInitiator processInitiator;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  public void manualFinalize(
      long deliveryNumber, String doorNumber, boolean isUnloadEnabled, HttpHeaders headers) {
    LOGGER.info("Manual finalization got triggered for deliveryNumber = {} ", deliveryNumber);

    Event event =
        eventRepository.findByDeliveryNumberAndEventType(
            deliveryNumber, EventType.MANUAL_FINALIZATION);

    if (Objects.nonNull(event)) {
      LOGGER.info("Manual Finalization event already exists for deliveryNumber={}", deliveryNumber);
      throw new ReceivingInternalException(
          ExceptionCodes.DUPLICATE_MANUAL_FINALIZATION,
          String.format("Duplicate Manual Finalization for delivery=%s", deliveryNumber));
    }

    if (isUnloadEnabled) {
      storeDeliveryService.unloadComplete(
          deliveryNumber, doorNumber, ReceivingConstants.MANUAL_FINALISE_DELIVERY, headers);
    }

    event = createEventObject(deliveryNumber);

    boolean isNGRFinalizationEnabled =
        tenantSpecificConfigReader.getConfiguredFeatureFlag(
            TenantContext.getFacilityNum().toString(),
            Constants.ENABLE_NGR_FINALIZATION,
            Boolean.FALSE);

    LOGGER.info("Checking if NGR finalization is enabled - {}", isNGRFinalizationEnabled);
    if (isNGRFinalizationEnabled) {
      submitDeliveryUpdateForFinalization(deliveryNumber, event);
    }

    eventRepository.save(event);
    LOGGER.info(
        "Successfully persisted Manual Finalization for deliveryNumber={}",
        event.getDeliveryNumber());
  }

  private void submitDeliveryUpdateForFinalization(long deliveryNumber, Event event) {
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(ReceivingUtils.getHeaders());
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setDeliveryNumber(String.valueOf(deliveryNumber));
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(deliveryUpdateMessage))
            .name(ReceivingConstants.MANUAL_FINALIZATION_PROCESSOR)
            .additionalAttributes(forwardableHeaders)
            .processor(ReceivingConstants.MANUAL_FINALIZATION_PROCESSOR)
            .build();
    LOGGER.info("Going to initiate manual finalization processor for {}", deliveryNumber);
    processInitiator.initiateProcess(receivingEvent, forwardableHeaders);
    event.setStatus(EventTargetStatus.SUCCESSFUL);
  }

  private Event createEventObject(Long deliveryNumber) {

    // Manual Finalization will get registered post X min of button click.
    Date updatedPickupTime =
        CoreUtil.addMinutesToJavaUtilDate(new Date(), sibManagedConfig.getManulFinalizationDelay());

    Event containerEvent = new Event();
    containerEvent.setKey(String.valueOf(deliveryNumber));
    containerEvent.setDeliveryNumber(deliveryNumber);
    containerEvent.setEventType(EventType.MANUAL_FINALIZATION);
    containerEvent.setPayload(EventType.MANUAL_FINALIZATION.name());
    containerEvent.setRetryCount(0);
    containerEvent.setStatus(EventTargetStatus.PENDING);
    containerEvent.setPickUpTime(updatedPickupTime);
    containerEvent.setFacilityNum(TenantContext.getFacilityNum());
    containerEvent.setFacilityCountryCode(TenantContext.getFacilityCountryCode());
    containerEvent.setMetaData(new HashMap<>());
    containerEvent.setAdditionalInfo(new HashMap<>());
    return containerEvent;
  }
}
