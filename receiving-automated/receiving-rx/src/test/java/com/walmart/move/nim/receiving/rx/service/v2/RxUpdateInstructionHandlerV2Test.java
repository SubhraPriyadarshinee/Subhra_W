package com.walmart.move.nim.receiving.rx.service.v2;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.v2.ProcessInstructionService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.v2.data.UpdateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.instruction.InstructionFactory;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.UpdateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class RxUpdateInstructionHandlerV2Test {

    @Mock private InstructionFactory factory;
    @Mock private UpdateInstructionServiceHelper updateInstructionServiceHelper;
    @Mock private UpdateInstructionDataValidator updateInstructionDataValidator;
    @Mock private ProcessInstructionService processInstructionService;
    @InjectMocks private RxUpdateInstructionHandlerV2 rxUpdateInstructionHandlerV2;

    private Gson gson = new Gson();

    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);

    }

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testUpdateInstruction_valid_user_input() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
        mockInstructionResponse.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));

        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocuments(Arrays.asList(deliveryDocument));
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");
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

        UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
        updateInstructionRequest.setUserEnteredDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        Mockito.when(updateInstructionServiceHelper.getDataForUpdateInstruction(anyLong(), any(UpdateInstructionRequest.class), anyString())).thenReturn(dataHolder);
        Mockito.when(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(any(Instruction.class))).thenReturn(true);
        Mockito.when(factory.getUpdateInstructionService(anyString())).thenReturn(processInstructionService);
        Mockito.doNothing().when(updateInstructionDataValidator).validateInstructionAndInstructionOwner(any(DataHolder.class), any(HttpHeaders.class));
        Mockito.when(processInstructionService.validateUserEnteredQty(any(UpdateInstructionRequest.class), any(Instruction.class))).thenReturn(mockInstructionResponse);
        Mockito.doNothing().when(processInstructionService).processUpdateInstruction(any(UpdateInstructionRequest.class), any(DataHolder.class),
                any(DeliveryDocument.class), any(DeliveryDocumentLine.class), anyBoolean(), any(HttpHeaders.class));
        Mockito.when(processInstructionService.buildContainerAndUpdateInstruction(any(UpdateInstructionRequest.class), any(DataHolder.class), anyString(),
                any(HttpHeaders.class))).thenReturn((InstructionResponseImplNew) mockInstructionResponse);

        InstructionResponse returnedResponse = rxUpdateInstructionHandlerV2.updateInstruction(123L, updateInstructionRequest, "trackingId123", MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedResponse);

    }

    @Test
    public void testUpdateInstruction_invalid_user_input() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        InstructionResponse mockInstructionResponse = new InstructionResponseImplNew();
        mockInstructionResponse.setInstruction(MockInstruction.getInstructionV2("RxBuildContainer"));

        DataHolder dataHolder = new DataHolder();
        dataHolder.setInstruction(instruction);
        dataHolder.setContainer(ssccScanResponse.getContainers().get(0));
        dataHolder.setDeliveryDocument(deliveryDocument);
        dataHolder.setDeliveryDocuments(Arrays.asList(deliveryDocument));
        dataHolder.setDeliveryDocumentLine(deliveryDocument.getDeliveryDocumentLines().get(0));
        dataHolder.setGdmResponseForScannedData(ssccScanResponse.getContainers().get(0));
        dataHolder.setReceivingFlow("CASE");


        UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
        updateInstructionRequest.setUserEnteredDataList(Arrays.asList());

        Mockito.when(updateInstructionServiceHelper.getDataForUpdateInstruction(anyLong(), any(UpdateInstructionRequest.class), anyString())).thenReturn(dataHolder);
        Mockito.when(updateInstructionServiceHelper.isEpcisSmartReceivingEnabled(any(Instruction.class))).thenReturn(true);
        Mockito.when(factory.getUpdateInstructionService(anyString())).thenReturn(processInstructionService);
        Mockito.doNothing().when(updateInstructionDataValidator).validateInstructionAndInstructionOwner(any(DataHolder.class), any(HttpHeaders.class));
        Mockito.when(processInstructionService.validateUserEnteredQty(any(UpdateInstructionRequest.class), any(Instruction.class))).thenReturn(mockInstructionResponse);
        Mockito.doNothing().when(processInstructionService).processUpdateInstruction(any(UpdateInstructionRequest.class), any(DataHolder.class),
                any(DeliveryDocument.class), any(DeliveryDocumentLine.class), anyBoolean(), any(HttpHeaders.class));
        Mockito.when(processInstructionService.buildContainerAndUpdateInstruction(any(UpdateInstructionRequest.class), any(DataHolder.class), anyString(),
                any(HttpHeaders.class))).thenReturn((InstructionResponseImplNew) mockInstructionResponse);

        InstructionResponse returnedResponse = rxUpdateInstructionHandlerV2.updateInstruction(123L, updateInstructionRequest, "trackingId123", MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedResponse);

    }
}