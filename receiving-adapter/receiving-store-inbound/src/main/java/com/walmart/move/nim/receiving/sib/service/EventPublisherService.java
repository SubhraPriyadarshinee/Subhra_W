package com.walmart.move.nim.receiving.sib.service;

import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.ContainerApplicationEvents;
import com.walmart.move.nim.receiving.sib.model.ContainerStockedEvents;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class EventPublisherService {

  @Autowired private EventRepository eventRepository;

  @ManagedConfiguration private SIBManagedConfig sibManagedConfig;

  @Autowired private ApplicationEventPublisher applicationEventPublisher;

  private static final Logger LOGGER = LoggerFactory.getLogger(EventPublisherService.class);

  public void publishEvents() {
    LOGGER.info("Fetching events to be published from database");
    List<EventType> publishableEvents =
        Arrays.stream(EventType.values())
            .filter(EventType::isPublishable)
            .collect(Collectors.toList());
    List<Event> events =
        eventRepository.findAllByStatusAndPickUpTimeLessThanEqualAndEventTypeInOrderByID(
            EventTargetStatus.PENDING.name(),
            Date.from(Instant.now()),
            publishableEvents,
            sibManagedConfig.getPublishEventMaxDbFetchSize());

    if (CollectionUtils.isNotEmpty(events)) {
      LOGGER.info("Updating events to IN_PROGRESS");
      events.forEach(event -> event.setStatus(EventTargetStatus.IN_PROGRESS));
      eventRepository.saveAll(events);
      LOGGER.info("Updated event status to IN_PROGRESS");

      // Accumulate Atlas Events except stocked and manual finalization event
      List<Event> atlasEvents =
          events
              .stream()
              .filter(event -> !EventType.STOCKED.equals(event.getEventType()))
              .collect(Collectors.toList());
      // Accumulate EI Events
      List<Event> eiEvents =
          events
              .stream()
              .filter(event -> EventType.STOCKED.equals(event.getEventType()))
              .collect(Collectors.toList());

      applicationEventPublisher.publishEvent(new ContainerApplicationEvents(this, atlasEvents));
      applicationEventPublisher.publishEvent(new ContainerStockedEvents(this, eiEvents));
      LOGGER.info("Published Spring events for publishing to handler");
    }
  }
}
