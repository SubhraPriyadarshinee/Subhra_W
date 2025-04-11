package com.walmart.move.nim.receiving.sib.processor;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.sib.entity.Event;
import com.walmart.move.nim.receiving.sib.repositories.EventRepository;
import com.walmart.move.nim.receiving.sib.transformer.ContainerDataTransformer;
import com.walmart.move.nim.receiving.sib.utils.EventType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class CorrectionContainerEventProcessorTest extends ReceivingTestBase {

  String OPERATION_TYPE = "operationType";
  String PALLET_TYPE = "palletType";
  @InjectMocks CorrectionContainerEventProcessor correctionContainerEventProcessor;
  @Mock EventRepository eventRepo;
  @Mock ContainerDataTransformer containerDataTransformer;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setCorrelationId(UUID.randomUUID().toString());
    TenantContext.setFacilityNum(12345);
    TenantContext.setFacilityCountryCode("US");
  }

  @AfterMethod
  public void resetMocks() {
    Mockito.reset(eventRepo);
  }

  @Test
  public void testCreateCorrectionEventWithThresholdPickupTime() {
    Map<String, Object> additionalAttribute = new HashMap<>();
    additionalAttribute.put(EVENT_RUN_AFTER_THRESHOLD_TIME_MINUTES, new Integer(20));
    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.CREATE_CORRECTION_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(getContainerDTO()))
            .processor(CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    when(eventRepo.save(any())).thenReturn(new Event());
    correctionContainerEventProcessor.doExecute(receivingEvent);
    verify(eventRepo, times(1)).save(any());
  }

  @Test
  public void testUpdateCorrectionEventAsInvalid() {
    Map<String, Object> additionalAttribute = new HashMap<>();

    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.UPDATE_CORRECTION_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(getContainerDTO()))
            .processor(CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    when(eventRepo.save(any())).thenReturn(new Event());
    Mockito.when(eventRepo.findByKeyAndStatusAndEventType(anyString(), any(), any()))
        .thenReturn(getEvent(EventTargetStatus.PENDING));
    correctionContainerEventProcessor.doExecute(receivingEvent);
    verify(eventRepo, times(1)).save(any());
  }

  @Test
  public void testUpdateCorrectionEventAsInvalidIfApplicable() {
    Map<String, Object> additionalAttribute = new HashMap<>();

    additionalAttribute.put(
        ReceivingConstants.ACTION_TYPE, ReceivingConstants.UPDATE_CORRECTION_EVENT);
    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(JacksonParser.writeValueAsString(getContainerDTO()))
            .processor(CORRECTION_CONTAINER_EVENT_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .build();
    Mockito.when(eventRepo.findByKeyAndStatusAndEventType(anyString(), any(), any()))
        .thenReturn(null);
    correctionContainerEventProcessor.doExecute(receivingEvent);
    verify(eventRepo, times(1)).findByKeyAndStatusAndEventType(any(), any(), any());
  }

  private ContainerDTO getContainerDTO() {
    Map<String, Object> miscInfo = new HashMap<>();
    miscInfo.put(OPERATION_TYPE, "Normal");
    miscInfo.put(PALLET_TYPE, "MFC");
    return ContainerDTO.builder()
        .trackingId("9876543210")
        .deliveryNumber(123456789L)
        .ssccNumber("dummySSCC")
        .containerMiscInfo(miscInfo)
        .containerItems(new ArrayList<>())
        .shipmentId("1234567890")
        .build();
  }

  private Event getEvent(EventTargetStatus event) {
    Event e = new Event();
    e.setEventType(EventType.CORRECTION);
    e.setStatus(event);
    return e;
  }
}
