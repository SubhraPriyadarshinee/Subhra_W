package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.service.EndgameContainerService;
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
import org.testng.annotations.Test;

public class EndgameReceivingControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private EndgameReceivingController endgameReceivingController;

  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  private HttpHeaders headers = MockHttpHeaders.getHeaders();

  @Mock private EndgameContainerService endgameContainerService;

  private MockMvc mockMvc;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    mockMvc =
        MockMvcBuilders.standaloneSetup(endgameReceivingController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void testReceiveContainerUpdates() throws Exception {
    doNothing().when(endgameContainerService).processContainerUpdates(any());

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/receiving/container/updates")
                .headers(headers)
                .contentType(MediaType.APPLICATION_JSON)
                .content("test"))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(endgameContainerService, times(1)).processContainerUpdates(anyString());
  }
}
