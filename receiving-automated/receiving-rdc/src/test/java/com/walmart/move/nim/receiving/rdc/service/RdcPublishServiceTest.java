package com.walmart.move.nim.receiving.rdc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.SymboticPutawayPublishHelper;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.symbotic.SymPutawayMessage;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class RdcPublishServiceTest {

  @InjectMocks private RdcPublishService rdcPublishService;
  @Mock private SymboticPutawayPublishHelper symboticPutawayPublishHelper;
  @Mock private AppConfig appConfig;
  private HttpHeaders httpHeaders = null;
  private Gson gson;
  private final List<String> VALID_ASRS_LIST =
      Arrays.asList(
          ReceivingConstants.SYM_BRKPK_ASRS_VALUE, ReceivingConstants.SYM_CASE_PACK_ASRS_VALUE);

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    gson = new Gson();
    ReflectionTestUtils.setField(rdcPublishService, "gson", gson);
  }

  @BeforeMethod
  public void setup() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    httpHeaders = MockHttpHeaders.getHeaders();
  }

  @AfterMethod
  public void tearDown() {
    reset(symboticPutawayPublishHelper, appConfig);
  }

  @Test
  private void testPublishMessageThrowsExceptionWhenMessageTypeHeaderIsMissing() {
    String messageBody = "testJson";
    try {
      rdcPublishService.publishMessage(messageBody, httpHeaders);
    } catch (Exception exception) {
      assertNotNull(exception);
      assertEquals(exception.getMessage(), ReceivingConstants.INVALID_MESSAGE_TYPE);
    }
  }

  @Test
  private void testPublishMessageThrowsExceptionWhenMessageTypeIsNotSupported() {
    String messageBody = "testJson";
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "test");
    try {
      rdcPublishService.publishMessage(messageBody, httpHeaders);
    } catch (Exception exception) {
      assertNotNull(exception);
      assertEquals(exception.getMessage(), ReceivingConstants.UNSUPPORTED_MESSAGE_TYPE);
    }
  }

  @Test
  private void testPublishMessageThrowsExceptionWhenSymSystemHeaderIsMissing() {
    String messageBody = "testJson";
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    try {
      rdcPublishService.publishMessage(messageBody, httpHeaders);
    } catch (Exception exception) {
      assertNotNull(exception);
      assertEquals(exception.getMessage(), ReceivingConstants.INVALID_SYM_SYSTEM);
    }
  }

  @Test
  private void testPublishMessageThrowsExceptionWhenPartialPutawayMessageRequestIsProvided()
      throws IOException {
    File resource = new ClassPathResource("MockSymPartialPutawayRequest.json").getFile();
    String messageBody = new String(Files.readAllBytes(resource.toPath()));
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    httpHeaders.add(ReceivingConstants.SYMBOTIC_SYSTEM, "SYM2_5");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    try {
      rdcPublishService.publishMessage(messageBody, httpHeaders);
    } catch (Exception exception) {
      assertNotNull(exception);
      assertEquals(exception.getMessage(), ReceivingConstants.PARTIAL_PUTAWAY_REQUEST);
    }
  }

  @Test
  private void testPublishMessageThrowsExceptionWhenInvalidPutawayMessageRequestProvided()
      throws IOException {
    String messageBody = "testJson";
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    httpHeaders.add(ReceivingConstants.SYMBOTIC_SYSTEM, "SYM2_5");
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    try {
      rdcPublishService.publishMessage(messageBody, httpHeaders);
    } catch (Exception exception) {
      assertNotNull(exception);
      assertEquals(exception.getMessage(), ReceivingConstants.INVALID_PUTAWAY_REQUEST);
    }
  }

  @Test
  private void testPublishMessage_doNotPublish_with_invalid_asrsAlignment_value()
      throws IOException {
    String messageBody = "testJson";
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    httpHeaders.add(ReceivingConstants.SYMBOTIC_SYSTEM, ReceivingConstants.PTL_ASRS_VALUE);
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    rdcPublishService.publishMessage(messageBody, httpHeaders);
    verify(symboticPutawayPublishHelper, times(0))
        .publish(anyString(), anyMap(), any(SymPutawayMessage.class));
  }

  @Test
  private void testPublishMessageToHawkeyeSuccess() throws IOException {
    File resource = new ClassPathResource("MockSymPutawayRequest.json").getFile();
    String messageBody = new String(Files.readAllBytes(resource.toPath()));
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    httpHeaders.add(ReceivingConstants.SYMBOTIC_SYSTEM, "SYM2_5");
    doNothing()
        .when(symboticPutawayPublishHelper)
        .publish(anyString(), anyMap(), any(SymPutawayMessage.class));
    when(appConfig.getValidSymAsrsAlignmentValues()).thenReturn(VALID_ASRS_LIST);
    rdcPublishService.publishMessage(messageBody, httpHeaders);
    verify(symboticPutawayPublishHelper, times(1))
        .publish(anyString(), anyMap(), any(SymPutawayMessage.class));
  }
}
