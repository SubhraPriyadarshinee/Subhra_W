package com.walmart.move.nim.receiving.rx.service.v2;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.v2.CompleteInstructionService;
import com.walmart.move.nim.receiving.core.service.v2.CompleteMultipleInstructionService;
import com.walmart.move.nim.receiving.core.service.v2.CreateInstructionService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.RxInstructionService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionServiceTest;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.data.ProblemReceivingServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.create.CaseCreateInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.create.DefaultBaseCreateInstructionService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
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
import java.util.Arrays;
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class RxInstructionHandlerV2Test {

    @Mock private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Mock private InstructionFactory factory;
    @Mock private CompleteMultipleInstructionService rxCompleteSplitPalletInstructionService;
    @Mock private ProblemReceivingServiceHelper problemReceivingServiceHelper;
    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    @Mock private CompleteInstructionService completeInstructionService;
    @Mock private CreateInstructionService createInstructionService;
    @Mock private CreateInstructionDataValidator createInstructionDataValidator;
    @Mock private RxInstructionService rxInstructionService;
    @Mock private DefaultBaseCreateInstructionService defaultBaseCreateInstructionService;
    @InjectMocks private RxInstructionHandlerV2 instructionHandlerV2;

    private Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(instructionHandlerV2, "gson", gson);
        ReflectionTestUtils.setField(defaultBaseCreateInstructionService, "createInstructionDataValidator", createInstructionDataValidator);
    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(tenantSpecificConfigReader);
        Mockito.reset(createInstructionServiceHelper);
        Mockito.reset(factory);
        Mockito.reset(rxCompleteSplitPalletInstructionService);
        Mockito.reset(problemReceivingServiceHelper);
        Mockito.reset(completeInstructionService);
        Mockito.reset(createInstructionService);
    }



    @Test
    public void testServeInstructionRequest_receiving_type_upc_epcis_flag_false() throws ReceivingException {
        // COMMENTED FOR FUTURE RESEARCH

        /*HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
        //mockHeaders.add("isEpcisSmartReceivingEnabled", "true");
        InstructionResponse response = new InstructionResponseImplNew();
        response.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));

        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(false);
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("UPC");
        //Mockito.when(((RxInstructionService)instructionHandlerV2).serveInstructionRequest(anyString(), any(HttpHeaders.class))).thenReturn(response);
        Mockito.doReturn(response).when((RxInstructionService)instructionHandlerV2).serveInstructionRequest(anyString(), any(HttpHeaders.class));
        InstructionResponse instructionResponse = instructionHandlerV2.serveInstructionRequest(gson.toJson(instructionRequest), mockHeaders);
        Assert.assertNotNull(instructionResponse);
        Mockito.verify((RxInstructionService)instructionHandlerV2, Mockito.times(1)).serveInstructionRequest(anyString(), any(HttpHeaders.class));

         */
    }

    @Test
    public void testInstructionRequest_receivingType_sscc_epcis_flag_true_existing_instruction() throws ReceivingException {
        HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
        mockHeaders.add("isEpcisSmartReceivingEnabledFromClient", "true");
        InstructionResponse response = new InstructionResponseImplNew();
        response.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));

        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(false);
        Mockito.when(createInstructionServiceHelper.isEpcisSmartReceivingEnabledFromClient(any(HttpHeaders.class))).thenReturn(true);
        Mockito.when(createInstructionServiceHelper.checkAndValidateExistingInstruction(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(response);
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("SSCC");
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
        //Mockito.when(((RxInstructionService)instructionHandlerV2).serveInstructionRequest(anyString(), any(HttpHeaders.class))).thenReturn(response);
        InstructionResponse instructionResponse = instructionHandlerV2.serveInstructionRequest(gson.toJson(instructionRequest), mockHeaders);
        Assert.assertNotNull(instructionResponse);
        Mockito.verify(createInstructionServiceHelper, Mockito.times(1)).checkAndValidateExistingInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    }

    @Test
    public void testInstructionRequest_receivingType_sscc_epcis_flag_true_new_instruction() throws ReceivingException, IOException {
        HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocuments(Arrays.asList(deliveryDocument));
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");

        InstructionResponse response = new InstructionResponseImplNew();
        response.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));

        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(true);
        Mockito.when(createInstructionServiceHelper.isEpcisSmartReceivingEnabledFromClient(any(HttpHeaders.class))).thenReturn(false);
        Mockito.when(createInstructionServiceHelper.checkAndValidateExistingInstruction(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(null);
        Mockito.when(createInstructionServiceHelper.getDataForCreateInstruction(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(dataHolder);
        Mockito.when(factory.getCreateInstructionService(anyString())).thenReturn(createInstructionService);
        Mockito.doNothing().when(createInstructionService).validateData(any(DataHolder.class));
        Mockito.doNothing().when(createInstructionService).validateData(any(InstructionRequest.class), any(DataHolder.class));
        Mockito.when(createInstructionService.serveInstruction(any(InstructionRequest.class), any(DataHolder.class), any(HttpHeaders.class))).thenReturn(response);
        Mockito.doNothing().when(createInstructionDataValidator).performDeliveryDocumentLineValidations(any(DeliveryDocumentLine.class));
        Mockito.doNothing().when(createInstructionDataValidator).validatePartiallyReceivedContainers(anyString());
        Mockito.doNothing().when(createInstructionDataValidator).validateNodesReceivingStatus(anyString());
        Mockito.doNothing().when(rxInstructionService).filterInvalidPOs(anyList());
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("SSCC");
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
        //Mockito.when(((RxInstructionService)instructionHandlerV2).serveInstructionRequest(anyString(), any(HttpHeaders.class))).thenReturn(response);
        InstructionResponse instructionResponse = instructionHandlerV2.serveInstructionRequest(gson.toJson(instructionRequest), mockHeaders);
        Assert.assertNotNull(instructionResponse);
        Mockito.verify(createInstructionServiceHelper, Mockito.times(1)).checkAndValidateExistingInstruction(any(InstructionRequest.class), any(HttpHeaders.class));
    }

    @Test
    public void testCancelInstruction() {
    }

    @Test
    public void testCompleteInstruction() throws ReceivingException {
        InstructionResponse mockResponse = new InstructionResponseImplNew();
        mockResponse.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));
        Mockito.when(completeInstructionService.completeInstruction(anyLong(), any(CompleteInstructionRequest.class), any(HttpHeaders.class))).thenReturn(mockResponse);
        InstructionResponse returnedResponse = instructionHandlerV2.completeInstruction(123L, new CompleteInstructionRequest(), MockHttpHeaders.getHeaders());
        Mockito.verify(completeInstructionService, Mockito.times(1)).completeInstruction(anyLong(), any(CompleteInstructionRequest.class), any(HttpHeaders.class));
        Assert.assertNotNull(returnedResponse);
    }

    @Test
    public void testBulkCompleteInstructions() throws ReceivingException {
        CompleteMultipleInstructionResponse mockResponse = new CompleteMultipleInstructionResponse();
        mockResponse.setPrintJob(new HashMap<>());
        Mockito.when(rxCompleteSplitPalletInstructionService.complete(any(BulkCompleteInstructionRequest.class), any(HttpHeaders.class))).thenReturn(mockResponse);
        CompleteMultipleInstructionResponse returnedResponse = instructionHandlerV2.bulkCompleteInstructions(new BulkCompleteInstructionRequest(), MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedResponse);
        Mockito.verify(rxCompleteSplitPalletInstructionService).complete(any(BulkCompleteInstructionRequest.class), any(HttpHeaders.class));
    }
}