package com.walmart.move.nim.receiving.rdc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.LabelData;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rdc.config.RdcManagedConfig;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.rdc.mock.data.MockRdcInstruction;
import com.walmart.move.nim.receiving.rdc.model.MirageExceptionRequest;
import com.walmart.move.nim.receiving.rdc.model.MirageLpnExceptionErrorResponse;
import com.walmart.move.nim.receiving.rdc.model.wft.RdcInstructionType;
import com.walmart.move.nim.receiving.rdc.utils.RdcInstructionUtils;
import com.walmart.move.nim.receiving.rdc.utils.RdcReceivingUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RdcExceptionReceivingServiceTest {
  @InjectMocks private RdcExceptionReceivingService rdcExceptionReceivingService;

  @Mock RdcReceivingUtils rdcReceivingUtils;
  @Mock RdcInstructionUtils rdcInstructionUtils;
  @Mock RdcManagedConfig rdcManagedConfig;
  @Mock VendorBasedDeliveryDocumentsSearchHandler deliveryDocumentsSearchHandler;
  @Mock TenantSpecificConfigReader tenantSpecificConfigReader;
  @Mock private LabelDataService labelDataService;
  @Mock private InstructionService instructionService;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Mock private ContainerPersisterService containerPersisterService;
  @Mock private NimRdsService nimRdsService;

  private Gson gson;

  @BeforeClass
  public void init() {
    gson = new Gson();
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(32818);
    TenantContext.setFacilityCountryCode("us");
  }

  @AfterMethod
  public void resetMocks() {
    reset(
        tenantSpecificConfigReader,
        rdcInstructionUtils,
        rdcReceivingUtils,
        labelDataService,
        nimRdsService);
  }

  @Test
  public void testProcessExceptionFor16Digit_MatchFound() {
    String containerLabel = "7033123654789654";
    InstructionResponseImplException instructionResponse = new InstructionResponseImplException();
    List<Integer> preLabelSourceSites = Arrays.asList(7031, 7033, 7034, 7035, 9398, 9631, 9654);
    doReturn(preLabelSourceSites).when(rdcManagedConfig).getPreLabelFreightSourceSites();
    instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.processExceptionLabel(containerLabel);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(), "offlineLabelValidated");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
  }

  @Test
  public void testProcessExceptionFor18Digit_MatchFound() {
    String containerLabel = "058440106043008723";
    InstructionResponseImplException instructionResponse = new InstructionResponseImplException();
    List<Integer> preLabelSourceSites =
        Arrays.asList(7031, 7033, 7034, 7035, 9398, 9631, 9654, 6043);
    doReturn(preLabelSourceSites).when(rdcManagedConfig).getPreLabelFreightSourceSites();
    instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.processExceptionLabel(containerLabel);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(), "offlineLabelValidated");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
  }

  @Test
  public void testProcessExceptionFor16Digit_Invalid() {
    String containerLabel = "1230123654781230";
    InstructionResponseImplException instructionResponse = new InstructionResponseImplException();
    List<Integer> preLabelSourceSites = Arrays.asList(7031, 7033, 7034, 7035, 9398, 9631, 9654);
    doReturn(preLabelSourceSites).when(rdcManagedConfig).getPreLabelFreightSourceSites();
    try {
      instructionResponse =
          (InstructionResponseImplException)
              rdcExceptionReceivingService.processExceptionLabel(containerLabel);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-MIRAGE-INVALID-BARCODE-404");
      assertEquals(e.getDescription(), "Invalid barcode scanned, please scan the correct barcode");
    }
  }

  @Test
  public void testProcessExceptionForPreLabelFreight_25DigitOfflineLabel() {
    doReturn(new LabelData())
        .when(labelDataService)
        .findByTrackingIdAndLabelIn(anyString(), anyList());
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.processExceptionLabel("1234567890123456789012345");
    verify(labelDataService, times(1)).findByTrackingIdAndLabelIn(anyString(), anyList());
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(),
        RdcInstructionType.LABEL_VALIDATED.getInstructionCode());
  }

  @Test
  public void testProcessExceptionForPreLabelFreight_25DigitNotAnOfflineLabel() {
    InstructionResponseImplException instructionResponse = new InstructionResponseImplException();
    doReturn(null).when(labelDataService).findByTrackingIdAndLabelIn(anyString(), anyList());
    instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.processExceptionLabel("1234567890123456789012345");
    verify(labelDataService, times(1)).findByTrackingIdAndLabelIn(anyString(), anyList());
    assertNull(instructionResponse.getInstruction());
  }

  @Test
  public void testFetchDeliveryDocuments_Success() throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(mockDeliveryDocuments);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .updateAdditionalItemDetailsFromGDM(mockDeliveryDocuments);
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .overridePackTypeCodeForBreakPackItem(any(DeliveryDocumentLine.class));
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        Boolean.FALSE.booleanValue());
  }

  @Test
  public void testFetchDeliveryDocuments_Success_IQSIntegrationDisabled()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(mockDeliveryDocuments);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .updateAdditionalItemDetailsFromGDM(mockDeliveryDocuments);
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .overridePackTypeCodeForBreakPackItem(any(DeliveryDocumentLine.class));
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        Boolean.FALSE.booleanValue());
  }

  @Test
  public void testFetchDeliveryDocuments_InvalidPackTypeHandlingCode()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    ItemData itemData = new ItemData();
    itemData.setPackTypeCode("C");
    itemData.setHandlingCode("I");
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setAdditionalInfo(itemData);
    mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setVendorPack(2);
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doCallRealMethod().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(mockDeliveryDocuments);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .updateAdditionalItemDetailsFromGDM(mockDeliveryDocuments);
    doCallRealMethod().when(rdcInstructionUtils).updateAdditionalItemDetailsFromGDM(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doCallRealMethod()
        .when(rdcReceivingUtils)
        .overridePackTypeCodeForBreakPackItem(any(DeliveryDocumentLine.class));
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .isAtlasConvertedItem(),
        Boolean.FALSE.booleanValue());
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getPackTypeCode(),
        "B");
    assertEquals(
        deliveryDocuments
            .get(0)
            .getDeliveryDocumentLines()
            .get(0)
            .getAdditionalInfo()
            .getHandlingCode(),
        "I");
  }

  @Test
  public void testFetchDeliveryDocuments_Success_SSTKDeliveryDocuments()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocumentByItemNumber(anyString(), anyInt(), any(HttpHeaders.class));
    doReturn(Collections.emptyList())
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .filterSSTKDeliveryDocuments(anyList());
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsFromGDM(
            receiveExceptionRequest, MockHttpHeaders.getHeaders());
    assertEquals(deliveryDocuments, Collections.emptyList());
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
  }

  @Test
  public void testParseLpnExceptionErrorResponse_MatchFound() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"Match Found\",\n"
            + "    \"message\": \"Place case onto conveyor with label\",\n"
            + "    \"code\": \"MATCH_FOUND\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(), "offlineLabelValidated");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testParseLpnExceptionErrorResponse_InvalidBarcode() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"Invalid Barcode\",\n"
            + "    \"message\": \"Invalid barcode scanned, please scan the correct barcode\",\n"
            + "    \"code\": \"ERROR_INVALID_BARCODE\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    try {
      InstructionResponseImplException instructionResponse =
          (InstructionResponseImplException)
              rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                  receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    } catch (ReceivingBadDataException rdbe) {
      assertEquals(rdbe.getDescription(), ReceivingConstants.MIRAGE_INVALID_BARCODE_ERROR_MSG);
      assertEquals(rdbe.getErrorCode(), ExceptionCodes.MIRAGE_EXCEPTION_ERROR_INVALID_BARCODE);
    }
  }

  @Test
  public void testParseLpnExceptionErrorResponse_LabelValidated() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"Label Validated\",\n"
            + "    \"message\": \"Place case onto conveyor with label\",\n"
            + "    \"code\": \"LABEL_VALIDATED\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(), "offlineLabelValidated");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testParseLpnExceptionErrorResponse_LpnReceived() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"LPN Received\",\n"
            + "    \"message\": \"Place case onto conveyor with label\",\n"
            + "    \"code\": \"LPN_RECEIVED\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "labelReceived");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testParseLpnExceptionErrorResponse_LabelBackedOut() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"Label backed-out\",\n"
            + "    \"message\": \"Remove Label then place case onto conveyor without Label\",\n"
            + "    \"code\": \"ERROR_LPN_BACKOUT\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "labelBackedOut");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Remove existing label");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testParseLpnExceptionErrorResponse_Error_LpnNotFound() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage(null);
    String response =
        "{\n"
            + "    \"title\": \"Lpn not found\",\n"
            + "    \"message\": \"Remove the label from the case. Then re-induct the case with no label\",\n"
            + "    \"code\": \"ERROR_LPN_NOT_FOUND\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(
        instructionResponse.getInstruction().getInstructionCode(), "offlineLabelValidated");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Place case on conveyor");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testParseLpnExceptionErrorResponse_Exception_LpnNotFound() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage("HOST_LATE");
    String response =
        "{\n"
            + "    \"title\": \"Lpn not found\",\n"
            + "    \"message\": \"Remove the label from the case. Then re-induct the case with no label\",\n"
            + "    \"code\": \"ERROR_LPN_NOT_FOUND\"\n"
            + "}";
    MirageLpnExceptionErrorResponse mirageLpnExceptionErrorResponse =
        gson.fromJson(response, MirageLpnExceptionErrorResponse.class);
    InstructionResponseImplException instructionResponse =
        (InstructionResponseImplException)
            rdcExceptionReceivingService.parseMirageExceptionErrorResponse(
                receiveExceptionRequest, mirageLpnExceptionErrorResponse);
    assertEquals(instructionResponse.getInstruction().getInstructionCode(), "lpnNotFound");
    assertEquals(
        instructionResponse.getExceptionInstructionMsg().getTitle(), "Remove existing label");
    assertNotNull(instructionResponse);
  }

  @Test
  public void testGetInstructionRequest_Success() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    receiveExceptionRequest.setMessageId("messageId");
    receiveExceptionRequest.setDoorNumber("120");
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(2312);
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPEN);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    InstructionRequest instructionRequest =
        rdcExceptionReceivingService.getInstructionRequest(
            receiveExceptionRequest, deliveryDocuments);
    Assert.assertNotNull(instructionRequest);
    Assert.assertEquals(receiveExceptionRequest.getMessageId(), instructionRequest.getMessageId());
    Assert.assertEquals(
        receiveExceptionRequest.getDoorNumber(), instructionRequest.getDoorNumber());
    Assert.assertEquals(
        receiveExceptionRequest.isVendorComplianceValidated(),
        instructionRequest.isVendorComplianceValidated());
    Assert.assertEquals(
        String.valueOf(deliveryDocument.getDeliveryNumber()),
        instructionRequest.getDeliveryNumber());
    Assert.assertEquals(
        String.valueOf(deliveryDocument.getDeliveryStatus()),
        instructionRequest.getDeliveryStatus());
  }

  @Test
  public void testFetchDeliveryDocumentsWithUpcAndMultipleDeliveries_Success()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMulipleDADifferentDelivery();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    InstructionRequest instructionRequest = getInstructionRequest(receiveExceptionRequest, null);
    doReturn(true)
        .when(tenantSpecificConfigReader)
        .getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.IS_IQS_INTEGRATION_ENABLED), anyBoolean());
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    doCallRealMethod().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(mockDeliveryDocuments);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .updateAdditionalItemDetailsFromGDM(mockDeliveryDocuments);
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            receiveExceptionRequest.getDeliveryNumbers(),
            instructionRequest,
            MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(2))
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1)).getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    verify(rdcReceivingUtils, times(1)).updateQuantitiesBasedOnUOM(anyList());
    verify(rdcInstructionUtils, times(1)).updateAdditionalItemDetailsFromGDM(anyList());
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    assertEquals(deliveryDocuments.size(), 2);
  }

  @Test
  public void
      testFetchDeliveryDocumentsWithUpcAndMultipleDeliveries_Success_IQSIntegrationDisabled()
          throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForMulipleDADifferentDelivery();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    InstructionRequest instructionRequest = getInstructionRequest(receiveExceptionRequest, null);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    doCallRealMethod().when(rdcReceivingUtils).updateQuantitiesBasedOnUOM(mockDeliveryDocuments);
    doCallRealMethod()
        .when(rdcInstructionUtils)
        .updateAdditionalItemDetailsFromGDM(mockDeliveryDocuments);
    doNothing()
        .when(rdcInstructionUtils)
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            receiveExceptionRequest.getDeliveryNumbers(),
            instructionRequest,
            MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(2))
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1)).getDADeliveryDocumentsFromGDMDeliveryDocuments(anyList());
    verify(rdcReceivingUtils, times(1)).updateQuantitiesBasedOnUOM(anyList());
    verify(nimRdsService, times(1)).updateAdditionalItemDetails(anyList(), any(HttpHeaders.class));
    verify(rdcInstructionUtils, times(1))
        .populateOpenAndReceivedQtyInDeliveryDocuments(
            anyList(), any(HttpHeaders.class), anyString());
    assertEquals(deliveryDocuments.size(), 2);
  }

  @Test
  public void testFetchDeliveryDocumentsWithUpcAndMultipleDeliveries_hasSSTKDocuments()
      throws IOException, ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    receiveExceptionRequest.setDeliveryNumbers(Collections.singletonList("39403397"));
    InstructionRequest instructionRequest = getInstructionRequest(receiveExceptionRequest, null);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    when(tenantSpecificConfigReader.getConfiguredFeatureFlag(
            anyString(), eq(ReceivingConstants.RDC_SSTK_LABEL_GENERATION_ENABLED), anyBoolean()))
        .thenReturn(true);
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    doReturn(mockDeliveryDocuments)
        .when(rdcInstructionUtils)
        .filterSSTKDeliveryDocuments(anyList());
    List<DeliveryDocument> deliveryDocuments =
        rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
            receiveExceptionRequest.getDeliveryNumbers(),
            instructionRequest,
            MockHttpHeaders.getHeaders());
    verify(rdcInstructionUtils, times(1))
        .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
    verify(rdcInstructionUtils, times(1)).filterSSTKDeliveryDocuments(anyList());
    assertEquals(deliveryDocuments.size(), 0);
  }

  @Test
  public void testValidateBreakPack_returnsTrue_BreakPackConveyPicks() throws IOException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_BREAK_PACK_CONVEY_PICKS_ITEM_HANDLING_CODE);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode(RdcConstants.BREAK_PACK_TYPE_CODE);
    Boolean isBreakPack = rdcExceptionReceivingService.validateBreakPack(mockDeliveryDocuments);
    assertTrue(isBreakPack);
  }

  @Test
  public void testValidateBreakPack_returnsTrue_MasterBreakPack() throws IOException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDABreakPack();
    DeliveryDocumentLine deliveryDocumentLine =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setItemPackAndHandlingCode(RdcConstants.DA_CASE_PACK_NONCON_RTS_PUT_ITEM_HANDLING_CODE);
    deliveryDocumentLine
        .getAdditionalInfo()
        .setPackTypeCode(RdcConstants.MASTER_BREAK_PACK_TYPE_CODE);
    Boolean isBreakPack = rdcExceptionReceivingService.validateBreakPack(mockDeliveryDocuments);
    assertTrue(isBreakPack);
  }

  @Test
  public void testValidateBreakPack_returnsFalse() throws IOException {
    List<DeliveryDocument> mockDeliveryDocuments =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocumentLine deliveryDocumentLine =
        mockDeliveryDocuments.get(0).getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setPackTypeCode("C");
    Boolean isBreakPack = rdcExceptionReceivingService.validateBreakPack(mockDeliveryDocuments);
    assertFalse(isBreakPack);
  }

  @Test
  public void testFetchDeliveryDocumentsWithUpcAndMultipleDeliveries_UPC_Not_Found()
      throws ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments = new ArrayList<>();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    InstructionRequest instructionRequest = getInstructionRequest(receiveExceptionRequest, null);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    doReturn(mockDeliveryDocuments)
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    try {
      List<DeliveryDocument> deliveryDocuments =
          rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
              receiveExceptionRequest.getDeliveryNumbers(),
              instructionRequest,
              MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.UPC_MATCH_NOT_FOUND);
      assertEquals(
          e.getDescription(),
          String.format(UPC_MATCH_NOT_FOUND, instructionRequest.getUpcNumber()));
      verify(rdcInstructionUtils, times(0))
          .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
      verify(rdcReceivingUtils, times(0)).updateQuantitiesBasedOnUOM(anyList());
      verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
      verify(rdcInstructionUtils, times(0))
          .populateOpenAndReceivedQtyInDeliveryDocuments(
              anyList(), any(HttpHeaders.class), anyString());
    }
  }

  @Test
  public void testFetchDeliveryDocumentsWithUpcAndMultipleDeliveries_IgnoresItemNotFoundError()
      throws ReceivingException {
    List<DeliveryDocument> mockDeliveryDocuments = new ArrayList<>();
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequestForNoBarcodeSeen();
    InstructionRequest instructionRequest = getInstructionRequest(receiveExceptionRequest, null);
    doReturn(deliveryDocumentsSearchHandler)
        .when(tenantSpecificConfigReader)
        .getConfiguredInstance(anyString(), anyString(), eq(DeliveryDocumentsSearchHandler.class));
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(ReceivingException.ITEM_NOT_FOUND_ERROR)
            .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
            .build();
    doThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorResponse(errorResponse)
                .build())
        .when(deliveryDocumentsSearchHandler)
        .fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class));
    try {
      List<DeliveryDocument> deliveryDocuments =
          rdcExceptionReceivingService.fetchDeliveryDocumentsWithUpcAndMultipleDeliveries(
              receiveExceptionRequest.getDeliveryNumbers(),
              instructionRequest,
              MockHttpHeaders.getHeaders());
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.UPC_MATCH_NOT_FOUND);
      assertEquals(
          e.getDescription(),
          String.format(UPC_MATCH_NOT_FOUND, instructionRequest.getUpcNumber()));
      verify(rdcInstructionUtils, times(0))
          .validateAndProcessGdmDeliveryDocuments(anyList(), any(InstructionRequest.class));
      verify(rdcReceivingUtils, times(0)).updateQuantitiesBasedOnUOM(anyList());
      verify(rdcInstructionUtils, times(0)).updateAdditionalItemDetailsFromGDM(anyList());
      verify(rdcInstructionUtils, times(0))
          .populateOpenAndReceivedQtyInDeliveryDocuments(
              anyList(), any(HttpHeaders.class), anyString());
    }
  }

  @Test
  public void testGetMirageExceptionRequest_preLabeledFreight() {
    ReceiveExceptionRequest receiveExceptionRequest =
        getReceiveExceptionRequestForPreLabeledFreight();
    MirageExceptionRequest mirageExceptionRequest =
        rdcExceptionReceivingService.getMirageExceptionRequest(receiveExceptionRequest);
    assertEquals(mirageExceptionRequest.getLpn(), receiveExceptionRequest.getLpns().get(0));
    assertEquals(mirageExceptionRequest.getItemNbr(), null);
    assertEquals(mirageExceptionRequest.getAclErrorString(), null);
    assertEquals(mirageExceptionRequest.getTokenId(), null);
    assertEquals(mirageExceptionRequest.getPrinterNbr(), null);
    assertEquals(mirageExceptionRequest.getGroupNbr(), null);
  }

  @Test
  public void testGetMirageExceptionRequest() {
    ReceiveExceptionRequest receiveExceptionRequest = getReceiveExceptionRequest();
    MirageExceptionRequest mirageExceptionRequest =
        rdcExceptionReceivingService.getMirageExceptionRequest(receiveExceptionRequest);
    assertEquals(mirageExceptionRequest.getLpn(), receiveExceptionRequest.getLpns().get(0));
    assertEquals(
        mirageExceptionRequest.getItemNbr(),
        String.valueOf(receiveExceptionRequest.getItemNumber()));
    assertEquals(
        mirageExceptionRequest.getAclErrorString(), receiveExceptionRequest.getExceptionMessage());
    assertEquals(mirageExceptionRequest.getTokenId(), receiveExceptionRequest.getTokenId());
    assertEquals(
        mirageExceptionRequest.getPrinterNbr(), receiveExceptionRequest.getPrinterNumber());
    assertEquals(
        mirageExceptionRequest.getGroupNbr(), receiveExceptionRequest.getDeliveryNumbers());
  }

  @Test
  public void testGetPrintRequestPayLoadForShippingLabelWithNoDestination()
      throws ReceivingException {
    Container container = new Container();
    Map<String, Object> shippingLabel =
        rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
            container, "mockLocation", "mockUsername");
    assertTrue(shippingLabel.isEmpty());
  }

  @Test
  public void testGetPrintRequestPayLoadForShippingLabelWithNoInstruction()
      throws ReceivingException {
    Container container = new Container();
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "123");
    container.setDestination(destination);
    Map<String, Object> shippingLabel =
        rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
            container, "mockLocation", "mockUsername");
    assertTrue(shippingLabel.isEmpty());
  }

  @Test
  public void testGetPrintRequestPayLoadForShippingLabel_EmptyPrintRequest()
      throws ReceivingException {
    Container container = new Container();
    container.setTrackingId("d328990000000000000106509");
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "123");
    container.setDestination(destination);
    container.setInstructionId(123l);
    Instruction instruction = new Instruction();
    instruction.setId(123l);
    instruction.setContainer(MockRdcInstruction.getContainerDetails());
    when(instructionService.getInstructionById(anyLong())).thenReturn(instruction);
    rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
        container, "mockLocation", "mockUsername");
  }

  @Test
  public void testGetPrintRequestPayLoadForShippingLabel() throws ReceivingException {
    Container container = new Container();
    container.setTrackingId("d328990000000000000106509");
    Map<String, String> destination = new HashMap<>();
    destination.put(ReceivingConstants.BU_NUMBER, "123");
    container.setDestination(destination);
    container.setInstructionId(123l);
    Instruction instruction = getInstruction();
    when(instructionService.getInstructionById(anyLong())).thenReturn(instruction);
    rdcExceptionReceivingService.getPrintRequestPayLoadForShippingLabel(
        container, "mockLocation", "mockUsername");
    verify(instructionPersisterService, times(1)).saveInstruction(any(Instruction.class));
    verify(containerPersisterService, times(1)).saveContainer(any(Container.class));
  }

  public Instruction getInstruction() {
    Instruction instruction = new Instruction();
    ContainerDetails containerDetails = new ContainerDetails();
    Map<String, Object> containerLabel = MockRdcInstruction.getContainerDetails().getCtrLabel();

    Map<String, Object> labelData = new HashMap<>();
    labelData.put("key", "STORE");
    labelData.put("value", "RL");
    List<Map<String, Object>> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "d328990000000000000106509");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList =
        (List<Map<String, Object>>) containerLabel.get(PRINT_REQUEST_KEY);
    printRequestList.add(printRequest);
    containerDetails.setCtrLabel(containerLabel);
    instruction.setId(123l);
    instruction.setContainer(containerDetails);
    return instruction;
  }

  private ReceiveExceptionRequest getReceiveExceptionRequest() {
    ReceiveExceptionRequest receiveExceptionRequest = new ReceiveExceptionRequest();
    receiveExceptionRequest.setExceptionMessage("Error");
    receiveExceptionRequest.setReceiver("Receiver");
    receiveExceptionRequest.setLpns(Collections.singletonList("1234567890"));
    receiveExceptionRequest.setItemNumber(550000000);
    receiveExceptionRequest.setSlot("R8000");
    receiveExceptionRequest.setDeliveryNumbers(Collections.singletonList("345123"));
    return receiveExceptionRequest;
  }

  public InstructionRequest getInstructionRequest(
      ReceiveExceptionRequest receiveExceptionRequest, List<DeliveryDocument> deliveryDocuments) {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId(receiveExceptionRequest.getMessageId());
    instructionRequest.setDeliveryNumber(receiveExceptionRequest.getDeliveryNumbers().get(0));
    instructionRequest.setDoorNumber(receiveExceptionRequest.getDoorNumber());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    if (Objects.nonNull(receiveExceptionRequest.getUpcNumber())) {
      instructionRequest.setUpcNumber(receiveExceptionRequest.getUpcNumber());
      instructionRequest.setReceivingType(ReceivingConstants.UPC);
    }
    return instructionRequest;
  }

  private ReceiveExceptionRequest getReceiveExceptionRequestForNoBarcodeSeen() {
    return ReceiveExceptionRequest.builder()
        .exceptionMessage("NO_BARCODE_SEEN")
        .lpns(Collections.singletonList("1234567890"))
        .slot("R8000")
        .deliveryNumbers(Arrays.asList("39403397", "20085744"))
        .tokenId("12345")
        .upcNumber("12345943012345")
        .build();
  }

  private ReceiveExceptionRequest getReceiveExceptionRequestForPreLabeledFreight() {
    return ReceiveExceptionRequest.builder().lpns(Collections.singletonList("1234567890")).build();
  }
}
