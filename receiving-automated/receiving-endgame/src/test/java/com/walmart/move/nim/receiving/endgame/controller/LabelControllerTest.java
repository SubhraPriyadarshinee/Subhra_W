package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.KafkaConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.ReceivingCounter;
import com.walmart.move.nim.receiving.core.repositories.ReceivingCounterRepository;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.ReceivingCounterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtilsTestCase;
import com.walmart.move.nim.receiving.endgame.config.EndgameManagedConfig;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.constants.LabelGenMode;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockLabelResponse;
import com.walmart.move.nim.receiving.endgame.model.LabelRequest;
import com.walmart.move.nim.receiving.endgame.model.LabelResponse;
import com.walmart.move.nim.receiving.endgame.model.LabelSummary;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataCustomRepository;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataRepository;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.mockito.*;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@PropertySource("classpath:application.properties")
public class LabelControllerTest extends ReceivingControllerTestBase {

  private LabelRequest labelRequest;

  private MockMvc mockMvc;

  private Gson gson;

  @InjectMocks private LabelController labelController;
  @Spy @InjectMocks private EndGameLabelingService labelingService;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private DeliveryMetaDataService deliveryMetaDataService;
  @Mock private PreLabelDataRepository preLabelDataRepository;
  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;
  @Mock private ReceivingCounterRepository receivingCounterRepository;
  @Mock private ReceivingCounterService receivingCounterService;
  @Mock private EndgameManagedConfig endgameManagedConfig;
  @Mock private KafkaTemplate kafkaTemplate;
  @Mock private PreLabelDataCustomRepository preLabelDataCustomRepository;
  @Mock private KafkaConfig kafkaConfig;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private ResourceBundleMessageSource resourceBundleMessageSource;

  private HttpHeaders headers = MockHttpHeaders.getHeaders();

  @BeforeMethod
  public void setUp() {
    labelRequest.setDeliveryNumber(87654321L);
    labelRequest.setDoorNumber("123");
    labelRequest.setLabelType("TCL");
    labelRequest.setNumberOfLabels(2L);
    labelRequest.setTrailerId("654321");
    labelRequest.setLabelGenMode(LabelGenMode.MOBILE.toString());

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(4034);
    ReflectionTestUtils.setField(labelingService, "hawkeyeLabelTopic", "label-topic");
  }

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    gson = new Gson();
    labelRequest = new LabelRequest();
    mockMvc =
        MockMvcBuilders.standaloneSetup(labelController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
    ReflectionTestUtils.setField(labelingService, "gson", gson);
  }

  private Optional<DeliveryMetaData> deliveryMetaData() {
    return Optional.of(
        DeliveryMetaData.builder()
            .totalCaseLabelSent(100)
            .deliveryNumber("87654321")
            .totalCaseCount(100)
            .build());
  }

  @Test
  public void testGenerateLabel() throws Exception {
    when(endGameDeliveryService.getGDMData(any())).thenReturn(EndGameUtilsTestCase.getDelivery());
    when(endgameManagedConfig.getEndgameDefaultDestination()).thenReturn("K01");
    when(endgameManagedConfig.getPrintableZPLTemplate())
        .thenReturn(MockLabelResponse.createMockPrintableZPLTemplate());
    when(receivingCounterService.counterUpdation(anyLong(), any())).thenReturn(getDefaultCounter());
    when(deliveryMetaDataService.findByDeliveryNumber(anyString())).thenReturn(deliveryMetaData());
    when(deliveryMetaDataService.save(any())).then(rep -> rep.getArgument(0));

    MvcResult result =
        mockMvc
            .perform(
                MockMvcRequestBuilders.post("/endgame/labels/request")
                    .content(gson.toJson(labelRequest))
                    .headers(headers))
            .andDo(print())
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn();

    LabelResponse response =
        gson.fromJson(result.getResponse().getContentAsString(), LabelResponse.class);

    assertEquals(response.getClientId(), "receiving");
    assertEquals(response.getPrintRequests().size(), 2);
    assertEquals(
        response.getHeaders().get(ReceivingConstants.CORRELATION_ID_HEADER_KEY), "1a2bc3d4");
    assertTrue(response.getPrintRequests().iterator().next().getLabelIdentifier().startsWith("T"));
  }

  @Test
  public void testGenerateLabel_DeliveryNotFound() throws Exception {
    when(endGameDeliveryService.getGDMData(any()))
        .thenThrow(
            new ReceivingException(
                ReceivingException.DELIVERY_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE));

    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/labels/request")
                .content(gson.toJson(labelRequest))
                .headers(headers))
        .andDo(print())
        .andExpect(MockMvcResultMatchers.status().isNotFound())
        .andReturn();
  }

  @Test
  public void testNullDeliveryNumber() throws Exception {
    labelRequest.setDeliveryNumber(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/labels/request")
                .content(gson.toJson(labelRequest))
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testNullLabelType() throws Exception {
    labelRequest.setLabelType(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/labels/request")
                .content(gson.toJson(labelRequest))
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  public void testNullNumberOfLabels() throws Exception {
    labelRequest.setNumberOfLabels(null);
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/labels/request")
                .content(gson.toJson(labelRequest))
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  private Optional<PreLabelData> preLabelData() {
    PreLabelData preLabelData =
        PreLabelData.builder().status(LabelStatus.ATTACHED).tcl("TC87654321").id(10).build();
    return Optional.of(preLabelData);
  }

  private ReceivingCounter getDefaultCounter() {
    ReceivingCounter receivingCounter = new ReceivingCounter();
    receivingCounter.setType(EndgameConstants.COUNTER_TYPE_TCL);
    receivingCounter.setCounterNumber(0);
    receivingCounter.setPrefixIndex('A');
    receivingCounter.setPrefix("TA");
    return receivingCounter;
  }

  @Test
  public void testGetLabelSummaryByDeliveryNumber() throws Exception {
    Long deliveryNumber = 12345678L;
    when(preLabelDataRepository.findByDeliveryNumber(anyLong(), any()))
        .thenReturn(preLabelDataPage());
    LabelSummary labelSummaryTcl = getLabelCount(33, LabelType.TCL);
    LabelSummary labelSummaryTpl = getLabelCount(65, LabelType.TPL);
    List<LabelSummary> labelSummaryList = Arrays.asList(labelSummaryTcl, labelSummaryTpl);
    when(preLabelDataCustomRepository.findLabelSummary(anyLong(), anyString()))
        .thenReturn(labelSummaryList);
    doReturn(null)
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/endgame/labels/"
                        + deliveryNumber
                        + "?deliveryStatus="
                        + DeliveryStatus.COMPLETE.toString()
                        + "&label-type=ALL"
                        + "&curr-index=1"
                        + "&page-size=10")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
    Mockito.verify(deliveryStatusPublisher, Mockito.times(0))
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
  }

  @Test
  public void testGetLabelSummaryByDeliveryNbrAndLabelType() throws Exception {
    Long deliveryNumber = 12345678L;
    when(preLabelDataRepository.findByDeliveryNumberAndType(anyLong(), any(LabelType.class), any()))
        .thenReturn(preLabelDataPage());
    LabelSummary labelSummaryTcl = getLabelCount(33, LabelType.TCL);
    LabelSummary labelSummaryTpl = getLabelCount(65, LabelType.TPL);
    List<LabelSummary> labelSummaryList = Arrays.asList(labelSummaryTcl, labelSummaryTpl);
    when(preLabelDataCustomRepository.findLabelSummary(anyLong(), anyString()))
        .thenReturn(labelSummaryList);
    doReturn(null)
        .when(deliveryStatusPublisher)
        .publishDeliveryStatus(anyLong(), anyString(), anyList(), anyMap());
    mockMvc
        .perform(
            MockMvcRequestBuilders.get(
                    "/endgame/labels/"
                        + deliveryNumber
                        + "?deliveryStatus="
                        + DeliveryStatus.ARV.toString()
                        + "&label-type=TCL"
                        + "&curr-index=1"
                        + "&page-size=10")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers))
        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
    Mockito.verify(deliveryStatusPublisher, Mockito.times(1))
        .publishDeliveryStatus(anyLong(), anyString(), any(), anyMap());
  }

  private Page<PreLabelData> preLabelDataPage() {
    List<PreLabelData> preLabelData = new ArrayList<>();
    preLabelData.add(
        PreLabelData.builder()
            .deliveryNumber(12345678L)
            .reason(null)
            .type(LabelType.TCL)
            .status(LabelStatus.SENT)
            .id(876452l)
            .tcl("TA00870795")
            .build());
    Page<PreLabelData> page = new PageImpl<>(preLabelData);
    return page;
  }

  private LabelSummary getLabelCount(long count, LabelType labelType) {
    LabelSummary labelSummary = new LabelSummary();
    labelSummary.setCount(count);
    labelSummary.setType(labelType);
    return labelSummary;
  }
}
