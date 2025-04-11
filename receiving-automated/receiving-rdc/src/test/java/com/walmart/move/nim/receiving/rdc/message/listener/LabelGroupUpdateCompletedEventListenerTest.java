package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.apache.kafka.common.utils.Bytes.EMPTY;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.LabelGroupUpdateCompletedEventMessage;
import com.walmart.move.nim.receiving.data.MockLabelGroupUpdateCompletionMessage;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.rdc.service.RdcCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.rdc.service.RdcDockTagService;
import com.walmart.move.nim.receiving.rdc.service.RdcLabelGroupUpdateCompletedEventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Map;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class LabelGroupUpdateCompletedEventListenerTest {
  @InjectMocks
  private LabelGroupUpdateCompletedEventListener labelGroupUpdateCompletedEventListener;

  @Mock private AppConfig appConfig;
  @Mock private RdcCompleteDeliveryProcessor rdcCompleteDeliveryProcessor;

  @Mock
  private RdcLabelGroupUpdateCompletedEventProcessor rdcLabelGroupUpdateCompletedEventProcessor;

  @Mock private RdcDockTagService rdcDockTagService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private Gson gson;
  private static final String facilityNum = "32679";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(labelGroupUpdateCompletedEventListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(
        appConfig,
        rdcCompleteDeliveryProcessor,
        rdcDockTagService,
        rdcLabelGroupUpdateCompletedEventProcessor);
  }

  @Test
  public void testListener_emptyMessage() throws ReceivingException {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    labelGroupUpdateCompletedEventListener.listen("", messageHeaders);
    verify(appConfig, times(0)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(0))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(0)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testListener_throwsException() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doThrow(new ReceivingInternalException("mock error", "mock_error"))
        .when(rdcLabelGroupUpdateCompletedEventProcessor)
        .processEvent(any());
    try {

      Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
      messageHeaders.put(ReceivingConstants.GROUP_NBR, "124356".getBytes());
      labelGroupUpdateCompletedEventListener.listen(
          MockLabelGroupUpdateCompletionMessage.INVALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE,
          messageHeaders);
    } catch (ReceivingInternalException e) {
      verify(rdcLabelGroupUpdateCompletedEventProcessor, times(1)).processEvent(any());
    }
  }

  @Test
  public void testListener_success() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    messageHeaders.put(ReceivingConstants.GROUP_NBR, "124356".getBytes());
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
        messageHeaders);
    LabelGroupUpdateCompletedEventMessage expectedLabelGroupUpdateCompletedEventMessage =
        gson.fromJson(
            MockLabelGroupUpdateCompletionMessage
                .VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
            LabelGroupUpdateCompletedEventMessage.class);
    expectedLabelGroupUpdateCompletedEventMessage.setHttpHeaders(
        ReceivingUtils.populateHttpHeadersFromKafkaHeaders(messageHeaders));
    expectedLabelGroupUpdateCompletedEventMessage.setDeliveryNumber("124356");
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(1))
        .processEvent(expectedLabelGroupUpdateCompletedEventMessage);
  }

  @Test
  public void testListener_inValidEvent() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    messageHeaders.put(ReceivingConstants.GROUP_NBR, EMPTY);
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
        messageHeaders);
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_inValid_emptyGroupNbr() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    messageHeaders.put(ReceivingConstants.GROUP_NBR, "".getBytes());
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
        messageHeaders);
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_inValid_nullGroupNbr() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    messageHeaders.put(ReceivingConstants.GROUP_NBR, null);
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
        messageHeaders);
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_inValid_NoGroupNbr() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG,
        messageHeaders);
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_inValidLabelGroupCompletedMessage() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doNothing().when(rdcLabelGroupUpdateCompletedEventProcessor).processEvent(any());
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getKafkaHeadersForLabelGroupEvent();
    labelGroupUpdateCompletedEventListener.listen(
        MockLabelGroupUpdateCompletionMessage.INVALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE,
        messageHeaders);
    verify(rdcLabelGroupUpdateCompletedEventProcessor, times(0)).processEvent(any());
  }
}
