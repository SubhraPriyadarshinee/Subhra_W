package com.walmart.move.nim.receiving.rdc.message.listener;

import static org.mockito.Mockito.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.data.MockMessageHeaders;
import com.walmart.move.nim.receiving.data.MockVerificationMessage;
import com.walmart.move.nim.receiving.rdc.service.RdcVerificationEventProcessor;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcVerificationMessageListenerTest {
  @InjectMocks private RdcVerificationMessageListener rdcVerificationMessageListener;
  @Mock RdcVerificationEventProcessor rdcVerificationEventProcessor;
  @Mock private AppConfig appConfig;
  private Gson gson;
  private static final String facilityNum = "32679";
  private static final String countryCode = "US";

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityCountryCode(countryCode);
    TenantContext.setFacilityNum(Integer.valueOf(facilityNum));
    gson = new Gson();
    ReflectionTestUtils.setField(rdcVerificationMessageListener, "gson", gson);
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig);
  }

  @Test
  public void testListener_messageEmpty() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    rdcVerificationMessageListener.listen("", getKafkaHeaders());
    verify(appConfig, times(0)).getHawkeyeMessageListenerEnabledFacilities();
  }

  @Test
  public void testListener_throwsException() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    doThrow(new ReceivingInternalException("mock error", "mock_error"))
        .when(rdcVerificationEventProcessor)
        .processEvent(any());
    try {
      rdcVerificationMessageListener.listen(
          MockVerificationMessage.INVALID_SYM_VERIFICATION_MESSAGE, getKafkaHeaders());
    } catch (ReceivingInternalException e) {
      verify(rdcVerificationEventProcessor, times(1)).processEvent(any());
    }
  }

  @Test
  public void testListener_success() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    rdcVerificationMessageListener.listen(
        MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE, getKafkaHeaders());
    verify(rdcVerificationEventProcessor, times(1)).processEvent(any());
  }

  private Map<String, byte[]> getKafkaHeaders() {
    Map<String, byte[]> messageHeaders = MockMessageHeaders.getMockKafkaListenerHeaders();
    messageHeaders.put(ReceivingConstants.TENENT_FACLITYNUM, facilityNum.getBytes());
    messageHeaders.put(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA".getBytes());
    messageHeaders.put(ReceivingConstants.GROUP_NBR, "124356".getBytes());
    messageHeaders.put(
        ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString().getBytes());
    return messageHeaders;
  }
}
