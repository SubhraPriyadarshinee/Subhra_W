package com.walmart.move.nim.receiving.sib.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.sib.config.SIBManagedConfig;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.model.CleanupEvent;
import com.walmart.move.nim.receiving.sib.model.DeliveryAutoCompleteEvent;
import com.walmart.move.nim.receiving.sib.model.ManualFinalizationEvents;
import com.walmart.move.nim.receiving.sib.model.StoreAutoInitializationEvents;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EventProcessingServiceTest extends ReceivingTestBase {

  @InjectMocks private EventProcessingService eventProcessingService;

  @Mock private SIBManagedConfig sibManagedConfig;

  @Mock private ApplicationEventPublisher applicationEventPublisher;

  @Mock private EventRepository eventRepository;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMock() {
    reset(eventRepository);
    reset(applicationEventPublisher);
  }

  @Test
  public void testProcessEvents_NoPendingEvents() {
    when(sibManagedConfig.getPublishEventMaxDbFetchSize()).thenReturn(50);
    when(eventRepository.findAllByStatusAndPickUpTimeLessThanEqualAndEventTypeInOrderByID(
            any(), any(), any(), any()))
        .thenReturn(Collections.emptyList());
    eventProcessingService.processEvents();
    verify(applicationEventPublisher, never()).publishEvent(any());
  }

  @Test
  public void testProcessEvents() {
    when(sibManagedConfig.getPublishEventMaxDbFetchSize()).thenReturn(50);
    when(eventRepository.findAllByStatusAndPickUpTimeLessThanEqualAndEventTypeInOrderByID(
            any(), any(), any(), any()))
        .thenReturn(getEvents());
    ArgumentCaptor<List> eventsCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<ApplicationEvent> applicationEventArgumentCaptor =
        ArgumentCaptor.forClass(ApplicationEvent.class);

    eventProcessingService.processEvents();

    verify(applicationEventPublisher, atLeast(4))
        .publishEvent(applicationEventArgumentCaptor.capture());
    verify(eventRepository, times(1)).saveAll(eventsCaptor.capture());

    List<ApplicationEvent> applicationEvents = applicationEventArgumentCaptor.getAllValues();
    assertEquals(applicationEvents.size(), 5);
    applicationEvents.forEach(
        applicationEvent -> {
          if (applicationEvent instanceof CleanupEvent) {
            CleanupEvent cleanupEvent = (CleanupEvent) applicationEvent;
            assertEquals(cleanupEvent.getEvents().size(), 1);
            assertEquals(cleanupEvent.getEvents().get(0).getEventType(), EventType.CLEANUP);
          }
          if (applicationEvent instanceof DeliveryAutoCompleteEvent) {
            DeliveryAutoCompleteEvent deliveryAutoCompleteEvent =
                (DeliveryAutoCompleteEvent) applicationEvent;
            assertEquals(deliveryAutoCompleteEvent.getEvents().size(), 1);
            assertEquals(
                deliveryAutoCompleteEvent.getEvents().get(0).getEventType(),
                EventType.DELIVERY_AUTO_COMPLETE);
          }
          if (applicationEvent instanceof StoreAutoInitializationEvents) {
            StoreAutoInitializationEvents storeAutoInitializationEvents =
                (StoreAutoInitializationEvents) applicationEvent;
            assertEquals(storeAutoInitializationEvents.getEvents().size(), 0);
          }
          if (applicationEvent instanceof ManualFinalizationEvents) {
            ManualFinalizationEvents manualFinalizationEvents =
                (ManualFinalizationEvents) applicationEvent;
            assertEquals(manualFinalizationEvents.getEvents().size(), 0);
          }
        });
    List<Event> events = eventsCaptor.getValue();
    assertNotNull(events);
    Set<EventTargetStatus> status =
        events.stream().map(Event::getStatus).collect(Collectors.toSet());
    assertEquals(status.size(), 1);
    assertTrue(status.contains(EventTargetStatus.IN_PROGRESS));
  }

  private List<Event> getEvents() {
    List<Event> events = new ArrayList<>();
    Event cleanUpEvent = new Event();
    cleanUpEvent.setEventType(EventType.CLEANUP);
    cleanUpEvent.setStatus(EventTargetStatus.PENDING);

    Event autoClosureEvent = new Event();
    autoClosureEvent.setEventType(EventType.DELIVERY_AUTO_COMPLETE);
    autoClosureEvent.setStatus(EventTargetStatus.PENDING);

    events.add(autoClosureEvent);
    events.add(cleanUpEvent);
    return events;
  }
}
