package com.walmart.move.nim.receiving.sib.transformer;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.EventDTO;
import java.util.List;
import java.util.stream.Collectors;

public class EventTransformer {

  public Event transform(EventDTO eventDTO) {
    Event event = new Event();
    event.setStatus(eventDTO.getStatus());
    event.setEventType(eventDTO.getEventType());
    event.setPickUpTime(eventDTO.getPickUpTime());
    event.setMetaData(eventDTO.getMetaData());
    event.setId(eventDTO.getId());
    event.setKey(eventDTO.getKey());
    event.setPayload(eventDTO.getPayload());
    event.setDeliveryNumber(eventDTO.getDeliveryNumber());
    event.setAdditionalInfo(eventDTO.getAdditionalInfo());
    return event;
  }

  public List<Event> transformList(List<EventDTO> eventDTOList) {
    return eventDTOList.stream().map(eventDTO -> transform(eventDTO)).collect(Collectors.toList());
  }

  public EventDTO reverseTransform(Event event) {

    EventDTO eventDTO = new EventDTO();
    eventDTO.setStatus(event.getStatus());
    eventDTO.setEventType(event.getEventType());
    eventDTO.setPickUpTime(event.getPickUpTime());
    eventDTO.setMetaData(event.getMetaData());
    eventDTO.setId(event.getId());
    eventDTO.setKey(event.getKey());
    eventDTO.setPayload(event.getPayload());
    eventDTO.setDeliveryNumber(event.getDeliveryNumber());
    eventDTO.setAdditionalInfo(event.getAdditionalInfo());
    eventDTO.setCreatedBy(event.getCreatedBy());
    eventDTO.setCreatedTime(event.getCreatedDate());

    return eventDTO;
  }

  public List<EventDTO> reverseTransformList(List<Event> events) {
    return events.stream().map(event -> reverseTransform(event)).collect(Collectors.toList());
  }
}
