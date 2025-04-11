package com.walmart.move.nim.receiving.endgame.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ONEOPS_ENVIRONMENT;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import java.util.Optional;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@PropertySource("classpath:application.properties")
public class EndgameTestControllerTest extends ReceivingControllerTestBase {

  private static final String TEST_DELIVERY_NUMBER = "12345678";

  private MockMvc mockMvc;

  @InjectMocks private EndgameTestController endgameTestController;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private ResourceBundleMessageSource resourceBundleMessageSource;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    System.setProperty(ONEOPS_ENVIRONMENT, "dev");
    this.mockMvc =
        MockMvcBuilders.standaloneSetup(endgameTestController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @Test
  public void testDeleteRecon() throws Exception {
    doNothing()
        .when(endGameLabelingService)
        .deleteTCLByDeliveryNumber(Long.valueOf(TEST_DELIVERY_NUMBER));
    doNothing().when(endGameLabelingService).deleteDeliveryMetaData(TEST_DELIVERY_NUMBER);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.delete("/endgame/test/deliveries/" + TEST_DELIVERY_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testGetDeliveryMetaData() throws Exception {
    when(endGameLabelingService.findDeliveryMetadataByDeliveryNumber(TEST_DELIVERY_NUMBER))
        .thenReturn(getDeliveryMetaData());

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    // TODO JSON Schema validation needs to be added
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/endgame/test/deliveries/" + TEST_DELIVERY_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testNegetiveGetDeliveryMetaData() throws Exception {
    doReturn(Optional.empty())
        .when(endGameLabelingService)
        .findDeliveryMetadataByDeliveryNumber(TEST_DELIVERY_NUMBER);

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    // TODO JSON Schema validation needs to be added
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/endgame/test/deliveries/" + TEST_DELIVERY_NUMBER)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isNotFound());
  }

  private Optional<DeliveryMetaData> getDeliveryMetaData() {
    DeliveryMetaData deliveryMetaData =
        DeliveryMetaData.builder()
            .deliveryNumber(TEST_DELIVERY_NUMBER)
            .totalCaseLabelSent(100)
            .totalCaseCount(100)
            .build();
    return Optional.of(deliveryMetaData);
  }
}
