package com.walmart.move.nim.receiving.sib.controller;

import com.walmart.move.nim.receiving.sib.model.EventDTO;
import com.walmart.move.nim.receiving.sib.service.EventService;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.bind.annotation.*;

@ConditionalOnExpression("${enable.sib.app:false}")
@RestController
@RequestMapping("/store/events")
public class EventController {

  private EventService eventService;

  @Autowired
  public EventController(EventService eventService) {
    this.eventService = eventService;
  }

  @GetMapping("/{deliveryNumber}")
  public List<EventDTO> getAllEvent(
      @PathVariable("deliveryNumber") Long deliveryNumber,
      @RequestParam(value = "status", required = false) EventTargetStatus eventTargetStatus,
      @RequestParam(value = "eventType", required = false) EventType eventType) {
    return eventService.getAllEventByDelivery(deliveryNumber, eventTargetStatus, eventType);
  }

  @PutMapping("/{id}")
  public EventDTO updateEvent(@PathVariable("id") Long id, @RequestBody EventDTO eventDTO) {
    return eventService.updateEvent(id, eventDTO);
  }
}
