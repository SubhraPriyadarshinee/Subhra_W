package com.walmart.move.nim.receiving.wfs.service;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.fail;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.GDMServiceUnavailableException;
import com.walmart.move.nim.receiving.core.common.exception.GdmError;
import com.walmart.move.nim.receiving.core.common.exception.GdmErrorCode;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceivingType;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.service.DeliveryService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSTwoDBarcodeScanTypeDocumentSearchHandlerTest {
  @InjectMocks
  private WFSTwoDBarcodeScanTypeDocumentSearchHandler wfsTwoDBarcodeScanTypeDocumentSearchHandler;

  @Mock private DeliveryService deliveryServiceImpl;
  @Mock private TenantSpecificConfigReader configUtils;
  @Mock private RestConnector restConnector;
  @Mock private AppConfig appConfig;
  @Spy private WFSTclFreeHandler tclFreeHandler;

  @BeforeClass
  public void initMocks() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @AfterMethod
  public void resetMocks() {
    reset(configUtils);
    reset(deliveryServiceImpl);
    reset(restConnector);
    reset(appConfig);
  }

  @BeforeMethod
  public void initPrivateMocks() {
    ReflectionTestUtils.setField(tclFreeHandler, "configUtils", configUtils);
    ReflectionTestUtils.setField(tclFreeHandler, "deliveryServiceImpl", deliveryServiceImpl);
  }

  public List<ScannedData> getScannedDataList() {
    List<ScannedData> scannedDataList = new ArrayList();

    ScannedData scannedData1 = new ScannedData();
    scannedData1.setKey("GTIN");
    scannedData1.setApplicationIdentifier("01");
    scannedData1.setValue("00815489023378"); // GTIN == UpcNumber

    ScannedData scannedData2 = new ScannedData();
    scannedData2.setKey("PO");
    scannedData2.setApplicationIdentifier("400");
    scannedData2.setValue("7868521124");

    scannedDataList.add(scannedData1);
    scannedDataList.add(scannedData2);
    return scannedDataList;
  }

  public List<String> getPreviouslyScannedDataList() {
    String upcNumber = "00815489023378";
    return Collections.singletonList(upcNumber);
  }

  public InstructionRequest getInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    Map<String, Object> mp = new HashMap<String, Object>();
    mp.put("PO", "");
    instructionRequest.setMessageId(UUID.randomUUID().toString());
    instructionRequest.setDeliveryNumber("891100");
    instructionRequest.setDeliveryStatus("SCH");
    instructionRequest.setUpcNumber("00815489023378"); // UpcNumber == GTIN
    instructionRequest.setScannedDataList(getScannedDataList());
    instructionRequest.setReceivingType(ReceivingType.UPC.getReceivingType());
    instructionRequest.setPreviouslyScannedDataList(getPreviouslyScannedDataList());
    instructionRequest.setAdditionalParams(mp);
    return instructionRequest;
  }

  private String getJSONStringResponse(String path) {
    String payload = null;
    try {
      String filePath = new File(path).getCanonicalPath();
      payload = new String(Files.readAllBytes(Paths.get(filePath)));
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (Objects.nonNull(payload)) {
      return payload;
    }
    return null;
  }

  @Test
  public void testFetchDeliveryDocument_via_UPC() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    String dataPath = "./src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 1);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "7868521124");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
  }

  @Test
  public void testFetchDeliveryDocument_via_UPC_TCL_Free_RunTime_exception()
      throws ReceivingException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setDeliveryNumber("0");
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    ReflectionTestUtils.setField(tclFreeHandler, "configUtils", configUtils);
    ReflectionTestUtils.setField(tclFreeHandler, "deliveryServiceImpl", deliveryServiceImpl);
    when(configUtils.getConfiguredFeatureFlag(anyString(), anyString())).thenReturn(true);
    when(deliveryServiceImpl.fetchDeliveriesByStatusUpcAndPoNumber(
            anyList(), anyString(), anyString(), anyInt(), anyList()))
        .thenThrow(new ReceivingException("Not Valid"));
    Assertions.assertThrows(
        ReceivingException.class,
        () ->
            wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
                instructionRequest, mockHttpHeaders));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_via_UPC_throws_BAD_DATA_Exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenThrow(
            new GDMServiceUnavailableException(
                gdmError.getErrorMessage(), gdmError.getErrorCode(), gdmError.getErrorHeader()));
    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testFetchDeliveryDocument_via_UPC_throws_ITEM_NOT_FOUND_Exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(gdmError.getErrorMessage())
            .errorCode(gdmError.getErrorCode())
            .errorHeader(gdmError.getLocalisedErrorHeader())
            .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
            .build();
    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorResponse(errorResponse)
                .build());

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);
  }

  @Test
  public void testFetchDeliveryDocument_via_GS1() throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 1);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "7868521124");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testFetchDeliveryDocument_via_GS1_throws_ITEM_NOT_FOUND_Exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.ITEM_NOT_FOUND_ERROR);
    ErrorResponse errorResponse =
        ErrorResponse.builder()
            .errorMessage(gdmError.getErrorMessage())
            .errorCode(gdmError.getErrorCode())
            .errorHeader(gdmError.getLocalisedErrorHeader())
            .errorKey(ExceptionCodes.PO_LINE_NOT_FOUND)
            .build();
    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenThrow(
            ReceivingException.builder()
                .httpStatus(HttpStatus.NOT_FOUND)
                .errorResponse(errorResponse)
                .build());
    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void testFetchDeliveryDocument_via_GS1_throws_Bad_Data_Exception()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    GdmError gdmError = GdmErrorCode.getErrorValue(ReceivingException.GDM_NETWORK_ERROR);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenThrow(
            new GDMServiceUnavailableException(
                gdmError.getErrorMessage(), gdmError.getErrorCode(), gdmError.getErrorHeader()));

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);
  }

  @Test
  public void testFetchDeliveryDocument_via_upc_throws_PO_notFound()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    List<ScannedData> scannedData = getScannedDataList();
    scannedData.remove(1); // no po is set and hence can't filer documents by PO
    instructionRequest.setScannedDataList(scannedData);
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.NO_PO_FOUND);
      assertEquals(e.getDescription(), WFSConstants.WFS_TWO_D_BARCODE_PO_NOT_FOUND_ERROR_MSG);
    }
  }

  @Test
  public void testFetchDeliveryDocumentByUpc() {
    try {
      wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocumentByUpc(
          21119003, "00000943037204", MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }

  @Test(expectedExceptions = ReceivingDataNotFoundException.class)
  public void testInvalidPoScenario() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.get(1).setValue("01292394435");
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);
  }

  @Test()
  public void testInvalidPoScenario_withAssert() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());

    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.get(1).setValue("01292394435");
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (ReceivingDataNotFoundException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.NO_PO_FOUND);
      assertEquals(e.getDescription(), WFSConstants.WFS_TWO_D_BARCODE_PO_NOT_FOUND_ERROR_MSG);
    }
  }

  @Test
  public void testFetchDeliveryDocument_via_GS1_withEqualsIgnoreCase()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument2.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    List<ScannedData> scannedData = getScannedDataList();
    scannedData.get(1).setValue("1575317gdm");
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 2);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "1575317gdm");
    assertEquals(deliveryDocumentList.get(1).getPurchaseReferenceNumber(), "1575317GDM");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
  }

  @Test
  public void testAuditDetailsFlowHappyPath() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(restConnector.exchange(any(), any(), any(), eq(String.class))).thenReturn(responseEntity);
    when(deliveryServiceImpl.gmdRestCallResponse(any(), any(), any())).thenReturn(responseEntity);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 1);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "7868521124");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
  }

  @Test
  public void testAuditDetailsThrowsResourceAccessException() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(restConnector.exchange(any(), any(), any(), eq(String.class)))
        .thenThrow(new ResourceAccessException("Unable to Connect to URL"));

    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (Exception e) {

    }
  }

  @Test
  public void testAuditDetailsThrowsException() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(restConnector.exchange(any(), any(), any(), eq(String.class))).thenReturn(null);

    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (Exception e) {

    }
  }

  @Test
  public void testAuditDetailsWithNullScannedDataList() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    instructionRequest.setScannedDataList(null);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(restConnector.exchange(any(), any(), any(), eq(String.class))).thenReturn(null);

    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (Exception e) {

    }
  }

  @Test
  public void testAuditDetails_sscc18Absent() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(restConnector.exchange(any(), any(), any(), eq(String.class))).thenReturn(null);

    try {
      List<DeliveryDocument> deliveryDocumentList =
          wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
              instructionRequest, mockHttpHeaders);
    } catch (Exception e) {

    }
  }

  @Test
  public void testAuditDetailsFlow_WithAdditionalInfoInDeliveryDocs() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocumentPackInformation.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(restConnector.exchange(any(), any(), any(), eq(String.class))).thenReturn(responseEntity);
    when(deliveryServiceImpl.gmdRestCallResponse(any(), any(), any())).thenReturn(responseEntity);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 1);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "7868521124");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
  }

  @Test
  public void testAuditDetailsThrowsReceivingException() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(deliveryServiceImpl.gmdRestCallResponse(any(), any(), any()))
        .thenThrow(new ReceivingException("Unable to Connect to URL"));

    Assertions.assertEquals(
        wfsTwoDBarcodeScanTypeDocumentSearchHandler
            .fetchDeliveryDocument(instructionRequest, mockHttpHeaders)
            .size(),
        1);
  }

  @Test
  public void testAuditDetailsThrowsGDMServiceUnAvailableException() throws Exception {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    instructionRequest.setReceivingType(ReceivingType.GS1.getReceivingType());
    String dataPath = "src/test/resources/GDMDeliveryDocument.json";
    String dataPath2 = "src/test/resources/GDMPalletPackResponse.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);
    String gdmPalletPackResponseString = getJSONStringResponse(dataPath2);

    ScannedData scannedData3 = new ScannedData();
    scannedData3.setKey("SSCC");
    scannedData3.setApplicationIdentifier("00");
    scannedData3.setValue("086001043710000001");

    List<ScannedData> scannedData = getScannedDataList();
    scannedData.add(scannedData3);
    instructionRequest.setScannedDataList(scannedData);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);
    when(configUtils.isFeatureFlagEnabled(WFSConstants.IS_PACK_ID_BASED_RECEIVING_ENABLED))
        .thenReturn(true);
    ResponseEntity<String> responseEntity =
        new ResponseEntity<String>(gdmPalletPackResponseString, HttpStatus.OK);
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");

    when(deliveryServiceImpl.gmdRestCallResponse(any(), any(), any()))
        .thenThrow(new GDMServiceUnavailableException("GDM service unavailable!"));

    Assertions.assertEquals(
        wfsTwoDBarcodeScanTypeDocumentSearchHandler
            .fetchDeliveryDocument(instructionRequest, mockHttpHeaders)
            .size(),
        1);
  }

  @Test
  public void testFetchDeliveryDocument_via_UPC_shelfLPNReceiving()
      throws ReceivingException, IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    instructionRequest
        .getAdditionalParams()
        .put(ReceivingConstants.IS_RE_RECEIVING_LPN_FLOW, Boolean.TRUE);
    instructionRequest
        .getAdditionalParams()
        .put(ReceivingConstants.PURCHASE_ORDER_NUMBER, "7868521124");
    instructionRequest
        .getAdditionalParams()
        .put(WFSConstants.SHELF_LPN, "b040930000200000000342066");
    instructionRequest
        .getAdditionalParams()
        .put(
            ReceivingConstants.RE_RECEIVING_SHIPMENT_NUMBER,
            "ASN_0654216GDM_m040930000200000004897595");

    HttpHeaders mockHttpHeaders = MockHttpHeaders.getHeaders();
    String dataPath = "./src/test/resources/GDMDeliveryDocument.json";
    String gdmDeliveryDocumentResponseString = getJSONStringResponse(dataPath);

    when(deliveryServiceImpl.findDeliveryDocument(anyLong(), anyString(), any()))
        .thenReturn(gdmDeliveryDocumentResponseString);

    List<DeliveryDocument> deliveryDocumentList =
        wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocument(
            instructionRequest, mockHttpHeaders);

    Mockito.verify(deliveryServiceImpl, times(1))
        .findDeliveryDocument(anyLong(), anyString(), eq(mockHttpHeaders));
    assertEquals(deliveryDocumentList.size(), 1);
    assertEquals(deliveryDocumentList.get(0).getDeliveryNumber(), 891100);
    assertEquals(deliveryDocumentList.get(0).getPurchaseReferenceNumber(), "7868521124");
    assertEquals(deliveryDocumentList.get(0).getDeliveryDocumentLines().size(), 4);
    assertEquals(
        deliveryDocumentList.get(0).getAdditionalInfo().getShelfLPN(), "b040930000200000000342066");
    assertEquals(
        deliveryDocumentList.get(0).getAdditionalInfo().getReReceivingShipmentNumber(),
        "ASN_0654216GDM_m040930000200000004897595");
    assertFalse(deliveryDocumentList.get(0).getAdditionalInfo().getIsAuditRequired());
  }

  @Test
  public void testFetchDeliveryDocumentByItemNumber() {
    try {
      wfsTwoDBarcodeScanTypeDocumentSearchHandler.fetchDeliveryDocumentByItemNumber(
          "21119003", 943037204, MockHttpHeaders.getHeaders());
      fail();
    } catch (ReceivingException exc) {
      assertEquals(HttpStatus.NOT_IMPLEMENTED, exc.getHttpStatus());
      assertEquals(ReceivingException.NOT_IMPLEMENTED_EXCEPTION, exc.getMessage());
    }
  }
}
