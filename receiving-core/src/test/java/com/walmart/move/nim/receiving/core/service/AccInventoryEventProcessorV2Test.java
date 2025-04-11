package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.service.v2.AccInventoryEventProcessorV2;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class AccInventoryEventProcessorV2Test extends ReceivingTestBase {

  @InjectMocks private AccInventoryEventProcessorV2 inventoryEventProcessor;

  @Spy private InventoryAdjustmentTO messageData;

  @Spy private ContainerService containerService;

  @Captor ArgumentCaptor<String> updatedContainerTrackingId;

  @Captor ArgumentCaptor<Integer> adjustQty;

  @Captor ArgumentCaptor<String> newContainerTrackingId;

  private JsonParser parser = new JsonParser();

  @BeforeClass
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void cleanup() {
    reset(messageData);
    reset(containerService);
  }

  @Test
  public void testInventoryAdjustmentWithDamageEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.VALID_DAMAGE_EVENT_V2).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processDamageAdjustment(anyString(), Mockito.anyInt(), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1))
        .processDamageAdjustment(anyString(), any(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithVdgEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.VALID_VDM_EVENT_V2).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0))
        .processVendorDamageAdjustment(anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithConcealedShortageOrOverageEvent()
      throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser
                .parse(MockInventoryAdjustmentEvent.VALID_CONCEALED_SHORTAGE_OVERAGE_EVENT_V2)
                .getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing()
        .when(containerService)
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0))
        .processConcealedShortageOrOverageAdjustment(
            anyString(), any(JsonObject.class), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithValidEvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT_V2).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing().when(containerService).backoutContainer(anyString(), any(HttpHeaders.class));

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0)).backoutContainer(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testInventoryAdjustmentWithInvalidEvent() throws ReceivingException {

    when(messageData.getJsonObject())
        .thenReturn(
            parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT_V2).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());

    inventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0)).backoutContainer(anyString(), any(HttpHeaders.class));
  }
}
