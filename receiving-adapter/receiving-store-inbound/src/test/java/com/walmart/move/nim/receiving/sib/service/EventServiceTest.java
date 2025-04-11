package com.walmart.move.nim.receiving.sib.service;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.EventDTO;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.transformer.EventTransformer;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.*;
import org.mockito.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EventServiceTest {

  @InjectMocks private EventService eventService;

  @Spy private EventTransformer eventTransformer;

  @Mock private EventRepository eventRepository;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testFindAllByDeliveryNumberAndEventTypeAndStatus() {
    List<Event> eventList = new ArrayList<>();
    Event event = createEvent(1234321L);
    eventList.add(event);

    when(eventRepository.findAllByDeliveryNumberAndEventTypeAndStatus(anyLong(), any(), any()))
        .thenReturn(eventList);

    eventService.getAllEventByDelivery(
        1234321L, EventTargetStatus.SUCCESSFUL, EventType.MANUAL_FINALIZATION);

    verify(eventRepository, times(1))
        .findAllByDeliveryNumberAndEventTypeAndStatus(anyLong(), any(), any());

    List<EventDTO> response = eventTransformer.reverseTransformList(eventList);

    assertEquals(Long.valueOf("1234321"), response.get(0).getDeliveryNumber());
    assertTrue(Objects.nonNull(response.get(0).getCreatedTime()));
    assertEquals("UT-System", response.get(0).getCreatedBy());
    assertEquals(EventTargetStatus.SUCCESSFUL, response.get(0).getStatus());
    assertEquals(EventType.MANUAL_FINALIZATION, response.get(0).getEventType());
  }

  @Test
  public void testFindAllByDeliveryNumberAndStatus() {
    List<Event> eventList = new ArrayList<>();
    Event event = createEvent(1871321L);
    eventList.add(event);

    when(eventRepository.findAllByDeliveryNumberAndStatus(anyLong(), any())).thenReturn(eventList);

    eventService.getAllEventByDelivery(1871321L, EventTargetStatus.SUCCESSFUL, null);

    verify(eventRepository, times(1)).findAllByDeliveryNumberAndStatus(anyLong(), any());

    List<EventDTO> response = eventTransformer.reverseTransformList(eventList);

    assertEquals(Long.valueOf("1871321"), response.get(0).getDeliveryNumber());
    assertTrue(Objects.nonNull(response.get(0).getCreatedTime()));
    assertEquals("UT-System", response.get(0).getCreatedBy());
    assertEquals(EventTargetStatus.SUCCESSFUL, response.get(0).getStatus());
    assertEquals(EventType.MANUAL_FINALIZATION, response.get(0).getEventType());
  }

  @Test
  public void testFindAllByDeliveryNumberAndEventType() {
    List<Event> eventList = new ArrayList<>();
    Event event = createEvent(1232381L);
    eventList.add(event);

    when(eventRepository.findAllByDeliveryNumberAndEventType(anyLong(), any()))
        .thenReturn(eventList);

    eventService.getAllEventByDelivery(1232381L, null, EventType.MANUAL_FINALIZATION);

    verify(eventRepository, times(0))
        .findAllByDeliveryNumberAndEventTypeAndStatus(anyLong(), any(), any());
    verify(eventRepository, times(0)).findAllByDeliveryNumberAndStatus(anyLong(), any());
    verify(eventRepository, times(1)).findAllByDeliveryNumberAndEventType(anyLong(), any());

    List<EventDTO> response = eventTransformer.reverseTransformList(eventList);

    assertEquals(Long.valueOf("1232381"), response.get(0).getDeliveryNumber());
    assertTrue(Objects.nonNull(response.get(0).getCreatedTime()));
    assertEquals("UT-System", response.get(0).getCreatedBy());
    assertEquals(EventTargetStatus.SUCCESSFUL, response.get(0).getStatus());
    assertEquals(EventType.MANUAL_FINALIZATION, response.get(0).getEventType());
  }

  @Test
  public void testFindAllByDeliveryNumber() {
    List<Event> eventList = new ArrayList<>();
    Event event = createEvent(1234435L);
    eventList.add(event);

    when(eventRepository.findAllByDeliveryNumber(anyLong())).thenReturn(eventList);

    eventService.getAllEventByDelivery(1234435L, null, null);

    verify(eventRepository, times(0))
        .findAllByDeliveryNumberAndEventTypeAndStatus(anyLong(), any(), any());
    verify(eventRepository, times(0)).findAllByDeliveryNumberAndStatus(anyLong(), any());
    verify(eventRepository, times(0)).findAllByDeliveryNumberAndEventType(anyLong(), any());
    verify(eventRepository, times(1)).findAllByDeliveryNumber(anyLong());

    List<EventDTO> response = eventTransformer.reverseTransformList(eventList);

    assertEquals(Long.valueOf("1234435"), response.get(0).getDeliveryNumber());
    assertTrue(Objects.nonNull(response.get(0).getCreatedTime()));
    assertEquals("UT-System", response.get(0).getCreatedBy());
    assertEquals(EventTargetStatus.SUCCESSFUL, response.get(0).getStatus());
    assertEquals(EventType.MANUAL_FINALIZATION, response.get(0).getEventType());
  }

  private Event createEvent(Long deliveryNumber) {
    Event event = new Event();
    event.setEventType(EventType.MANUAL_FINALIZATION);
    event.setId(1234567890L);
    event.setKey("Key-123");
    event.setStatus(EventTargetStatus.SUCCESSFUL);
    event.setDeliveryNumber(deliveryNumber);
    event.setCreatedBy("UT-System");
    event.setCreatedDate(new Date());
    event.setFacilityCountryCode("US");
    event.setFacilityNum(5504);

    return event;
  }
}
