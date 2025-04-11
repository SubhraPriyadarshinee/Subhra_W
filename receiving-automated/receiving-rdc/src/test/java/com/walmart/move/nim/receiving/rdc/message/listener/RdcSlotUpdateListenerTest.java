package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcSlotUpdateMessage;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.service.RdcSlotUpdateEventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcSlotUpdateListenerTest {

  @InjectMocks RdcSlotUpdateListener rdcSlotUpdateListener;
  @Mock private RdcSlotUpdateEventProcessor rdcSlotUpdateEventProcessor;
  @Mock private AppConfig appConfig;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  private static final String facilityNum = "32679";
  private static final String countryCode = "US";
  private Gson gson;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(rdcSlotUpdateListener, "gson", gson);
  }

  @Test
  public void testListener_messageEmpty() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.parseInt(facilityNum)));
    rdcSlotUpdateListener.listen("", getKafkaHeaders());
    verify(rdcSlotUpdateEventProcessor, times(0)).processEvent(any(RdcSlotUpdateMessage.class));
  }

  @Test
  public void testListener_success() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.parseInt(facilityNum)));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcSlotUpdateListener.listen(gson.toJson(getMockRdcSlotUpdateMessage()), getKafkaHeaders());
    verify(rdcSlotUpdateEventProcessor, times(1)).processEvent(any());
  }

  @Test
  public void testListener_EmptyMessageList() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Arrays.asList(Integer.parseInt(facilityNum)));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcSlotUpdateListener.listen(gson.toJson(Collections.EMPTY_LIST), getKafkaHeaders());
    verify(rdcSlotUpdateEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_throwsException() {
    doThrow(new ReceivingInternalException("mock error", "mock_error"))
        .when(rdcSlotUpdateEventProcessor)
        .processEvent(any());
    try {
      when(appConfig.getSlotUpdateListenerEnabledFacilities())
          .thenReturn(Arrays.asList(Integer.parseInt(facilityNum)));
      when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
              anyString(), anyString(), anyBoolean()))
          .thenReturn(true);
      rdcSlotUpdateListener.listen(gson.toJson(getMockRdcSlotUpdateMessage()), getKafkaHeaders());
    } catch (ReceivingInternalException e) {
      verify(rdcSlotUpdateEventProcessor, times(1)).processEvent(any());
    }
  }

  @Test
  public void testListener_invalid_facilityNumber() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32698));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    rdcSlotUpdateListener.listen(gson.toJson(getMockRdcSlotUpdateMessage()), getKafkaHeaders());
    verify(rdcSlotUpdateEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_SSTK_automation_flag_disabled() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32679));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(false);
    rdcSlotUpdateListener.listen(gson.toJson(getMockRdcSlotUpdateMessage()), getKafkaHeaders());
    verify(rdcSlotUpdateEventProcessor, times(0)).processEvent(any());
  }

  @Test
  public void testListener_invalid_eventType() {
    when(appConfig.getSlotUpdateListenerEnabledFacilities())
        .thenReturn(Collections.singletonList(32679));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), anyString(), anyBoolean()))
        .thenReturn(true);
    List<RdcSlotUpdateMessage> rdcSlotUpdateMessageList = getMockRdcSlotUpdateMessage();
    Map<String, byte[]> headers = getKafkaHeaders();
    headers.replace(
        ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_CAPACITY_UPDATE.getBytes());
    rdcSlotUpdateListener.listen(gson.toJson(rdcSlotUpdateMessageList.get(0)), headers);
    verify(rdcSlotUpdateEventProcessor, times(0)).processEvent(any());
  }

  private Map<String, byte[]> getKafkaHeaders() {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getMockKafkaListenerHeaders();
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_COUNTRY_CODE, countryCode.getBytes());
    messageHeaders.put(
        ReceivingConstants.EVENT_TYPE, ReceivingConstants.ITEM_PRIME_DETAILS.getBytes());
    messageHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString().getBytes());
    return messageHeaders;
  }

  private List<RdcSlotUpdateMessage> getMockRdcSlotUpdateMessage() {
    return Collections.singletonList(
        RdcSlotUpdateMessage.builder()
            .primeSlotId(RdcConstants.SYMCP_SLOT)
            .asrsAlignment(ReceivingConstants.SYM_SYSTEM_DEFAULT_VALUE)
            .itemNbr(123456L)
            .build());
  }
}
