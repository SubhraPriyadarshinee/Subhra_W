package com.walmart.move.nim.receiving.sib.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.messsage.listener.DeliveryUnloadCompleteEventListener;
import com.walmart.move.nim.receiving.sib.model.DeliveryUnloadCompleteEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.service.StoreDeliveryService;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.ArrayList;
import java.util.List;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryUnloadCompleteEventListenerTest {

  @InjectMocks private DeliveryUnloadCompleteEventListener deliveryUnloadCompleteEventListener;

  @Mock private StoreDeliveryService storeDeliveryService;

  @Mock private EventRepository eventRepository;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @AfterMethod
  public void resetMock() {
    Mockito.reset(storeDeliveryService);
    Mockito.reset(eventRepository);
  }

  @Test
  public void testOnApplicationEvent_EmptyEvents() {
    deliveryUnloadCompleteEventListener.onApplicationEvent(
        new DeliveryUnloadCompleteEvent(this, null));
    verify(eventRepository, never()).saveAll(any());
  }

  @Test
  public void testOnApplicationEvent() {

    deliveryUnloadCompleteEventListener.onApplicationEvent(
        new DeliveryUnloadCompleteEvent(this, getEvents()));
    ArgumentCaptor<List<Event>> eventArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventRepository, times(2)).saveAll(eventArgumentCaptor.capture());
    List<List<Event>> events = eventArgumentCaptor.getAllValues();
    assertEquals(events.size(), 2);
    events.forEach(
        processedEvents -> {
          Integer facilityNum = processedEvents.get(0).getFacilityNum();
          EventTargetStatus eventTargetStatus = processedEvents.get(0).getStatus();
          if (facilityNum.equals(5504)) {
            assertEquals(processedEvents.size(), 2);
          }
          if (facilityNum.equals(5505)) {
            assertEquals(processedEvents.size(), 1);
          }
          assertEquals(eventTargetStatus, EventTargetStatus.SUCCESSFUL);
        });
  }

  @Test
  public void testOnApplicationEvent_Error() {

    when(storeDeliveryService.unloadComplete(anyLong(), any(), any(), any()))
        .thenThrow(NullPointerException.class);
    deliveryUnloadCompleteEventListener.onApplicationEvent(
        new DeliveryUnloadCompleteEvent(this, getEvents()));
    ArgumentCaptor<List<Event>> eventArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(eventRepository, times(2)).saveAll(eventArgumentCaptor.capture());
    List<List<Event>> events = eventArgumentCaptor.getAllValues();
    assertEquals(events.size(), 2);
    events.forEach(
        processedEvents -> {
          Integer facilityNum = processedEvents.get(0).getFacilityNum();
          EventTargetStatus eventTargetStatus = processedEvents.get(0).getStatus();
          if (facilityNum.equals(5504)) {
            assertEquals(processedEvents.size(), 2);
          }
          if (facilityNum.equals(5505)) {
            assertEquals(processedEvents.size(), 1);
          }
          assertEquals(eventTargetStatus, EventTargetStatus.STALE);
        });
  }

  private List<Event> getEvents() {
    List<Event> events = new ArrayList<>();

    Event e1 = new Event();
    e1.setEventType(EventType.DELIVERY_UNLOAD_COMPLETE);
    e1.setDeliveryNumber(550400001l);
    e1.setFacilityCountryCode("US");
    e1.setFacilityNum(5504);
    e1.setStatus(EventTargetStatus.PENDING);

    Event e2 = new Event();
    e2.setEventType(EventType.DELIVERY_UNLOAD_COMPLETE);
    e2.setDeliveryNumber(550400002l);
    e2.setFacilityCountryCode("US");
    e2.setFacilityNum(5504);
    e2.setStatus(EventTargetStatus.PENDING);

    Event e3 = new Event();
    e3.setEventType(EventType.DELIVERY_UNLOAD_COMPLETE);
    e3.setDeliveryNumber(550500002l);
    e3.setFacilityCountryCode("US");
    e3.setFacilityNum(5505);
    e3.setStatus(EventTargetStatus.PENDING);

    events.add(e1);
    events.add(e2);
    events.add(e3);
    return events;
  }
}
