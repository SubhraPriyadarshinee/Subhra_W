package com.walmart.move.nim.receiving.endgame.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.AUDIT_V2_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingControllerTestBase;
import com.walmart.move.nim.receiving.core.advice.RestResponseExceptionHandler;
import com.walmart.move.nim.receiving.core.common.DeliveryDocumentHelper;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.message.service.EventProcessor;
import com.walmart.move.nim.receiving.core.model.gdm.v2.DeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerTag;
import com.walmart.move.nim.receiving.core.service.DeliveryMetaDataService;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.endgame.constants.LabelStatus;
import com.walmart.move.nim.receiving.endgame.entity.PreLabelData;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.mock.data.MockContainer;
import com.walmart.move.nim.receiving.endgame.mock.data.MockMessageData;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.endgame.model.ReceivingRequest;
import com.walmart.move.nim.receiving.endgame.service.EndGameDeliveryService;
import com.walmart.move.nim.receiving.endgame.service.EndGameLabelingService;
import com.walmart.move.nim.receiving.endgame.service.EndGameReceivingService;
import com.walmart.move.nim.receiving.utils.common.MessageData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.mockito.*;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@PropertySource("classpath:application.properties")
public class TCLControllerTest extends ReceivingControllerTestBase {

  private static final String TEST_TCL = "TC00000001";

  private MockMvc mockMvc;

  @InjectMocks private TCLController tclController;
  @Spy @InjectMocks private RestResponseExceptionHandler restResponseExceptionHandler;

  @Mock private EndGameLabelingService endGameLabelingService;
  @Mock private EndGameReceivingService endGameReceivingService;
  @Mock private EndGameDeliveryService endGameDeliveryService;
  @Mock private InstructionHelperService instructionHelperService;
  @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private DeliveryDocumentHelper deliveryDocumentHelper;
  @Mock private DeliveryService deliveryService;
  @Mock private EventProcessor eventProcessor;
  @Mock private ResourceBundleMessageSource resourceBundleMessageSource;
  @Mock private DeliveryMetaDataService deliveryMetaDataService;

  private ReceivingRequest receivingRequest;
  private Gson gson;
  private DeliveryDetails deliveryDetails;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(9610);

    this.mockMvc =
        MockMvcBuilders.standaloneSetup(tclController)
            .setControllerAdvice(restResponseExceptionHandler)
            .build();

    gson = new Gson();
    receivingRequest = new ReceivingRequest();
    receivingRequest.setTrailerCaseLabel("TC00000001");
    receivingRequest.setCaseUPC("00049807100011");
    List<ContainerTag> containerTagList = new ArrayList<>();
    ContainerTag containerTags = new ContainerTag();
    containerTags.setAction("SET");
    containerTags.setTag("HOLD_FOR_SALE");
    containerTagList.add(containerTags);
    receivingRequest.setQuantity(1);
    receivingRequest.setQuantityUOM("ZA");
    receivingRequest.setIsMultiSKU(false);
    receivingRequest.setContainerTagList(containerTagList);
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    ItemDetails itemDetails = new ItemDetails();
    itemDetails.setNumber(134567L);
    purchaseOrderLine.setItemDetails(itemDetails);
    PurchaseOrder purchaseOrder = new PurchaseOrder();
    purchaseOrder.setLines(Collections.singletonList(purchaseOrderLine));
    receivingRequest.setPurchaseOrder(purchaseOrder);
  }

  @BeforeMethod
  public void reset() {
    Mockito.reset(endGameLabelingService);
    Mockito.reset(endGameReceivingService);
    Mockito.reset(deliveryService);
    Mockito.reset(deliveryDocumentHelper);
    Mockito.reset(instructionHelperService);
    Mockito.reset(tenantSpecificConfigReader);
    deliveryDetails = new DeliveryDetails();
    deliveryDetails.setDeliveryStatus(DeliveryStatus.ARV.name());
    deliveryDetails.setDeliveryLegacyStatus(DeliveryStatus.ARV.name());
  }

  @Test
  public void testGetTCLStatus() throws Exception {
    when(endGameLabelingService.findByTcl(TEST_TCL)).thenReturn(getLabelDetails());

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    String response =
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/endgame/tcls/status/" + TEST_TCL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(httpHeaders))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(response, LabelStatus.ATTACHED.getStatus());
  }

  private Optional<PreLabelData> getNullObject() {
    return Optional.empty();
  }

  private Optional<PreLabelData> getLabelDetails() {
    PreLabelData preLabelData =
        PreLabelData.builder().tcl(TEST_TCL).status(LabelStatus.ATTACHED).build();
    preLabelData.setCaseUpc("00049807100011");
    preLabelData.setDiverAckEvent(gson.toJson(getScanEventData()));
    return Optional.of(preLabelData);
  }

  private ScanEventData getScanEventData() {
    ScanEventData scanEventData = new ScanEventData();
    scanEventData.setWeight(10.00);
    scanEventData.setWeightUnitOfMeasure("LB");
    return scanEventData;
  }

  @Test
  public void getTCLDetails() throws Exception {
    when(endGameLabelingService.findByTcl(TEST_TCL)).thenReturn(getLabelDetails());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.get("/endgame/tcls/" + TEST_TCL)
                .contentType(MediaType.APPLICATION_JSON)
                .headers(httpHeaders))
        .andExpect(status().isOk());

    verify(deliveryDocumentHelper, never()).getUrlForFetchingDelivery(anyLong());
    verify(deliveryService, never()).getDeliveryDetails(anyString(), anyLong());
    verify(instructionHelperService, never())
        .reopenDeliveryIfNeeded(anyLong(), anyString(), any(), anyString());
  }

  @Test
  public void testReceiveTCL() throws Exception {
    when(tenantSpecificConfigReader.isFeatureFlagEnabled(AUDIT_V2_ENABLED)).thenReturn(true);
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(getLabelDetails());
    when(deliveryMetaDataService.updateAuditInfoInDeliveryMetaData(anyList(), anyInt(), anyLong()))
        .thenReturn(true);
    when(endGameReceivingService.receiveVendorPack(any()))
        .thenReturn(ReceiveVendorPack.builder().container(MockContainer.getContainer()).build());
    doNothing().when(endGameDeliveryService).publishWorkingEventIfApplicable(anyLong());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/tcls/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receivingRequest))
                .headers(httpHeaders))
        .andExpect(status().isCreated());
  }

  @Test
  public void testTCLpresent() throws Exception {
    when(endGameLabelingService.findByTcl(anyString())).thenReturn(getLabelDetails());
    doNothing().when(endGameDeliveryService).publishWorkingEventIfApplicable(anyLong());
    when(endGameReceivingService.receiveVendorPack(any()))
        .thenThrow(
            new ReceivingConflictException(
                ExceptionCodes.CONTAINER_ALREADY_EXISTS,
                "Container already exists for this TCL:" + "TC00000001"));

    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/tcls/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(receivingRequest))
                .headers(httpHeaders))
        .andExpect(status().isConflict());
  }

  @Test
  public void testReceiveTCLScan() throws Exception {
    MessageData messageData = MockMessageData.getMockReceivingRequestDataForQA();
    doNothing().when(eventProcessor).processEvent(any());
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/tcls/auto/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(messageData))
                .headers(httpHeaders))
        .andExpect(status().isOk());
  }

  @Test
  public void testReceiveTCLScanWithFailure() throws Exception {
    ReceivingRequest messageData = MockMessageData.getScanEventDataWithDimensions();
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    doThrow(new ReceivingException(ExceptionCodes.UNABLE_TO_CREATE_CONTAINER))
        .when(eventProcessor)
        .processEvent(any(ScanEventData.class));
    mockMvc
        .perform(
            MockMvcRequestBuilders.post("/endgame/tcls/auto/receive")
                .contentType(MediaType.APPLICATION_JSON)
                .content(gson.toJson(messageData))
                .headers(httpHeaders))
        .andExpect(status().is5xxServerError());
  }
}
