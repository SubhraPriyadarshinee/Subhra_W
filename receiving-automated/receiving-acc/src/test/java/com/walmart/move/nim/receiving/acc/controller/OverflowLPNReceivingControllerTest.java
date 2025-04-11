package com.walmart.move.nim.receiving.acc.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONEOPS_ENVIRONMENT;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.acc.model.OverflowLPNReceivingRequest;
import com.walmart.move.nim.receiving.acc.service.OverflowLPNReceivingService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import io.strati.security.jaxrs.ws.rs.core.MediaType;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class OverflowLPNReceivingControllerTest extends ReceivingTestBase {

  private MockMvc mockMvc;

  private Gson gson = new Gson();

  private OverflowLPNReceivingController overflowLPNReceivingController;

  @Mock private OverflowLPNReceivingService lpnReceivingService;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    overflowLPNReceivingController = new OverflowLPNReceivingController();
    ReflectionTestUtils.setField(
        overflowLPNReceivingController, "lpnReceivingService", lpnReceivingService);
    System.setProperty(ONEOPS_ENVIRONMENT, "dev");
    this.mockMvc = MockMvcBuilders.standaloneSetup(overflowLPNReceivingController).build();
  }

  @Test
  public void testReceiveByLpn() throws Exception {
    OverflowLPNReceivingRequest request =
        OverflowLPNReceivingRequest.builder()
            .lpn("a12345")
            .deliveryNumber(12345L)
            .location("dummyLocation")
            .build();

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/overflow/receive-lpn")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(httpHeaders)
                .content(gson.toJson(request, OverflowLPNReceivingRequest.class)))
        .andExpect(status().isOk());
  }

  @Test
  public void testReceiveByLpn_ApiFail_LpnIsNull() throws Exception {
    OverflowLPNReceivingRequest request =
        OverflowLPNReceivingRequest.builder()
            .deliveryNumber(12345L)
            .location("dummyLocation")
            .build();

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/overflow/receive-lpn")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .headers(httpHeaders)
                .content(gson.toJson(request, OverflowLPNReceivingRequest.class)))
        .andExpect(status().isBadRequest());
  }
}
