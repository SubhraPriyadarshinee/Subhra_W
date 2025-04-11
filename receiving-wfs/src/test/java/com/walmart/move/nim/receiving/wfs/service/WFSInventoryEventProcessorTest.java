package com.walmart.move.nim.receiving.wfs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockInventoryAdjustmentEvent;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class WFSInventoryEventProcessorTest extends ReceivingTestBase {

  @InjectMocks private WFSInventoryEventProcessor wfsInventoryEventProcessor;

  @Spy private InventoryAdjustmentTO messageData;

  //  @Spy private ContainerService containerService;
  @Spy private WFSContainerService containerService;

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
  public void testWFSInventoryAdjustmentWithValidVTREvent() throws ReceivingException {
    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.VALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());
    doNothing().when(containerService).backoutContainerForWFS(anyString(), any(HttpHeaders.class));

    wfsInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(1)).backoutContainerForWFS(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testWFSInventoryAdjustmentWithInvalidVTREvent() throws ReceivingException {

    when(messageData.getJsonObject())
        .thenReturn(parser.parse(MockInventoryAdjustmentEvent.INVALID_VTR_EVENT).getAsJsonObject());
    when(messageData.getHttpHeaders()).thenReturn(MockHttpHeaders.getHeaders());

    wfsInventoryEventProcessor.processEvent(messageData);

    verify(containerService, times(0)).backoutContainerForWFS(anyString(), any(HttpHeaders.class));
  }
}
