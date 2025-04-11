package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RxDeliveryDocumentsMapperV2;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingNotImplementedException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AdditionalInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.testng.Assert.*;

public class RxDeliveryDocumentsSearchHandlerV2Test {

    @Mock private RxDeliveryDocumentsMapperV2 rxDeliveryDocumentsMapperV2;
    @Mock private CreateInstructionDataValidator createInstructionDataValidator;
    @Mock private CreateInstructionServiceHelper createInstructionServiceHelper;
    @Mock private RxDeliveryServiceImpl rxDeliveryServiceImpl;

    @InjectMocks private RxDeliveryDocumentsSearchHandlerV2 rxDeliveryDocumentsSearchHandlerV2;

    private static Gson gson = new Gson();

    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(rxDeliveryDocumentsMapperV2);
        Mockito.reset(createInstructionDataValidator);
        Mockito.reset(createInstructionServiceHelper);
        Mockito.reset(rxDeliveryServiceImpl);
    }

    @Test
    public void testFetchDeliveryDocument_regular_receivingType() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

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
        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        mockInstructionRequest.setReceivingType("2D_BARCODE");
        mockInstructionRequest.setSscc(null);
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        Mockito.when(rxDeliveryServiceImpl.getCurrentNode(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(createInstructionDataValidator.validateCurrentNodeResponse(any(),anyBoolean())).thenReturn(true);
        Mockito.doNothing().when(createInstructionDataValidator).validateCurrentNodeExpiryAndLot(any(), any());

        List<DeliveryDocument> returnedObj = rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(mockInstructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedObj);

    }


    @Test
    public void testGetCurrentAndSiblings_sgtin() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

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
        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        mockInstructionRequest.setReceivingType("2D_BARCODE");
        mockInstructionRequest.setSscc(null);
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);

        SsccScanResponse returnedObj = rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(mockInstructionRequest, MockHttpHeaders.getHeaders(), "parent123");
        Assert.assertNotNull(returnedObj);

    }

    @Test
    public void testGetCurrentAndSiblings_sscc() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("sscc");
        scannedData1.setValue("123");
        scannedData1.setApplicationIdentifier("00");

        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1));
        mockInstructionRequest.setReceivingType("sscc");
        mockInstructionRequest.setSscc("1234");
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        Mockito.when(rxDeliveryServiceImpl.getCurrentAndSiblings(any(), any(), anyMap())).thenReturn(ssccScanResponse);

        SsccScanResponse returnedObj = rxDeliveryDocumentsSearchHandlerV2.getCurrentAndSiblings(mockInstructionRequest, MockHttpHeaders.getHeaders(), "parent123");
        Assert.assertNotNull(returnedObj);

    }


    @Test
    public void testFetchDeliveryDocument_regular_receivingType_multisku_hint1() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
        gdmContainer.setSscc("rootSSCC");
        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setContainers(Arrays.asList(gdmContainer));
        ssccScanResponse.setAdditionalInfo(additionalInfo);

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
        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        mockInstructionRequest.setReceivingType("2D_BARCODE");
        mockInstructionRequest.setSscc(null);
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        Mockito.when(rxDeliveryServiceImpl.getCurrentNode(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(createInstructionDataValidator.validateCurrentNodeResponse(any(),anyBoolean())).thenReturn(true);
        Mockito.doNothing().when(createInstructionDataValidator).validateCurrentNodeExpiryAndLot(any(), any());
        Mockito.when(createInstructionServiceHelper.isMultiSkuRootNode(any())).thenReturn(true); // RETURN TRUE
        List<DeliveryDocument> returnedObj = rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(mockInstructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedObj);

    }


    @Test
    public void testFetchDeliveryDocument_regular_receivingType_multisku_hint2() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

        SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
        gdmContainer.setSscc(null);
        gdmContainer.setGtin("rootGtin");
        gdmContainer.setSerial("rootSerial");
        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setContainers(Arrays.asList(gdmContainer));
        ssccScanResponse.setAdditionalInfo(additionalInfo);

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
        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        mockInstructionRequest.setReceivingType("2D_BARCODE");
        mockInstructionRequest.setSscc(null);
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        Mockito.when(rxDeliveryServiceImpl.getCurrentNode(any(), any(), anyMap())).thenReturn(ssccScanResponse);
        Mockito.when(createInstructionDataValidator.validateCurrentNodeResponse(any(),anyBoolean())).thenReturn(true);
        Mockito.doNothing().when(createInstructionDataValidator).validateCurrentNodeExpiryAndLot(any(), any());
        Mockito.when(createInstructionServiceHelper.isMultiSkuRootNode(any())).thenReturn(true); // RETURN TRUE
        List<DeliveryDocument> returnedObj = rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(mockInstructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(returnedObj);

    }


    @Test
    public void testFetchDeliveryDocument_regular_receivingType_empty_response() throws IOException, ReceivingException {

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        Instruction instruction = MockInstruction.getInstructionV2("RxSerBuildContainer");
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);

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
        InstructionRequest mockInstructionRequest = MockInstruction.getInstructionRequest();
        mockInstructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        mockInstructionRequest.setReceivingType("2D_BARCODE");
        mockInstructionRequest.setSscc(null);
        mockInstructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

        // Return null from GDM
        Mockito.when(rxDeliveryServiceImpl.getCurrentNode(any(), any(), anyMap())).thenReturn(null);
        Mockito.when(createInstructionDataValidator.validateCurrentNodeResponse(any(),anyBoolean())).thenReturn(false);
        Mockito.doNothing().when(createInstructionDataValidator).validateCurrentNodeExpiryAndLot(any(), any());

        List<DeliveryDocument> returnedObj = rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(mockInstructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals(0, returnedObj.size());

    }


    @Test
    public void testFetchDeliveryDocumentByUpc() {

        Throwable exception = Assertions.assertThrows(ReceivingNotImplementedException.class,
                () -> rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocumentByUpc(123L, "1234", MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(  "Feature Not Implemented.", exception.getMessage());

    }

    @Test
    public void testFetchDeliveryDocumentByItemNumber() {
        Throwable exception = Assertions.assertThrows(ReceivingNotImplementedException.class,
                () -> rxDeliveryDocumentsSearchHandlerV2.fetchDeliveryDocumentByItemNumber("123", 1234, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(  "Feature Not Implemented.", exception.getMessage());
    }
}