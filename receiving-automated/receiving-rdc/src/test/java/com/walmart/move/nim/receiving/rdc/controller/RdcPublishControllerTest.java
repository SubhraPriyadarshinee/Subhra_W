package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.service.RdcPublishService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcPublishControllerTest {

  @InjectMocks private RdcPublishController rdcPublishController;
  @Mock private RdcPublishService rdcPublishService;
  private MockMvc mockMvc;
  private static final String PUBLISH_MESSAGE_URL = "/rdc/publish/message";
  HttpHeaders httpHeaders;

  @BeforeClass
  public void init() {
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("US");
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rdcPublishController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
    httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.MESSAGE_TYPE, "putaway");
    httpHeaders.add(ReceivingConstants.SYMBOTIC_SYSTEM, "SYM2_5");
  }

  @AfterMethod
  public void tearDown() {
    reset(rdcPublishService);
  }

  @Test
  private void testPublishMessageInvalidRequestBodyWithEmptyMessage() throws Exception {
    mockMvc
        .perform(MockMvcRequestBuilders.post(PUBLISH_MESSAGE_URL).headers(httpHeaders).content(""))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());

    verify(rdcPublishService, times(0)).publishMessage(anyString(), any(HttpHeaders.class));
  }

  @Test
  private void testPublishMessageWithValidMessageBodySuccess() throws Exception {
    String messageBody = getMockSymPutawayMessageRequest();
    doNothing().when(rdcPublishService).publishMessage(anyString(), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post(PUBLISH_MESSAGE_URL)
                .headers(httpHeaders)
                .content(messageBody))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());

    verify(rdcPublishService, times(1)).publishMessage(anyString(), any(HttpHeaders.class));
  }

  private String getMockSymPutawayMessageRequest() throws IOException {
    File resource = new ClassPathResource("MockSymPutawayRequest.json").getFile();
    return new String(Files.readAllBytes(resource.toPath()));
  }
}
