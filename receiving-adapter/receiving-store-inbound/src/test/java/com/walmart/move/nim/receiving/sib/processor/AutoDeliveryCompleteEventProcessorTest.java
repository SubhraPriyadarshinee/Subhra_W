package com.walmart.move.nim.receiving.sib.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.Map;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AutoDeliveryCompleteEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private AutoDeliveryCompleteEventProcessor autoDeliveryCompleteEventProcessor;

  @Mock EventRepository eventRepository;

  private Gson gson;

  @BeforeClass
  public void init() {
    MockitoAnnotations.openMocks(this);
    gson = new Gson();
  }

  @AfterMethod
  public void resetMock() {
    Mockito.reset(eventRepository);
  }

  @Test
  public void testDoExecute_EmptyPayload() {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, 10);
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_DELIVERY_COMPLETE_EVENT);

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder().additionalAttributes(additionalAttribute).build();
    autoDeliveryCompleteEventProcessor.doExecute(receivingEvent);
    verify(eventRepository, never()).save(any());
  }

  @Test
  public void testDoExecute_InvalidActionType() {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, 10);
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_CORRECTION_EVENT);

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .additionalAttributes(additionalAttribute)
            .payload("5000001")
            .build();
    autoDeliveryCompleteEventProcessor.doExecute(receivingEvent);
    verify(eventRepository, never()).save(any());
  }

  @Test
  public void testDoExecuteHappyCase() {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, 10);
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_DELIVERY_COMPLETE_EVENT);

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .additionalAttributes(additionalAttribute)
            .payload("5000001")
            .build();
    autoDeliveryCompleteEventProcessor.doExecute(receivingEvent);
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository, times(1)).save(eventArgumentCaptor.capture());
    Event event = eventArgumentCaptor.getValue();
    assertNotNull(event);
    assertEquals(event.getEventType(), EventType.DELIVERY_AUTO_COMPLETE);
    assertEquals(event.getDeliveryNumber().longValue(), 5000001);
  }
}
