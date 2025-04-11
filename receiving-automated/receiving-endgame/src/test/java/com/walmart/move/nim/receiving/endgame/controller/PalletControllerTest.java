package com.walmart.move.nim.receiving.endgame.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.LabelType;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockPalletReceiveContainer;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.model.SlotMoveType;
import com.walmart.move.nim.receiving.endgame.repositories.PreLabelDataRepository;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameSlottingService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/** Test class for pallet receiving v2 API */
public class PalletControllerTest extends ReceivingControllerTestBase {

  private static final String TEST_TPL = "PQ00000257";

  @InjectMocks private PalletController palletController;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  private MockMvc mockMvc;
  private Gson gson;

  @Mock EndGameDeliveryService endGameDeliveryService;
  @Mock EndGameLabelingService endGameLabelingService;
  @Mock EndGameReceivingService endGameReceivingService;
  @Mock EndGameSlottingService endGameSlottingService;
  @Mock private ResourceBundleMessageSource resourceBundleMessageSource;

  @Mock PreLabelDataRepository preLabelDataRepository;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    gson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create();
    ReflectionTestUtils.setField(palletController, "gson", gson);
    ReflectionTestUtils.setField(endGameDeliveryService, "gson", gson);
    ReflectionTestUtils.setField(endGameLabelingService, "gson", gson);
    ReflectionTestUtils.setField(endGameReceivingService, "gson", gson);

    mockMvc =
        MockMvcBuilders.standaloneSetup(palletController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();
  }

  @BeforeMethod
  public void setUp() {
    reset(endGameDeliveryService);
    reset(endGameLabelingService);
    reset(endGameReceivingService);
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Check the pallet receive v2 api status for direct
   * upc scan flow
   *
   * @throws Exception thrown if mock POST api call fails
   */
  @Test
  public void testMultiplePalletReceive_UpcScan() throws Exception {

    // create sample payload for pallet receive v2 api
    MultiplePalletReceivingRequest multiplePalletReceivingRequest =
        MockPalletReceiveContainer.createMultiplePalletReceiveRequest();
    ContainerItem containerItem =
        multiplePalletReceivingRequest.getContainers().get(0).getContainerItems().get(0);

    doNothing()
        .when(endGameDeliveryService)
        .publishNonSortWorkingEvent(any(), isA(DeliveryStatus.class));
    when(endGameReceivingService.retrieveContainerItemFromContainer(isA(ContainerDTO.class)))
        .thenReturn(containerItem);
    PalletSlotResponse palletSlotResponse = getPalletResponse();
    palletSlotResponse.setLocations(palletSlotResponse.getLocations());

    when(endGameReceivingService.getSlotLocations(anyList(), any(ExtraAttributes.class)))
        .thenReturn(palletSlotResponse);
    when(endGameLabelingService.generateTCL(1, LabelType.TPL))
        .thenReturn(new HashSet<>(Collections.singleton(TEST_TPL)));
    doNothing().when(endGameReceivingService).verifyContainerReceivable(anyString());
    when(endGameReceivingService.receiveMultiplePallets(
            multiplePalletReceivingRequest.getContainers(), new PalletSlotResponse(), "PO"))
        .thenReturn(LabelResponse.builder().build());

    when(endGameLabelingService.saveLabels(anyList())).thenReturn(getPreLabelData());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("54321", "US");
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/pallet/v2")
                .content(gson.toJson(multiplePalletReceivingRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isCreated());
  }

  /**
   * https://jira.walmart.com/browse/SCTNGMS-29 - Check the pallet receive v2 api status for tpl
   * scan flow
   *
   * @throws Exception thrown if mock POST api call fails
   */
  @Test
  public void testMultiplePalletReceive_TplScan() throws Exception {

    // create sample payload for pallet receive v2 api
    MultiplePalletReceivingRequest multiplePalletReceivingRequest =
        MockPalletReceiveContainer.createMultiplePalletReceiveRequestForTplScan();
    ContainerItem containerItem =
        multiplePalletReceivingRequest.getContainers().get(0).getContainerItems().get(0);

    doNothing()
        .when(endGameDeliveryService)
        .publishNonSortWorkingEvent(any(), isA(DeliveryStatus.class));
    when(endGameReceivingService.retrieveContainerItemFromContainer(isA(ContainerDTO.class)))
        .thenReturn(containerItem);

    PalletSlotResponse palletSlotResponse = getPalletResponse();
    palletSlotResponse.setLocations(palletSlotResponse.getLocations());
    when(endGameReceivingService.getSlotLocations(anyList(), any(ExtraAttributes.class)))
        .thenReturn(palletSlotResponse);

    when(endGameLabelingService.generateTCL(1, LabelType.TPL))
        .thenReturn(new HashSet<>(Collections.singleton(TEST_TPL)));
    doNothing().when(endGameReceivingService).verifyContainerReceivable(anyString());
    when(endGameReceivingService.receiveMultiplePallets(
            multiplePalletReceivingRequest.getContainers(), new PalletSlotResponse(), "PO"))
        .thenReturn(LabelResponse.builder().build());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders("54321", "US");
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/pallet/v2")
                .content(gson.toJson(multiplePalletReceivingRequest))
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isCreated());
  }

  private PalletSlotResponse getPalletResponse() {
    PalletSlotResponse palletSlotResponse = new PalletSlotResponse();
    palletSlotResponse.setMessageId("23232adfda");
    SlotLocation slotLocation = new SlotLocation();
    slotLocation.setContainerTrackingId(TEST_TPL);
    slotLocation.setLocation("test");
    slotLocation.setType("test");
    slotLocation.setMoveRequired(true);
    slotLocation.setMoveType(SlotMoveType.HAUL);
    slotLocation.setInventoryTags(Arrays.asList(ReceivingConstants.CONTAINER_TAG_HOLD_FOR_SALE));
    palletSlotResponse.setLocations(Arrays.asList(slotLocation));
    return palletSlotResponse;
  }

  private List<PreLabelData> getPreLabelData() {
    List<PreLabelData> preLabelDataList = new ArrayList<>();
    PreLabelData labelData = new PreLabelData();
    labelData.setType(LabelType.TPL);
    labelData.setStatus(LabelStatus.SCANNED);
    labelData.setDeliveryNumber(635468);
    preLabelDataList.add(labelData);
    return preLabelDataList;
  }
}
