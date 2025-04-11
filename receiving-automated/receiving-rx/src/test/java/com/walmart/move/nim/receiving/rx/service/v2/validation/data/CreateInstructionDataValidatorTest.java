package com.walmart.move.nim.receiving.rx.service.v2.validation.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.Error;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxInstructionHelperService;
import com.walmart.move.nim.receiving.rx.service.RxInstructionPersisterService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.request.RequestValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
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
import java.util.HashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CreateInstructionDataValidatorTest {

    @Mock private TenantSpecificConfigReader tenantSpecificConfigReader;
    @Mock private RxInstructionPersisterService rxInstructionPersisterService;
    @Mock private RxInstructionHelperService rxInstructionHelperService;

    @InjectMocks private CreateInstructionDataValidator createInstructionDataValidator;
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

    @AfterMethod
    public void cleanUp() {
        Mockito.reset(tenantSpecificConfigReader);
        Mockito.reset(rxInstructionPersisterService);
        Mockito.reset(rxInstructionHelperService);
    }


    @Test
    public void testValidateScanForEPCISUnit2DRecv() {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
        deliveryDocumentLine.getAdditionalInfo().setIsSerUnit2DScan(null);
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validateScanForEPCISUnit2DRecv(deliveryDocumentLine, RxReceivingType.TWOD_BARCODE_PARTIALS));
        Assertions.assertEquals("Scan Unit 2D barcode.", exception.getMessage());

    }

    private DeliveryDocument getDeliveryDocument(Instruction instruction) {
        return gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    }

    @Test
    public void testPerformDeliveryDocumentLineValidations() {
        Instruction instruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");
        instruction.setMove(MockInstruction.getMoveData());
        DeliveryDocument deliveryDocument = getDeliveryDocument(instruction);
        DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);

        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.performDeliveryDocumentLineValidations(null));
        Assertions.assertEquals("Allowed PO Line quantity has been received.", exception.getMessage());

        // DEPT NUMBER VALIDATION
        deliveryDocumentLine.setDeptNumber(null);
        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.performDeliveryDocumentLineValidations(deliveryDocumentLine));
        Assertions.assertEquals( "Dept Type is not available in ASN, please report problem.", exception1.getMessage());

        DeliveryDocumentLine deliveryDocumentLine2 = getDeliveryDocument(instruction).getDeliveryDocumentLines().get(0);
        deliveryDocumentLine2.setShipmentDetailsList(new ArrayList<>());
        Throwable exception2 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.performDeliveryDocumentLineValidations(deliveryDocumentLine2));
        Assertions.assertEquals(  "Shipment Details unavailable while publishing to EPCIS", exception2.getMessage());

        // CONTROLLED SUBSTANCE
        DeliveryDocumentLine deliveryDocumentLine3 = getDeliveryDocument(instruction).getDeliveryDocumentLines().get(0);
        deliveryDocumentLine3.getAdditionalInfo().setIsControlledSubstance(true);
        Throwable exception3 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.performDeliveryDocumentLineValidations(deliveryDocumentLine3));
        Assertions.assertEquals(  String.format("The scanned item %s is a controlled substance. Quarantine this item", deliveryDocumentLine3.getItemNbr()), exception3.getMessage());

        // HANDLING CODE
        Mockito.when(tenantSpecificConfigReader.isFeatureFlagEnabled(anyString())).thenReturn(true);
        DeliveryDocumentLine deliveryDocumentLine4 = getDeliveryDocument(instruction).getDeliveryDocumentLines().get(0);
        deliveryDocumentLine4.getAdditionalInfo().setHandlingCode("X");
        Throwable exception4 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.performDeliveryDocumentLineValidations(deliveryDocumentLine4));
        Assertions.assertEquals(  String.format("Item %s is showing as blocked and cannot be received. Please contact the QA team on how to proceed.", deliveryDocumentLine3.getItemNbr()), exception4.getMessage());



    }

    @Test
    public void testValidatePartialsInSplitPallet() {
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("SPLIT_PALLET_SSCC");
        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validatePartialsInSplitPallet(instructionRequest, true));
        Assertions.assertEquals(  "Partial case receiving is not allowed in split pallet.Please receive scanned item through Partial receiving feature.", exception1.getMessage());

    }

    @Test
    public void testValidateScannedData() {
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

        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validateScannedData(new HashMap<>(), "01", "test Error"));
        Assertions.assertEquals(  "Ineligible for receiving. Mandatory ScannedData field is missing.", exception.getMessage());

        Throwable exception1 = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validateScannedData(RxUtils.scannedDataMap(Arrays.asList(scannedData1, scannedData2, scannedData3)), "10", "This is a mock Error"));
        Assertions.assertEquals(  "This is a mock Error", exception1.getMessage());

    }

    @Test
    public void testThrowEPCISProblemDataNotFound() {
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.throwEPCISProblemDataNotFound());
        Assertions.assertEquals(  "Problem Not found in Receiving. Please scan valid Problem ticket to continue.", exception.getMessage());

    }

    @Test
    public void testIsNewInstructionCanBeCreated() throws ReceivingException {
        Mockito.doNothing().when(rxInstructionPersisterService).checkIfNewInstructionCanBeCreated(anyString(), anyInt(), anyLong(), anyInt(), anyBoolean(), anyString());
        createInstructionDataValidator.isNewInstructionCanBeCreated("testReferenceNumber", 1, 2, 3L, true, true, "test");
        Mockito.verify(rxInstructionPersisterService, Mockito.times(1)).checkIfNewInstructionCanBeCreated(anyString(), anyInt(), anyLong(), anyInt(), anyBoolean(), anyString());
    }



    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_false() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(true);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getPurchaseOrders().get(0).getVendorInformation().setSerialInfoEnabled(false);
        Boolean returnValue = createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false);
        Assert.assertFalse(returnValue);
    }

    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_false1() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(true);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.setPurchaseOrders(new ArrayList<>());
        Boolean returnValue = createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false);
        Assert.assertFalse(returnValue);
    }

    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_true() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).thenReturn(true);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getPurchaseOrders().get(0).getVendorInformation().setSerialInfoEnabled(true);
        Boolean returnValue = createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false);
        Assert.assertTrue(returnValue);
    }

    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_true_asn_switch_true_no_data_found() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).
                thenReturn(true);

       SsccScanResponse ssccScanResponse = new SsccScanResponse();
        Error error = new Error();
        error.setErrorCode("GDM_EPCIS_DATA_404_FOR_PO");
        ssccScanResponse.setErrors(Arrays.asList());
        Boolean returnValue = createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false);
        Assert.assertFalse(returnValue);
    }

    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_true_asn_switch_false_no_data_found() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).
                thenReturn(false);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getPurchaseOrders().get(0).getVendorInformation().setSerialInfoEnabled(true);
        Error error = new Error();
        error.setErrorCode("GDM_EPCIS_DATA_404_FOR_PO_LINE");
        ssccScanResponse.setErrors(Arrays.asList(error));
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false));
        Assertions.assertEquals(  "EPCIS validation is unavailable for this barcode. Scan the case or unit directly to receive. Otherwise, quarantine this freight.", exception.getMessage());

    }

    @Test
    public void testValidateCurrentNodeResponse_epcis_enabled_true_asn_switch_true_no_data_found1() throws IOException {
        Mockito.when(tenantSpecificConfigReader.getConfiguredFeatureFlag(anyString(), anyString(), anyBoolean())).
                thenReturn(true);

        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getPurchaseOrders().get(0).getVendorInformation().setSerialInfoEnabled(true);
        Error error = new Error();
        error.setErrorCode("GDM_EPCIS_DATA_404_FOR_PO_LINE");
        ssccScanResponse.setErrors(Arrays.asList(error));
       Boolean returnValue = createInstructionDataValidator.validateCurrentNodeResponse(ssccScanResponse,false);
       Assert.assertFalse(returnValue);
    }


    @Test
    public void testValidateNodesReceivingStatus() {
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validateNodesReceivingStatus("Received"));
        Assertions.assertEquals(   "Scanned barcode has been already Received. Please scan a valid barcode.", exception.getMessage());

    }

    @Test
    public void testValidatePartiallyReceivedContainers() {
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionDataValidator.validatePartiallyReceivedContainers("PartiallyReceived"));
        Assertions.assertEquals(    "Some cases/units have been already Received. Please scan individual cases/units to receive the remaining", exception.getMessage());

    }

    @Test
    public void testValidateCurrentNodeExpiryAndLot_bad_exp_date() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

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
        scannedData3.setValue("261231d");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testLot");
        scannedData4.setApplicationIdentifier("10");

        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));


        Throwable exception = Assertions.assertThrows(RuntimeException.class,
                () ->  createInstructionDataValidator.validateCurrentNodeExpiryAndLot(instructionRequest, ssccScanResponse));
        Assertions.assertNotNull(exception);

    }

    @Test
    public void testValidateCurrentNodeExpiryAndLot_mismatch_exp_date() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

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


        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () ->  createInstructionDataValidator.validateCurrentNodeExpiryAndLot(instructionRequest, ssccScanResponse));
        Assertions.assertEquals(    "GDM_EXPIRY_NOT_MATCHING_SCAN_EXPIRY", exception.getMessage());

    }


    @Test
    public void testValidateCurrentNodeExpiryAndLot_mismatch_lot() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("gdmLot");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

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


        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () ->  createInstructionDataValidator.validateCurrentNodeExpiryAndLot(instructionRequest, ssccScanResponse));
        Assertions.assertEquals(    "GDM_LOT_NOT_MATCHING_SCAN_LOT", exception.getMessage());

    }

    @Test
    public void testValidateCurrentNodeExpiryAndLot_match() throws IOException {
        Mockito.doNothing().when(rxInstructionHelperService).checkIfContainerIsCloseDated(anyMap());
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("gdmLot");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();

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
        scannedData4.setValue("gdmLot");
        scannedData4.setApplicationIdentifier("10");

        instructionRequest.setProblemTagId(null);
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));

        createInstructionDataValidator.validateCurrentNodeExpiryAndLot(instructionRequest, ssccScanResponse);
        Mockito.verify(rxInstructionHelperService, Mockito.times(1)).checkIfContainerIsCloseDated(anyMap());

    }
}