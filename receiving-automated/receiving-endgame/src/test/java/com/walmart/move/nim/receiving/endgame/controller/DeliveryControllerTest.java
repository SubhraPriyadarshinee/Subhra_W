package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DeliveryControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private EndgameDeliveryController endgameDeliveryController;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;
  @Mock private EndGameDeliveryService endgameDeliveryService;
  private MockMvc mockMvc;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private static String DOOR_NUM = "100";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(endgameDeliveryController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void setUp() {
    reset(endgameDeliveryService);
  }

  @Test
  public void testSuccessForDelivery() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/endgame/delivery/door/" + DOOR_NUM + "/status")
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(endgameDeliveryService, times(1)).getDoorStatus(DOOR_NUM);
  }
}
