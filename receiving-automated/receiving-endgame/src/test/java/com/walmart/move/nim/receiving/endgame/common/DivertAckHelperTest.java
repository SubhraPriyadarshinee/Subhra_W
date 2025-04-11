package com.walmart.move.nim.receiving.endgame.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.REPLEN_CASE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.service.EndGameDivertAckEventProcessor;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DivertAckHelperTest extends ReceivingTestBase {
  private Gson gson = new Gson();
  @InjectMocks private DivertAckHelper divertAckHelper;
  @Mock private EndGameDivertAckEventProcessor endGameDivertAckEventProcessor;
  @Mock private TenantSpecificConfigReader configUtils;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void setRootUp() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(divertAckHelper, "gson", gson);
    TenantContext.setFacilityCountryCode("us");
    TenantContext.setFacilityNum(9610);
  }

  @AfterMethod
  public void resetMocks() {
    reset(endGameDivertAckEventProcessor);
    reset(configUtils);
  }

  @Test
  public void testDoProcess() {
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    String message = gson.toJson(scanEventData);

    doNothing().when(endGameDivertAckEventProcessor).processEvent(any(MessageData.class));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    verify(endGameDivertAckEventProcessor, times(1)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testDoProcessWithEmptyPONumbersList() {
    ScanEventData scanEventData = MockMessageData.getMockScanEventDataWithEmptyPONumbersList();
    String message = gson.toJson(scanEventData);

    doNothing().when(endGameDivertAckEventProcessor).processEvent(any(MessageData.class));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    verify(endGameDivertAckEventProcessor, times(1)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testDoProcessWithOutPONumbersField() {
    ScanEventData scanEventData = MockMessageData.getMockScanEventDataWithOutPONumbersField();
    String message = gson.toJson(scanEventData);

    doNothing().when(endGameDivertAckEventProcessor).processEvent(any(MessageData.class));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    verify(endGameDivertAckEventProcessor, times(1)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test(
      expectedExceptions = ReceivingInternalException.class,
      expectedExceptionsMessageRegExp = "Unable to process scan event.*")
  public void testProcessException() {
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    String message = gson.toJson(scanEventData);
    doThrow(
            new ReceivingInternalException(
                ExceptionCodes.UNABLE_TO_CREATE_CONTAINER,
                String.format(
                    EndgameConstants.UNABLE_TO_CREATE_CONTAINER_ERROR_MSG,
                    scanEventData.getTrailerCaseLabel())))
        .when(endGameDivertAckEventProcessor)
        .processEvent(any(MessageData.class));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    verify(endGameDivertAckEventProcessor, times(1)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testDoProcess_EmptyMessage() {
    String message = null;
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    doNothing().when(endGameDivertAckEventProcessor).processEvent(any(MessageData.class));
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    verify(endGameDivertAckEventProcessor, times(0)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }

  @Test
  public void testDoProcess_ForReplenEvent() {
    ScanEventData scanEventData = MockMessageData.getMockScanEventData();
    String message = gson.toJson(scanEventData);
    doNothing().when(endGameDivertAckEventProcessor).processEvent(any(MessageData.class));
    when(configUtils.isFacilityEnabled(anyInt())).thenReturn(Boolean.TRUE);
    TenantContext.setEventType(REPLEN_CASE);
    divertAckHelper.doProcess(message, MockMessageData.getMockKafkaListenerHeaders());
    TenantContext.setEventType("");
    verify(endGameDivertAckEventProcessor, times(0)).processEvent(any(ScanEventData.class));
    verify(configUtils, times(1)).isFacilityEnabled(anyInt());
  }
}
