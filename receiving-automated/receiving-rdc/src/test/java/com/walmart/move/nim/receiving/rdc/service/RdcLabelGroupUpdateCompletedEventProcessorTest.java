package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.symbotic.LabelGroupUpdateCompletedEventMessage;
import com.walmart.move.nim.receiving.data.MockLabelGroupUpdateCompletionMessage;
import com.walmart.move.nim.receiving.rdc.model.docktag.DockTagData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcLabelGroupUpdateCompletedEventProcessorTest {
  @Mock private AppConfig appConfig;
  @Mock private RdcCompleteDeliveryProcessor rdcCompleteDeliveryProcessor;
  @Mock private RdcDockTagService rdcDockTagService;

  @InjectMocks
  RdcLabelGroupUpdateCompletedEventProcessor rdcLabelGroupUpdateCompletedEventProcessor;

  private static final String facilityNum = "32679";
  private static final String countryCode = "US";
  private Gson gson;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
  }

  @Test
  public void testListenerSkipMessagesForNotEnabledFacilities() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32944));
    String message =
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG;
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(getMockHeaders());
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(0))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(0)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testListener_COMPLETED_Status_completeDelivery_throwsException()
      throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doThrow(new ReceivingException("Mock error"))
        .when(rdcCompleteDeliveryProcessor)
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    String message =
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE;
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(getMockHeaders());
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(1))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(0)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testListener_COMPLETED_Status_ReceivedPallet_NoAction() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message =
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE_PALLET;
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(getMockHeaders());
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(0))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(0)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testListener_COMPLETED_Status_completeDockTag() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doReturn(new DockTagData())
        .when(rdcDockTagService)
        .completeDockTagById(anyString(), any(HttpHeaders.class));
    String message =
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETION_MESSAGE_DOCKTAG;
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(getMockHeaders());
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(0))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(1)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  @Test
  public void testListener_COMPLETED_Status_completeDelivery() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doReturn(new DeliveryInfo())
        .when(rdcCompleteDeliveryProcessor)
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    String message =
        MockLabelGroupUpdateCompletionMessage.VALID_LABEL_GROUP_UPDATE_COMPLETED_MESSAGE;
    LabelGroupUpdateCompletedEventMessage labelGroupUpdateCompletedEventMessage =
        gson.fromJson(message, LabelGroupUpdateCompletedEventMessage.class);
    labelGroupUpdateCompletedEventMessage.setHttpHeaders(getMockHeaders());
    rdcLabelGroupUpdateCompletedEventProcessor.processEvent(labelGroupUpdateCompletedEventMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcCompleteDeliveryProcessor, times(1))
        .completeDelivery(anyLong(), anyBoolean(), any(HttpHeaders.class));
    verify(rdcDockTagService, times(0)).completeDockTagById(anyString(), any(HttpHeaders.class));
  }

  private HttpHeaders getMockHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA");
    httpHeaders.add(ReceivingConstants.GROUP_NBR, "123456");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, countryCode);
    return httpHeaders;
  }
}
