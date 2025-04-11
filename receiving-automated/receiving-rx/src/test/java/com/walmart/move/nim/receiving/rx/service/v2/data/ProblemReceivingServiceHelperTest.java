package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.InstructionError;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.config.RxManagedConfig;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryServiceImpl;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class ProblemReceivingServiceHelperTest {

    @Mock private AppConfig appConfig;
    @Mock private RxDeliveryServiceImpl rxDeliveryService;
    @Mock private RxManagedConfig rxManagedConfig;
    @Mock  private RxInstructionHelperService rxInstructionHelperService;
    @Mock private  InstructionHelperService instructionHelperService;


    @InjectMocks private ProblemReceivingServiceHelper problemReceivingServiceHelper;
    private static Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        //ReflectionTestUtils.setField(problemReceivingServiceHelper, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @AfterMethod
    public void tearDown() {
        Mockito.reset(appConfig);
        Mockito.reset(rxDeliveryService);
        Mockito.reset(rxManagedConfig);
        Mockito.reset(rxInstructionHelperService);
        Mockito.reset(instructionHelperService);

    }

    @Test
    public void testGetProjectedReceivedQtyInEaches_problemItem_check_disabled() throws ReceivingException {
        Mockito.when(rxManagedConfig.isProblemItemCheckEnabled()).thenReturn(false);
        Assert.assertEquals(0, problemReceivingServiceHelper.getProjectedReceivedQtyInEaches(new FitProblemTagResponse(),
                new InstructionRequest(),
                new ArrayList<>(),
                new DeliveryDocumentLine()));
    }

    @Test
    public void testGetProjectedReceivedQtyInEaches_problemItem_check_enabled() throws ReceivingException, IOException {
        Mockito.when(rxManagedConfig.isProblemItemCheckEnabled()).thenReturn(true);
        // FitProblemResponse
        FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
        Resolution mockResolution = new Resolution();
        mockResolution.setQuantity(3);
        mockResolution.setState(RxConstants.OPEN);
        mockResolution.setQuantity(3);
        Issue issue = new Issue();
        issue.setQuantity(3);
        issue.setUom("");
        mockFitProblemTagResponse.setReportedQty(3);
        mockFitProblemTagResponse.setIssue(issue);
        mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

        // Instruction Request
        InstructionRequest mockInstructionRequest = MockInstruction.getProblemInstructionRequest();
        DeliveryDocument mockDeliveryDocument = getDeliveryDocument();
        DeliveryDocumentLine mockDeliveryDocumentLine = mockDeliveryDocument.getDeliveryDocumentLines().get(0);
        mockDeliveryDocumentLine.setVendorPack(1);
        mockDeliveryDocumentLine.setWarehousePack(1);
        mockDeliveryDocumentLine.getAdditionalInfo().setAttpQtyInEaches(mockDeliveryDocumentLine.getTotalOrderQty() + mockDeliveryDocumentLine.getOverageQtyLimit());
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(mockDeliveryDocument));
        Mockito.when(rxManagedConfig.isProblemItemCheckEnabled()).thenReturn(true);
        Mockito.when(instructionHelperService.getReceivedQtyDetailsInEaAndValidate(anyString(), any(DeliveryDocument.class), anyString())).thenReturn(new Pair<>(1, 1L));

        // ASSERTION
        Assert.assertEquals(2, problemReceivingServiceHelper.getProjectedReceivedQtyInEaches(mockFitProblemTagResponse,
                mockInstructionRequest,
                Arrays.asList(mockDeliveryDocument),
                mockDeliveryDocumentLine));
    }


    @Test
    public void testValidateFitProblemResponse_2d_scan() throws IOException {
        Mockito.doNothing().when(rxInstructionHelperService).sameItemOnProblem(any(FitProblemTagResponse.class), any(DeliveryDocumentLine.class));
        Mockito.doNothing().when(rxInstructionHelperService).checkIfContainerIsCloseDated(any(FitProblemTagResponse.class), anyMap());

        FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
        Resolution mockResolution = new Resolution();
        mockResolution.setQuantity(10);
        mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

        InstructionRequest mockInstructionRequest = MockInstruction.getProblemInstructionRequest();
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("200109395464720439");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testLot");
        scannedData4.setApplicationIdentifier("10");
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        problemReceivingServiceHelper.validateFitProblemResponse(mockInstructionRequest,
                getDeliveryDocument().getDeliveryDocumentLines().get(0),
                mockFitProblemTagResponse);

        // VERIFY METHODS ARE CALLED
        Mockito.verify(rxInstructionHelperService, Mockito.times(1)).sameItemOnProblem(any(FitProblemTagResponse.class), any(DeliveryDocumentLine.class));
        Mockito.verify(rxInstructionHelperService, Mockito.times(1)).checkIfContainerIsCloseDated(any(FitProblemTagResponse.class), anyMap());

    }


    @Test
    public void testValidateFitProblemResponse_sscc_scan() throws IOException {
        Mockito.doNothing().when(rxInstructionHelperService).sameItemOnProblem(any(FitProblemTagResponse.class), any(DeliveryDocumentLine.class));
        Mockito.doNothing().when(rxInstructionHelperService).checkIfContainerIsCloseDated(any(FitProblemTagResponse.class), anyMap());

        FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
        Resolution mockResolution = new Resolution();
        mockResolution.setQuantity(10);
        mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

        InstructionRequest mockInstructionRequest = MockInstruction.getProblemInstructionRequest();
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("sscc");
        scannedData1.setValue("B06085000020338321");
        scannedData1.setApplicationIdentifier("00");



        problemReceivingServiceHelper.validateFitProblemResponse(mockInstructionRequest,
                getDeliveryDocument().getDeliveryDocumentLines().get(0),
                mockFitProblemTagResponse);

        Mockito.verify(rxInstructionHelperService, Mockito.times(1)).sameItemOnProblem(any(FitProblemTagResponse.class), any(DeliveryDocumentLine.class));
        // VERIFY THE METHOD IS NEVER CALLED
        Mockito.verify(rxInstructionHelperService, Mockito.never()).checkIfContainerIsCloseDated(any(FitProblemTagResponse.class), anyMap());

    }

    @Test
    public void testCheckForLatestShipments() throws IOException, ReceivingException {
        // isAttachShipments flag set to true
        Mockito.when(appConfig.isAttachLatestShipments()).thenReturn(true);
        DeliveryDocument mockDeliveryDocument = getDeliveryDocument();
        Optional<List<DeliveryDocument>> mockOptionalDeliveryDocument = Optional.of(Arrays.asList(mockDeliveryDocument));
        Mockito.when(rxDeliveryService.findDeliveryDocumentBySSCCWithLatestShipmentLinking(anyString(), anyString(), any(HttpHeaders.class))).
                thenReturn(mockOptionalDeliveryDocument);
        Mockito.when(rxDeliveryService.linkDeliveryAndShipmentByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class))).
                thenReturn(mockOptionalDeliveryDocument);

        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setProblemTagId("probTagId");
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("200109395464720439");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testLot");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setSscc(null);
        instructionRequest.setDeliveryNumber("del123");



        Optional<List<DeliveryDocument>> returnedValue = problemReceivingServiceHelper.checkForLatestShipments(instructionRequest,
                RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4)),
                MockHttpHeaders.getHeaders());

        // VERIFY linkDeliveryAndShipmentByGtinAndLotNumber method was called
        Mockito.verify(rxDeliveryService, Mockito.times(1)).linkDeliveryAndShipmentByGtinAndLotNumber(anyString(), anyMap(), any(HttpHeaders.class));
        Assert.assertEquals(mockDeliveryDocument.getDeliveryNumber(),
                returnedValue.get().get(0).getDeliveryNumber()
                );

        ScannedData scannedDataSscc = new ScannedData();
        scannedDataSscc.setKey("sscc");
        scannedDataSscc.setValue("B06085000020338321");
        scannedDataSscc.setApplicationIdentifier("00");
        instructionRequest.setScannedDataList(Arrays.asList(scannedDataSscc));
        instructionRequest.setSscc("B06085000020338321");
        Optional<List<DeliveryDocument>> returnedValue1 = problemReceivingServiceHelper.checkForLatestShipments(instructionRequest,
                RxUtils.scannedDataMap(Arrays.asList(scannedDataSscc)),
                MockHttpHeaders.getHeaders());

        // VERIFY findDeliveryDocumentBySSCCWithLatestShipmentLinking method was called
        Mockito.verify(rxDeliveryService, Mockito.times(1)).findDeliveryDocumentBySSCCWithLatestShipmentLinking(anyString(), anyString(), any(HttpHeaders.class));
        Assert.assertEquals(mockDeliveryDocument.getDeliveryNumber(),
                returnedValue1.get().get(0).getDeliveryNumber()
                );


        // isAttachShipments flag set to false
        Mockito.when(appConfig.isAttachLatestShipments()).thenReturn(false);
        Optional<List<DeliveryDocument>> returnedValue3 = problemReceivingServiceHelper.checkForLatestShipments(instructionRequest,
                RxUtils.scannedDataMap(Arrays.asList(scannedDataSscc)),
                MockHttpHeaders.getHeaders());
        // NO RESPONSE WILL BE SENT FROM CALLING METHOD
        Assert.assertFalse(returnedValue3.isPresent());

    }

    @Test
    public void testFetchFitResponseForProblemTagId() {
        InstructionRequest mockInstructionRequest = MockInstruction.getProblemInstructionRequest();
        FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
        Resolution mockResolution = new Resolution();
        mockResolution.setQuantity(10);
        mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));
        mockInstructionRequest.setFitProblemTagResponse(null);

        Optional<FitProblemTagResponse> optionalFitProblemTagResponse = Optional.of(mockFitProblemTagResponse);
        Mockito.when(rxManagedConfig.isProblemItemCheckEnabled()).thenReturn(true);
        Mockito.when(rxInstructionHelperService.getFitProblemTagResponse(anyString())).thenReturn(optionalFitProblemTagResponse);
        FitProblemTagResponse returnedValue = problemReceivingServiceHelper.fetchFitResponseForProblemTagId(mockInstructionRequest);
        // VERIFY FIT API IS CALLED
        Mockito.verify(rxInstructionHelperService).getFitProblemTagResponse(anyString());
        Assert.assertNotNull(returnedValue);
    }

    @Test
    public void testFetchFitResponseForProblemTagId_1() {
        InstructionRequest mockInstructionRequest = MockInstruction.getProblemInstructionRequest();
        FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
        Resolution mockResolution = new Resolution();
        mockResolution.setQuantity(10);
        mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

        mockInstructionRequest.setFitProblemTagResponse(mockFitProblemTagResponse);
        FitProblemTagResponse returnedValue1 = problemReceivingServiceHelper.fetchFitResponseForProblemTagId(mockInstructionRequest);
        // VERIFY THAT getFitProblemTagResponse API CALL IS NOT MADE FOR NOT EMPTY FIT RESPONSE IN INSTRUCTION REQUEST
        Mockito.verifyNoInteractions(rxInstructionHelperService);
        Assert.assertNotNull(returnedValue1);


    }

    private static DeliveryDocument getDeliveryDocument()  throws IOException {
        File resource =
                new ClassPathResource("delivery_documents_mock.json")
                        .getFile();
        String mockResponse = new String(Files.readAllBytes(resource.toPath()));
        DeliveryDocument deliveryDocument = gson.fromJson(mockResponse, DeliveryDocument.class);
        return deliveryDocument;
    }
}