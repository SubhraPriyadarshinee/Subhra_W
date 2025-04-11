package com.walmart.move.nim.receiving.sib.service;

import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.*;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class EventProcessingService {

  @Autowired private EventRepository eventRepository;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private ApplicationEventPublisher applicationEventPublisher;

  private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisherService.class);

  public void processEvents() {
    LOGGER.info("Fetching events to be processed from database");
    List<EventType> processableEvents =
        Arrays.stream(EventType.values())
            .filter(EventType::isProcessable)
            .collect(Collectors.toList());
    List<Event> events =
        eventRepository.findAllByStatusAndPickUpTimeLessThanEqualAndEventTypeInOrderByID(
            EventTargetStatus.PENDING.name(),
            Date.from(Instant.now()),
            processableEvents,
            sibManagedConfig.getPublishEventMaxDbFetchSize());

    if (CollectionUtils.isNotEmpty(events)) {
      LOGGER.info("Updating events to IN_PROGRESS");
      events.forEach(event -> event.setStatus(EventTargetStatus.IN_PROGRESS));
      eventRepository.saveAll(events);
      LOGGER.info("Updated event status to IN_PROGRESS");

      Map<EventType, List<Event>> groupedEventTypes =
          events.stream().collect(Collectors.groupingBy(Event::getEventType));

      List<Event> cleanupEvents =
          groupedEventTypes.getOrDefault(EventType.CLEANUP, Collections.emptyList());

      List<Event> manualFinalizationEvents =
          groupedEventTypes.getOrDefault(EventType.MANUAL_FINALIZATION, Collections.emptyList());

      List<Event> storeAutoInitializeEvents =
          groupedEventTypes.getOrDefault(
              EventType.STORE_AUTO_INITIALIZATION, Collections.emptyList());

      List<Event> autoDeliveryCompleteEvents =
          groupedEventTypes.getOrDefault(EventType.DELIVERY_AUTO_COMPLETE, Collections.emptyList());

      List<Event> deliveryUnloadCompleteEvents =
          groupedEventTypes.getOrDefault(
              EventType.DELIVERY_UNLOAD_COMPLETE, Collections.emptyList());

      applicationEventPublisher.publishEvent(new CleanupEvent(this, cleanupEvents));
      applicationEventPublisher.publishEvent(
          new ManualFinalizationEvents(this, manualFinalizationEvents));
      applicationEventPublisher.publishEvent(
          new StoreAutoInitializationEvents(this, storeAutoInitializeEvents));
      applicationEventPublisher.publishEvent(
          new DeliveryAutoCompleteEvent(this, autoDeliveryCompleteEvents));
      applicationEventPublisher.publishEvent(
          new DeliveryUnloadCompleteEvent(this, deliveryUnloadCompleteEvents));
      LOGGER.info("Published Spring events for processing to handlers");
    }
  }
}
