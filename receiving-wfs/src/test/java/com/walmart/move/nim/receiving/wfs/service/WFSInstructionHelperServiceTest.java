package com.walmart.move.nim.receiving.wfs.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.PENDING_LPN;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.wfs.WFSTestUtils;
import com.walmart.move.nim.receiving.wfs.config.WFSManagedConfig;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import com.walmart.move.nim.receiving.wfs.mock.data.MockInstruction;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.mockito.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class WFSInstructionHelperServiceTest extends ReceivingTestBase {
  @Spy @InjectMocks public WFSInstructionHelperService wfsInstructionHelperService;
  @Mock public TenantSpecificConfigReader configUtils;
  @Mock private WFSManagedConfig wfsManagedConfig;
  @Mock private GDMRestApiClient gdmRestApiClient;
  @Mock private AppConfig appConfig;
  private Gson gson;
  private String fcNumberToNameMapping;
  private HttpHeaders headers = MockHttpHeaders.getHeaders();
  String inputGDMLpnDetailsResponseFilePath =
      "../receiving-test/src/main/resources/json/GdmLpnDetailsResponse.json";
  String inputGDMLpnDetailsResponseFilePath2 =
      "../receiving-test/src/main/resources/json/GdmLpnDetailsResponseWithContainerId.json";
  String inputPayloadWithoutDeliveryDocumentFilePath =
      "../receiving-test/src/main/resources/json/wfs/wfsInputPayloadWithoutDeliveryDocument.json";
  String inputGDMLpnDetailsResponse =
      WFSTestUtils.getJSONStringResponse(inputGDMLpnDetailsResponseFilePath);
  String inputGDMLpnDetailsResponse2 =
      WFSTestUtils.getJSONStringResponse(inputGDMLpnDetailsResponseFilePath2);
  String inputPayloadWithoutDeliveryDocument =
      WFSTestUtils.getJSONStringResponse(inputPayloadWithoutDeliveryDocumentFilePath);

  //  @BeforeClass
  //  public void initMocks() {
  //
  //  }

  @BeforeMethod
  public void init() {
    MockitoAnnotations.initMocks(this);
    MockitoAnnotations.initMocks(this);
    TenantContext.setFacilityNum(6280);
    gson = new Gson();
    ReflectionTestUtils.setField(wfsInstructionHelperService, "wfsManagedConfig", wfsManagedConfig);
    fcNumberToNameMapping =
        "{"
            + "\"7441\":\"ORD1\","
            + "\"9208\":\"ORD2\","
            + "\"6094\":\"ORD3\","
            + "\"6012\":\"ORD4\""
            + "}";
    when(wfsManagedConfig.getFcNameMapping()).thenReturn(fcNumberToNameMapping);
    ReflectionTestUtils.setField(wfsInstructionHelperService, "gson", gson);
    gson = new Gson();
  }

  @AfterMethod
  public void resetMocks() {
    // add all mocks
    reset(configUtils, wfsManagedConfig, gdmRestApiClient);
  }

  @Test
  public void testMapFCNumberToFCNameSuccessfully() {
    // happy flow

    Map<String, String> resultCtrDestination;
    resultCtrDestination =
        wfsInstructionHelperService.mapFCNumberToFCName(
            new HashMap<>(Collections.singletonMap(ReceivingConstants.BU_NUMBER, "7441")));
    assertEquals(resultCtrDestination.get(ReceivingConstants.FACILITY_NAME), "ORD1");
  }

  @Test
  public void testMapFCNumberToFCNameWithBadBuNumber() {
    // here, FC num does not exist in the mapping, ctrDestination is unchanged

    Map<String, String> resultCtrDestination;
    resultCtrDestination =
        wfsInstructionHelperService.mapFCNumberToFCName(
            new HashMap<>(Collections.singletonMap(ReceivingConstants.BU_NUMBER, "555")));
    assertEquals(resultCtrDestination.get(ReceivingConstants.FACILITY_NAME), null);
  }

  @Test
  public void testUpdatePrintJobsInInstructionForWFS_labelQtyGiven_KeyNotPresent() {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Pacific");

    Instruction instruction = new Instruction();
    instruction.setContainer(MockInstruction.getContainerDetails());
    Map<String, String> ctrDestination = new HashMap<>();
    ctrDestination.put(ReceivingConstants.FACILITY_NAME, "ORD4");
    instruction.getContainer().setCtrDestination(ctrDestination);

    wfsInstructionHelperService.updatePrintJobsInInstructionForWFS(instruction, 5);

    List<Map<String, Object>> updatedPrintRequests =
        (List<Map<String, Object>>)
            instruction.getContainer().getCtrLabel().get(ReceivingConstants.PRINT_REQUEST_KEY);
    Map<String, Object> printRequest = updatedPrintRequests.get(0);
    List<Map<String, String>> labelDataList = (List<Map<String, String>>) printRequest.get("data");

    assertEquals(labelDataList.size(), 4);
    Map<String, String> newLabel1 = labelDataList.get(1);
    assertEquals(newLabel1.get("key"), "QTY");
    assertEquals(newLabel1.get("value"), "5");

    Map<String, String> newLabel2 = labelDataList.get(2);
    assertEquals(newLabel2.get("key"), "LABELTIMESTAMP");
    assertNotNull(newLabel2.get("value"));

    Map<String, String> newLabel3 = labelDataList.get(3);
    assertEquals(newLabel3.get("key"), "FCNAME");
    assertNotNull(newLabel3.get("value"));
    assertEquals(newLabel3.get("value"), "ORD4");
  }

  @Test
  public void testUpdatePrintJobsInInstructionForWFS_labelQtyNotGiven_KeyNotPresent() {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Pacific");

    Instruction instruction = new Instruction();
    instruction.setContainer(MockInstruction.getContainerDetails());
    Map<String, String> ctrDestination = new HashMap<>();
    ctrDestination.put(ReceivingConstants.FACILITY_NAME, "ORD4");
    instruction.getContainer().setCtrDestination(ctrDestination);

    wfsInstructionHelperService.updatePrintJobsInInstructionForWFS(instruction, null);

    List<Map<String, Object>> updatedPrintRequests =
        (List<Map<String, Object>>)
            instruction.getContainer().getCtrLabel().get(ReceivingConstants.PRINT_REQUEST_KEY);
    Map<String, Object> printRequest = updatedPrintRequests.get(0);
    List<Map<String, String>> labelDataList = (List<Map<String, String>>) printRequest.get("data");

    assertEquals(labelDataList.size(), 3);
    Map<String, String> newLabel1 = labelDataList.get(1);
    assertEquals(newLabel1.get("key"), "LABELTIMESTAMP");
    assertNotNull(newLabel1.get("value"));

    Map<String, String> newLabel3 = labelDataList.get(2);
    assertEquals(newLabel3.get("key"), "FCNAME");
    assertNotNull(newLabel3.get("value"));
    assertEquals(newLabel3.get("value"), "ORD4");
  }

  @Test
  public void testUpdatePrintJobsInInstructionForWFS_labelQtyGiven_KeyPresent() {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Pacific");

    Instruction instruction = new Instruction();
    Map<String, String> ctrDestination = new HashMap<>();
    ctrDestination.put(ReceivingConstants.FACILITY_NAME, "ORD4");

    ContainerDetails mockContainerDetails = MockInstruction.getContainerDetails();
    Map<String, Object> mockCtrLabel = mockContainerDetails.getCtrLabel();
    List<Map<String, Object>> mockUpdatedPrintRequests =
        (List<Map<String, Object>>) mockCtrLabel.get(ReceivingConstants.PRINT_REQUEST_KEY);
    Map<String, Object> mockPrintRequest = mockUpdatedPrintRequests.get(0);
    List<Map<String, String>> mockLabelDataList =
        (List<Map<String, String>>) mockPrintRequest.get("data");
    Map<String, String> mockLabelMap = new HashMap<>();
    mockLabelMap.put("key", "QTY");
    mockLabelMap.put("value", "");
    mockLabelDataList.add(mockLabelMap);
    mockContainerDetails.setCtrLabel(mockCtrLabel);
    instruction.setContainer(mockContainerDetails);

    wfsInstructionHelperService.updatePrintJobsInInstructionForWFS(instruction, 3);

    List<Map<String, Object>> updatedPrintRequests =
        (List<Map<String, Object>>)
            instruction.getContainer().getCtrLabel().get(ReceivingConstants.PRINT_REQUEST_KEY);
    Map<String, Object> printRequest = updatedPrintRequests.get(0);
    List<Map<String, String>> labelDataList = (List<Map<String, String>>) printRequest.get("data");

    assertEquals(labelDataList.size(), 4);
    Map<String, String> newLabel1 = labelDataList.get(1);
    assertEquals(newLabel1.get("key"), "QTY");
    assertEquals(newLabel1.get("value"), "3");

    Map<String, String> newLabel2 = labelDataList.get(2);
    assertEquals(newLabel2.get("key"), "LABELTIMESTAMP");
    assertNotNull(newLabel2.get("value"));

    Map<String, String> newLabel3 = labelDataList.get(3);
    assertEquals(newLabel3.get("key"), "FCNAME");
    assertNotNull(newLabel3.get("value"));
    assertEquals(newLabel3.get("value"), "ORD4");
  }

  @Test
  public void testUpdatePrintJobsInInstructionForWFS_fcNameIsAbsent() throws Exception {
    when(configUtils.getDCTimeZone(any())).thenReturn("US/Pacific");

    Instruction instruction = new Instruction();
    instruction.setContainer(MockInstruction.getContainerDetails());
    instruction.getContainer().setCtrDestination(null);

    wfsInstructionHelperService.updatePrintJobsInInstructionForWFS(instruction, 5);

    List<Map<String, Object>> updatedPrintRequests =
        (List<Map<String, Object>>)
            instruction.getContainer().getCtrLabel().get(ReceivingConstants.PRINT_REQUEST_KEY);
    Map<String, Object> printRequest = updatedPrintRequests.get(0);
    List<Map<String, String>> labelDataList = (List<Map<String, String>>) printRequest.get("data");

    assertEquals(labelDataList.size(), 3);
    Map<String, String> newLabel1 = labelDataList.get(1);
    assertEquals(newLabel1.get("key"), "QTY");
    assertEquals(newLabel1.get("value"), "5");

    Map<String, String> newLabel2 = labelDataList.get(2);
    assertEquals(newLabel2.get("key"), "LABELTIMESTAMP");
    assertNotNull(newLabel2.get("value"));
  }

  // 2d barcode scan -> throws exception
  @Test
  public void testCheckShelfContainers_scan2dBarcodeThrowsException() throws ReceivingException {
    doReturn(inputGDMLpnDetailsResponse2)
        .when(gdmRestApiClient)
        .getReReceivingContainerResponseFromGDM(any(), any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithoutDeliveryDocument, InstructionRequest.class);
    instructionRequest.setUpcNumber("");
    instructionRequest.setScannedDataList(WFSTestUtils.getScannedDataList());
    try {
      wfsInstructionHelperService.checkForPendingShelfContainers(instructionRequest, headers);
    } catch (ReceivingException e) {
      verify(gdmRestApiClient, times(1)).getReReceivingContainerResponseFromGDM(any(), any());
      Assert.assertEquals(e.getHttpStatus(), HttpStatus.UNPROCESSABLE_ENTITY);
      Assert.assertEquals(e.getErrorResponse().getErrorKey(), PENDING_LPN);
    }
  }

  // 2d-barcode scan no exception
  @Test
  public void testCheckShelfContainers_scan2dBarcodeThrowsNoException() throws ReceivingException {
    doReturn(inputGDMLpnDetailsResponse)
        .when(gdmRestApiClient)
        .getReReceivingContainerResponseFromGDM(any(), any());
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithoutDeliveryDocument, InstructionRequest.class);
    instructionRequest.setUpcNumber("");
    instructionRequest.setReceivingType("UPC");
    instructionRequest.setScannedDataList(WFSTestUtils.getScannedDataList());
    try {
      wfsInstructionHelperService.checkForPendingShelfContainers(instructionRequest, headers);
      verify(gdmRestApiClient, times(1)).getReReceivingContainerResponseFromGDM(any(), any());
    } catch (Exception e) {

    }
  }

  @Test
  public void testCheckShelfContainers_NoException() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    String gdmLpnDetailsResponseString = "";
    doReturn(gdmLpnDetailsResponseString)
        .when(gdmRestApiClient)
        .getReReceivingContainerResponseFromGDM(any(), any());
    when(appConfig.getGdmBaseUrl()).thenReturn("https://stg-int.gdm.prod.us.walmart.net");
    InstructionRequest instructionRequest =
        gson.fromJson(inputPayloadWithoutDeliveryDocument, InstructionRequest.class);
    instructionRequest.setUpcNumber("04809342343");

    try {
      wfsInstructionHelperService.checkForPendingShelfContainers(instructionRequest, headers);
    } catch (Exception e) {

    }
  }

  @Test
  public void testCheckShelfContainers_NoException2() throws ReceivingException {
    doReturn(Boolean.TRUE)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            eq("6280"), eq(WFSConstants.IS_RE_RECEIVING_CONTAINER_CHECK_ENABLED), anyBoolean());
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setUpcNumber("00298347293478");
    wfsInstructionHelperService.checkForPendingShelfContainers(new InstructionRequest(), headers);
  }
}
