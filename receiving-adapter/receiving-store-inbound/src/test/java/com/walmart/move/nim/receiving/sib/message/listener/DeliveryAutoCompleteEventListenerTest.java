package com.walmart.move.nim.receiving.sib.message.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.messsage.listener.DeliveryAutoCompleteEventListener;
import com.walmart.move.nim.receiving.sib.model.DeliveryAutoCompleteEvent;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import java.util.ArrayList;
import java.util.List;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DeliveryAutoCompleteEventListenerTest extends ReceivingTestBase {

  @InjectMocks private DeliveryAutoCompleteEventListener deliveryAutoCompleteEventListener;

  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Mock private EventRepository eventRepository;

  @Mock private DefaultCompleteDeliveryProcessor defaultCompleteDeliveryProcessor;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
  }

  @AfterMethod
  public void resetMock() {
    Mockito.reset(tenantSpecificConfigReader);
    Mockito.reset(eventRepository);
  }

  @Test
  public void testOnApplicationEvent_EmptyEvents() {
    deliveryAutoCompleteEventListener.onApplicationEvent(new DeliveryAutoCompleteEvent(this, null));
    verify(eventRepository, never()).saveAll(any());
  }

  @Test
  public void testOnApplicationEvent() throws ReceivingException {

    when(tenantSpecificConfigReader.getConfiguredInstance(any(), any(), any()))
        .thenReturn(defaultCompleteDeliveryProcessor);
    when(defaultCompleteDeliveryProcessor.completeDelivery(any(), anyBoolean(), any()))
        .thenReturn(new DeliveryInfo());
    deliveryAutoCompleteEventListener.onApplicationEvent(
        new DeliveryAutoCompleteEvent(this, getEvents()));
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

  private List<Event> getEvents() {
    List<Event> events = new ArrayList<>();

    Event e1 = new Event();
    e1.setEventType(EventType.DELIVERY_AUTO_COMPLETE);
    e1.setDeliveryNumber(550400001l);
    e1.setFacilityCountryCode("US");
    e1.setFacilityNum(5504);
    e1.setStatus(EventTargetStatus.PENDING);

    Event e2 = new Event();
    e2.setEventType(EventType.DELIVERY_AUTO_COMPLETE);
    e2.setDeliveryNumber(550400002l);
    e2.setFacilityCountryCode("US");
    e2.setFacilityNum(5504);
    e2.setStatus(EventTargetStatus.PENDING);

    Event e3 = new Event();
    e3.setEventType(EventType.DELIVERY_AUTO_COMPLETE);
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
