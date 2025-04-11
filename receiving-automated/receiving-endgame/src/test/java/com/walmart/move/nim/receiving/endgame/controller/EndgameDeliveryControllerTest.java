package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.model.Location;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class EndgameDeliveryControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private EndgameDeliveryController endgameDeliveryController;
  //  @Spy @InjectMocks private EndGameDeliveryService endGameDeliveryService;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private EndGameDeliveryService endGameDeliveryService;

  private MockMvc mockMvc;
  private Gson gson;
  private Location request;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();

  private static String DOOR_NUM = "200";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);
    request = new Location();
    gson = new Gson();
    //    ReflectionTestUtils.setField(endgameDeliveryController, "gson", gson);

    mockMvc =
        MockMvcBuilders.standaloneSetup(endgameDeliveryController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void setUp() {
    request.setLocation(DOOR_NUM);
    request.setMoveRequired(false);
    reset(endGameDeliveryService);
  }

  @Test
  public void testProcessDeliveryEventSuccess() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/delivery/process")
                .headers(headers)
                .content(gson.toJson(request)))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(endGameDeliveryService, times(1)).processPendingDeliveryEvent(any());
  }

  @Test
  public void testGetDeliveryDoorStatus() throws Exception {
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/endgame/delivery/door/" + DOOR_NUM + "/status")
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(endGameDeliveryService, times(1)).getDoorStatus(any());
  }
}
