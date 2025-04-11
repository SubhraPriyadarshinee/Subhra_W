package com.walmart.move.nim.receiving.rdc.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.model.DeliveryLinkRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.service.RdcDeliveryLinkService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcDeliveryLinkControllerTest extends ReceivingControllerTestBase {
  @InjectMocks private RdcDeliveryLinkController rdcDeliveryLinkController;
  @Mock private RdcDeliveryLinkService rdcDeliveryLinkService;
  private MockMvc mockMvc;

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(rdcDeliveryLinkController)
            .setControllerAdvice(RestResponseExceptionHandler.class)
            .build();
  }

  @AfterMethod
  public void resetMocks() {
    reset(rdcDeliveryLinkService);
  }

  @Test
  public void testDeliveryLink_Success() throws Exception {
    DeliveryLinkRequest deliveryLinkRequest = getDeliveryLinkRequest();
    doReturn(new ResponseEntity<>(null, HttpStatus.OK))
        .when(rdcDeliveryLinkService)
        .validateReadinessAndLinkDelivery(any(DeliveryLinkRequest.class), any(HttpHeaders.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/rdc/delivery/link")
                .headers(MockHttpHeaders.getHeaders())
                .content(JacksonParser.writeValueAsString(deliveryLinkRequest)))
        .andExpect(MockMvcResultMatchers.status().isOk());

    verify(rdcDeliveryLinkService, times(1))
        .validateReadinessAndLinkDelivery(any(DeliveryLinkRequest.class), any(HttpHeaders.class));
  }

  private DeliveryLinkRequest getDeliveryLinkRequest() {
    return DeliveryLinkRequest.builder().deliveryNumber("12345").locationId("DOOR_111").build();
  }
}
