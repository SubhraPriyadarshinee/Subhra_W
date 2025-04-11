package com.walmart.move.nim.receiving.acc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.walmart.move.nim.receiving.acc.constants.ACCConstants;
import com.walmart.move.nim.receiving.acc.model.RoboDepalEventMessage;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.docktag.ReceiveDockTagRequest;
import com.walmart.move.nim.receiving.core.service.DockTagService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RoboDepalEventProcessorTest {
  @InjectMocks private RoboDepalEventProcessor depalEventProcessor;
  @Mock private RoboDepalEventService roboDepalEventService;
  @Mock private DockTagService dockTagService;
  @Mock private AppConfig appConfig;

  private RoboDepalEventMessage depalPayload;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32888);
    TenantContext.setFacilityCountryCode("US");

    depalPayload = new RoboDepalEventMessage();
    depalPayload.setEquipmentName("CP04FL_C");
    depalPayload.setTrackingId("e328880000000000000000001");
    depalPayload.setSourceLocationId("CP04FL_C-DEPAL-1");
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig);
    reset(roboDepalEventService);
    reset(dockTagService);
  }

  @BeforeMethod
  public void beforeMethod() {
    when(appConfig.getRoboDepalUserId()).thenReturn("Automation");
  }

  @Test
  public void testProcessEvent_DepalAck() {
    depalPayload.setMessageType(ACCConstants.ROBO_DEPAL_ACK_EVENT);
    depalEventProcessor.processEvent(depalPayload);
    verify(roboDepalEventService, times(1))
        .processDepalAckEvent(any(ReceiveDockTagRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testProcessEvent_DepalFinish() {
    depalPayload.setMessageType(ACCConstants.ROBO_DEPAL_FINISH_EVENT);
    depalEventProcessor.processEvent(depalPayload);
    verify(roboDepalEventService, times(1))
        .processDepalFinishEvent(any(ReceiveDockTagRequest.class), any(HttpHeaders.class));
  }

  @Test
  public void testProcessEvent_InvalidEvent() {
    depalPayload.setMessageType("invalid event");
    depalEventProcessor.processEvent(depalPayload);
    verify(roboDepalEventService, times(0))
        .processDepalAckEvent(any(ReceiveDockTagRequest.class), any(HttpHeaders.class));
    verify(roboDepalEventService, times(0))
        .processDepalFinishEvent(any(ReceiveDockTagRequest.class), any(HttpHeaders.class));
  }
}
