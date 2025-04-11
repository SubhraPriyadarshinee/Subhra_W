package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.symbotic.RdcVerificationMessage;
import com.walmart.move.nim.receiving.data.MockVerificationMessage;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcVerificationEventProcessorTest {
  @InjectMocks private RdcVerificationEventProcessor rdcVerificationEventProcessor;
  @Mock private RdcAutoReceiveService rdcAutoReceiveService;
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
  }

  @AfterMethod
  public void tearDown() {
    reset(appConfig);
  }

  @Test
  public void testListenerSkipMessagesForNotEnabledFacilities() {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32944));
    String message = MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    rdcVerificationMessage.setHttpHeaders(getMockHeaders());
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
  }

  @Test
  public void testListener_invalidGroupType() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    HttpHeaders httpHeaders = getMockHeaders();
    httpHeaders.replace(ReceivingConstants.TENENT_GROUP_TYPE, Collections.singletonList("DA"));
    rdcVerificationMessage.setHttpHeaders(httpHeaders);
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(0))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  @Test
  public void testListener_invalidMessageType_Unknown() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    HttpHeaders httpHeaders = getMockHeaders();
    httpHeaders.put(
        ReceivingConstants.MESSAGE_TYPE,
        Collections.singletonList(ReceivingConstants.RDC_MESSAGE_TYPE_UNKNOWN));
    rdcVerificationMessage.setHttpHeaders(httpHeaders);
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(0))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  @Test
  public void testListener_validMessage() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    rdcVerificationMessage.setHttpHeaders(getMockHeaders());
    doNothing()
        .when(rdcAutoReceiveService)
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(1))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  @Test
  public void testListener_invalidMessageType() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.INVALID_SYM_VERIFICATION_MESSAGE_TYPE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    rdcVerificationMessage.setHttpHeaders(getMockHeaders());
    doNothing()
        .when(rdcAutoReceiveService)
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(0))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  @Test
  public void testListener_receivedPallet() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.SYM_VERIFICATION_MESSAGE_RECEIVED;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    rdcVerificationMessage.setHttpHeaders(getMockHeaders());
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(0))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  @Test
  public void testListener_CatchesReceivingException() throws ReceivingException {
    when(appConfig.getHawkeyeMessageListenerEnabledFacilities()).thenReturn(Arrays.asList(32679));
    String message = MockVerificationMessage.VALID_SYM_VERIFICATION_MESSAGE;
    RdcVerificationMessage rdcVerificationMessage =
        gson.fromJson(message, RdcVerificationMessage.class);
    rdcVerificationMessage.setHttpHeaders(getMockHeaders());
    doThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .errorResponse(
                    ErrorResponse.builder()
                        .errorMessage(ReceivingException.GDM_SERVICE_DOWN)
                        .build())
                .build())
        .when(rdcAutoReceiveService)
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
    rdcVerificationEventProcessor.processEvent(rdcVerificationMessage);
    verify(appConfig, times(1)).getHawkeyeMessageListenerEnabledFacilities();
    verify(rdcAutoReceiveService, times(1))
        .autoReceiveOnVerificationEvent(any(RdcVerificationMessage.class), any(HttpHeaders.class));
  }

  private HttpHeaders getMockHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add(ReceivingConstants.TENENT_GROUP_TYPE, "RCV_DA");
    httpHeaders.add(ReceivingConstants.CORRELATION_ID_HEADER_KEY, UUID.randomUUID().toString());
    httpHeaders.add(ReceivingConstants.TENENT_FACLITYNUM, facilityNum);
    httpHeaders.add(ReceivingConstants.TENENT_COUNTRY_CODE, countryCode);
    return httpHeaders;
  }
}
