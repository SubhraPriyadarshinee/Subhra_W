package com.walmart.move.nim.receiving.sib.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.EventDTO;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.transformer.EventTransformer;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

public class EventService {

  private EventRepository eventRepository;
  private EventTransformer eventTransformer;

  @Autowired
  public EventService(EventRepository eventRepository, EventTransformer eventTransformer) {
    this.eventRepository = eventRepository;
    this.eventTransformer = eventTransformer;
  }

  public List<EventDTO> getAllEventByDelivery(
      Long deliveryNumber, EventTargetStatus eventTargetStatus, EventType eventType) {

    if (Objects.nonNull(eventType) && Objects.nonNull(eventTargetStatus)) {
      return eventTransformer.reverseTransformList(
          eventRepository.findAllByDeliveryNumberAndEventTypeAndStatus(
              deliveryNumber, eventType, eventTargetStatus));
    }

    if (Objects.nonNull(eventTargetStatus) && Objects.isNull(eventType)) {
      return eventTransformer.reverseTransformList(
          eventRepository.findAllByDeliveryNumberAndStatus(deliveryNumber, eventTargetStatus));
    }

    if (Objects.isNull(eventTargetStatus) && Objects.nonNull(eventType)) {
      return eventTransformer.reverseTransformList(
          eventRepository.findAllByDeliveryNumberAndEventType(deliveryNumber, eventType));
    }
    return eventTransformer.reverseTransformList(
        eventRepository.findAllByDeliveryNumber(deliveryNumber));
  }

  public EventDTO updateEvent(Long id, EventDTO eventDTO) {
    Optional<Event> _event = eventRepository.findById(id);

    if (!_event.isPresent()) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.EVENT_NOT_FOUND, String.format("Event not found with id=%s", id));
    }

    Event event = _event.get();
    event.setStatus(eventDTO.getStatus());
    if (Objects.isNull(eventDTO.getPickUpTime())) {
      event.setPickUpTime(new Date());
    }
    return eventTransformer.reverseTransform(eventRepository.save(event));
  }
}
