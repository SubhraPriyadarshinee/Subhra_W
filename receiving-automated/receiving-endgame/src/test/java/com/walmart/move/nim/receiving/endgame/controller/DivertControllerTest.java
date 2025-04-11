package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.DivertPriorityChangeRequest;
import com.walmart.move.nim.receiving.endgame.repositories.SlottingDestinationRepository;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import java.util.Collections;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class DivertControllerTest extends ReceivingControllerTestBase {

  @InjectMocks private DivertController divertController;
  @Spy @InjectMocks private EndGameSlottingService endGameSlottingService;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private SlottingDestinationRepository slottingDestinationRepository;
  @Mock private KafkaTemplate kafkaTemplate;
  @Mock private ResourceBundleMessageSource resourceBundleMessageSource;

  private MockMvc mockMvc;
  private Gson gson;
  private DivertPriorityChangeRequest request;
  private SlottingDestination slottingDestination;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  private static String caseUPC = "10000000000000";

  @BeforeClass
  public void init() {
    MockitoAnnotations.initMocks(this);

    request = new DivertPriorityChangeRequest();
    slottingDestination = new SlottingDestination();
    gson = new Gson();
    ReflectionTestUtils.setField(endGameSlottingService, "gson", gson);

    mockMvc =
        MockMvcBuilders.standaloneSetup(divertController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void setUp() {
    request.setCaseUPC(caseUPC);
    request.setNewDivert("DECANT");
    request.setPreviousDivert("PALLET_BUILD");

    slottingDestination.setCaseUPC(caseUPC);
    slottingDestination.setPossibleUPCs(caseUPC);
    slottingDestination.setDestination("PALLET_BUILD");
    ReflectionTestUtils.setField(endGameSlottingService, "hawkeyeDivertUpdateTopic", "hello");

    reset(kafkaTemplate);
    reset(slottingDestinationRepository);
  }

  @Test
  public void testSuccess() throws Exception {
    when(slottingDestinationRepository.findByCaseUPC(any()))
        .thenReturn(
            EndGameUtilsTestCase.getSingleSlottingDestination(caseUPC, DivertStatus.DECANT));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/divert")
                .headers(headers)
                .content(gson.toJson(request)))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(slottingDestinationRepository, times(1)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(1)).send(isA(Message.class));
  }

  @Test
  public void testSuccessWithMultipleSellerId() throws Exception {
    when(slottingDestinationRepository.findByCaseUPC(any()))
        .thenReturn(
            EndGameUtilsTestCase.getSingleSlottingDestinationWithMultipleSellerId(
                caseUPC, DivertStatus.DECANT));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/divert")
                .headers(headers)
                .content(gson.toJson(request)))
        .andExpect(MockMvcResultMatchers.status().isOk());
    verify(slottingDestinationRepository, times(0)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(0)).send(isA(Message.class));
  }

  @Test
  public void testUPCNotFound() throws Exception {
    when(slottingDestinationRepository.findByCaseUPC(any())).thenReturn(Collections.emptyList());
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/divert")
                .headers(headers)
                .content(gson.toJson(request)))
        .andExpect(MockMvcResultMatchers.status().isNotFound());
    verify(slottingDestinationRepository, times(0)).save(any(SlottingDestination.class));
    verify(kafkaTemplate, times(0)).send(isA(Message.class));
  }
}
