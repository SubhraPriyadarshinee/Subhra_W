package com.walmart.move.nim.receiving.rx.service.v2.data;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.AdditionalInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DataHolder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.data.MockRxHttpHeaders;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.rx.service.RxDeliveryDocumentsSearchHandlerV2;
import com.walmart.move.nim.receiving.rx.service.RxInstructionPersisterService;
import com.walmart.move.nim.receiving.rx.service.v2.validation.data.CreateInstructionDataValidator;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.*;
import static org.testng.Assert.*;

public class CreateInstructionServiceHelperTest {

    @Mock private RxDeliveryDocumentsSearchHandlerV2 deliveryDocumentsSearchHandlerV2;
    @Mock private RxInstructionPersisterService rxInstructionPersisterService;
    @Mock private InstructionPersisterService instructionPersisterService;
    @Mock private InstructionHelperService instructionHelperService;
    @Mock private CreateInstructionDataValidator createInstructionDataValidator;

    @InjectMocks private CreateInstructionServiceHelper createInstructionServiceHelper;

    private static Gson gson = new Gson();


    @BeforeClass
    public void initMocks() {
        TenantContext.setFacilityCountryCode("US");
        TenantContext.setFacilityNum(32897);
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(createInstructionServiceHelper, "gson", gson);

    }
    @BeforeMethod
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }
    @AfterMethod
    public void tearDown() {
        Mockito.reset(deliveryDocumentsSearchHandlerV2);
        Mockito.reset(rxInstructionPersisterService);
        Mockito.reset(instructionPersisterService);
        Mockito.reset(instructionHelperService);
        Mockito.reset(createInstructionDataValidator);
    }

    @Test
    public void testCheckAndValidateExistingInstruction_partials_instruction_exists_throw_error() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        //instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("2D_BARCODE_PARTIAL");

        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryNumber("1234");

        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(Arrays.asList(instruction));

        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(   "Please complete all Open Instructions for this item before receiving Partial case.", exception.getMessage());



    }

    @Test
    public void testCheckAndValidateExistingInstruction_partials_user_id_mismatch_throw_error() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("testUserId");
        instruction.setLastChangeUserId("testUserId");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("2D_BARCODE_PARTIAL");

        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryNumber("1234");

        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(Arrays.asList(instruction));

        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(   "GLS-RCV-MULTI-INST-400", exception.getMessage());

    }


    @Test
    public void testCheckAndValidateExistingInstruction_partials_existing_instruction() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("2D_BARCODE_PARTIAL");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryNumber("1234");

        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(Arrays.asList(instruction));

        InstructionResponse output = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
        Assert.assertEquals(output.getInstruction(), instruction);
        Assert.assertEquals(output.getDeliveryDocuments(), instructionRequest.getDeliveryDocuments());



    }


    @Test
    public void testCheckAndValidateExistingInstruction_partials_new_instruction() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("2D_BARCODE_PARTIAL");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryNumber("1234");
        DeliveryDocument instructionDeliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(null);
        Mockito.when(rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.when(instructionPersisterService.fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(any(InstructionRequest.class), anyString(), anyString())).thenReturn(instruction);
        InstructionResponse output = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
        Assert.assertEquals(output.getInstruction(), instruction);
        Assert.assertEquals(instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(), instructionDeliveryDocument.getPurchaseReferenceNumber() );
    }


    @Test
    public void testCheckAndValidateExistingInstruction_partials_new_instruction_problem_id() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("2D_BARCODE_PARTIAL");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryNumber("1234");
        instructionRequest.setProblemTagId("prob123");
        DeliveryDocument instructionDeliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(null);
        Mockito.when(rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.when(instructionPersisterService.fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagId(any(InstructionRequest.class), anyString(), anyString())).thenReturn(instruction);
        InstructionResponse output = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
        Assert.assertEquals(output.getInstruction(), instruction);
        Assert.assertEquals(instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(), instructionDeliveryDocument.getPurchaseReferenceNumber() );
    }


    @Test
    public void testCheckAndValidateExistingInstruction_partials_new_instruction_problem_id_sscc_scan() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("sscc");
        instructionRequest.setSscc("testSscc123");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("sscc");
        scannedData1.setValue("testSscc123");
        scannedData1.setApplicationIdentifier("00");

        instructionRequest.setScannedDataList(Arrays.asList(scannedData1));
        instructionRequest.setDeliveryNumber("1234");
        instructionRequest.setProblemTagId("prob123");
        DeliveryDocument instructionDeliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(null);
        Mockito.when(rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.when(instructionPersisterService.fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.doNothing().when(createInstructionDataValidator).validateScannedData(anyMap(), anyString(), anyString());
        InstructionResponse output = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
        Assert.assertEquals(output.getInstruction(), instruction);
        Assert.assertEquals(instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(), instructionDeliveryDocument.getPurchaseReferenceNumber() );
    }

    @Test
    public void testCheckAndValidateExistingInstruction_partials_new_instruction_sscc_scan() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("2026-12-31");
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("sscc");
        instructionRequest.setSscc("testSscc123");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("sscc");
        scannedData1.setValue("testSscc123");
        scannedData1.setApplicationIdentifier("00");

        instructionRequest.setScannedDataList(Arrays.asList(scannedData1));
        instructionRequest.setDeliveryNumber("1234");
        instructionRequest.setProblemTagId(null);
        DeliveryDocument instructionDeliveryDocument = gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
        Mockito.when(instructionPersisterService.findInstructionByDeliveryAndGtin(anyLong())).thenReturn(null);
        Mockito.when(rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.when(instructionPersisterService.fetchInstructionByDeliveryNumberSSCCAndUserIdAndProblemTagId(any(InstructionRequest.class), anyString())).thenReturn(instruction);
        Mockito.doNothing().when(createInstructionDataValidator).validateScannedData(anyMap(), anyString(), anyString());
        Mockito.when(rxInstructionPersisterService.fetchExistingInstructionIfexists(any(InstructionRequest.class))).thenReturn(instruction);
        InstructionResponse output = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output);
        Assert.assertEquals(output.getInstruction(), instruction);
        Assert.assertEquals(instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(), instructionDeliveryDocument.getPurchaseReferenceNumber() );

        instructionRequest.setSscc(null);
        InstructionResponse output1 = createInstructionServiceHelper.checkAndValidateExistingInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(output1);
        Assert.assertEquals(output1.getInstruction(), instruction);
        Assert.assertEquals(instructionRequest.getDeliveryDocuments().get(0).getPurchaseReferenceNumber(), instructionDeliveryDocument.getPurchaseReferenceNumber() );


    }




    @Test
    public void testFilterInstructionMatching2DV2() throws IOException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);
        ssccScanResponse.getContainers().get(0).setGtin("testgtin123");
        ssccScanResponse.getContainers().get(0).setSerial("testserial123");
        ssccScanResponse.getContainers().get(0).setExpiryDate("261231"); // INVALID DATE FORMAT
        ssccScanResponse.getContainers().get(0).setLotNumber("testlot123");
        instruction.setInstructionCreatedByPackageInfo(gson.toJson(ssccScanResponse.getContainers().get(0)));
        instruction.setInstructionCode("RxBuildUnitScan");
        instruction.setCreateUserId("sysadmin");
        instruction.setLastChangeUserId("sysadmin");
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionServiceHelper.filterInstructionMatching2DV2(Arrays.asList(instruction), "testgtin123", "testserial123",  "testlot123", "261231" ));
        Assertions.assertEquals(   "Setup error. Invalid GDM Expiry date.", exception.getMessage());
    }

    @Test
    public void testGetDataForCreateInstruction() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("sscc");
        instructionRequest.setSscc("testSscc123");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));

        DeliveryDocument gdmDeliveryDocument = getDeliveryDocument();

        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());

        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setContainers(ssccScanResponse.getContainers());
        gdmCurrentNodeDetail.setAdditionalInfo(additionalInfo);
        gdmCurrentNodeDetail.setContainers(ssccScanResponse.getContainers());
        gdmDeliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Pair<DeliveryDocument, Long> deliveryDocumentLongPair = new Pair<>(gdmDeliveryDocument, 123L);
        Mockito.when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString())).thenReturn(deliveryDocumentLongPair);

        DataHolder holder = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(holder);
        Assert.assertEquals("FULL-PALLET", holder.getReceivingFlow());

        instructionRequest.setProblemTagId("testProb");

        DataHolder holder1 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(holder1);
        Assert.assertEquals("FULL-PALLET", holder.getReceivingFlow());


    }

    @Test
    public void testGetDataForCreateInstruction_multisku() throws IOException, ReceivingException {
        Instruction instruction = MockInstruction.getInstructionV2("RxBuildContainer");
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("sscc");
        instructionRequest.setSscc("testSscc123");
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));

        DeliveryDocument gdmDeliveryDocument = getDeliveryDocument();

        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        ssccScanResponse.getContainers(), ssccScanResponse.getAdditionalInfo());

        SsccScanResponse.Container gdmContainer = ssccScanResponse.getContainers().get(0);
        gdmContainer.setHints(Arrays.asList("MULTI_SKU_PACKAGE"));
        AdditionalInfo additionalInfo = new AdditionalInfo();
        additionalInfo.setContainers(Arrays.asList(gdmContainer));
        gdmCurrentNodeDetail.setAdditionalInfo(additionalInfo);
        gdmCurrentNodeDetail.setContainers(Arrays.asList(gdmContainer));
        gdmDeliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Pair<DeliveryDocument, Long> deliveryDocumentLongPair = new Pair<>(gdmDeliveryDocument, 123L);
        Mockito.when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString())).thenReturn(deliveryDocumentLongPair);

        DataHolder holder = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(holder);
        Assert.assertEquals("MULTI-SKU", holder.getReceivingFlow());

        instructionRequest.setProblemTagId("testProb");

        DataHolder holder1 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertNotNull(holder1);
        Assert.assertEquals("MULTI-SKU", holder.getReceivingFlow());


    }

    @Test
    public void testGetDataForCreateInstruction_invalid1() throws IOException, ReceivingException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        SsccScanResponse ssccScanResponse1 =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);


        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("sscc");
        ScannedData scannedData1 = new ScannedData();
        scannedData1.setKey("serial");
        scannedData1.setValue("testserial123");
        scannedData1.setApplicationIdentifier("21");

        ScannedData scannedData2 = new ScannedData();
        scannedData2.setKey("gtin");
        scannedData2.setValue("testgtin123");
        scannedData2.setApplicationIdentifier("01");

        ScannedData scannedData3 = new ScannedData();
        scannedData3.setKey("expiryDate");
        scannedData3.setValue("261231");
        scannedData3.setApplicationIdentifier("17");

        ScannedData scannedData4 = new ScannedData();
        scannedData4.setKey("lot");
        scannedData4.setValue("testlot123");
        scannedData4.setApplicationIdentifier("10");
        instructionRequest.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
        instructionRequest.setDeliveryDocuments(Arrays.asList(getDeliveryDocument()));

        DeliveryDocument gdmDeliveryDocument = getDeliveryDocument();
        AdditionalInfo additionalInfo = new AdditionalInfo();
        SsccScanResponse.Container gdmRootContainer = ssccScanResponse1.getContainers().get(0);
        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        SsccScanResponse.Container gdmContainer = ssccScanResponse.getContainers().get(0);
        gdmContainer.setHints(Arrays.asList( "UNIT_ITEM"));
        DeliveryDocument.GdmCurrentNodeDetail gdmCurrentNodeDetail =
                new DeliveryDocument.GdmCurrentNodeDetail(
                        Arrays.asList(gdmContainer), additionalInfo);

        gdmDeliveryDocument.setGdmCurrentNodeDetail(gdmCurrentNodeDetail);

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Pair<DeliveryDocument, Long> deliveryDocumentLongPair = new Pair<>(gdmDeliveryDocument, 123L);
        Mockito.when(instructionHelperService.autoSelectDocumentAndDocumentLineMABD(anyList(), anyInt(), anyString())).thenReturn(deliveryDocumentLongPair);

        Throwable exception = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Unit receiving not allowed for this item, scan the pallet.", exception.getMessage());


        instructionRequest.setProblemTagId("testProb");

        Throwable exception1 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Unit receiving not allowed for this item, scan the pallet.", exception1.getMessage());

        // PASS 2
        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "PARTIAL_PACK_ITEM"));
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setSscc("testsscc");

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Throwable exception2 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Package has a partial case, scan individual cases.", exception2.getMessage());

        // PASS 3 "Perform receiving by Unit scan (partial-case receiving)."
        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "PARTIAL_PACK_ITEM"));
        gdmContainer.setUnitCount(1.0);
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setSscc("testsscc");

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Throwable exception3 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Perform receiving by Unit scan (partial-case receiving).", exception3.getMessage());

        // PASS 4
        instructionRequest.setSscc(null);
        Throwable exception4 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Perform receiving by Unit scan (partial-case receiving).", exception4.getMessage());


        // PASS 3 "Perform receiving by Unit scan (partial-case receiving)."
        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList("CNTR_WITH_MULTI_LABEL_CHILD_CNTRS"));
        gdmContainer.setUnitCount(1.0);
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setSscc("testsscc");

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Throwable exception5 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(    "Please scan individual cases/units to receive.", exception5.getMessage());

        // PASS 3 "Perform receiving by Unit scan (partial-case receiving)."
        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "SSCC_SGTIN_PACKAGE"));
        gdmContainer.setUnitCount(1.0);
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setSscc("testsscc");

        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        Throwable exception6 = Assertions.assertThrows(ReceivingException.class,
                () -> createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders()));
        Assertions.assertEquals(     "Package has a 2D barcode, scan the case 2D barcode.", exception6.getMessage());

        // PASS 3 "Perform receiving by Unit scan (partial-case receiving)."
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE"));
        gdmContainer.setUnitCount(1.0);
        gdmContainer.setChildCount(1.0);
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setReceivingType("MULTI_SKU_FLOW");
        instructionRequest.setProblemTagId(null);
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder3 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("MULTI-SKU-PLT-UNPACKED-AND-RCVD", dataHolder3.getReceivingFlow());

        instructionRequest.setProblemTagId("problem123");
        DataHolder dataHolder4 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("PROBLEM-MULTI-SKU-PLT-UNPACKED-AND-RCVD", dataHolder4.getReceivingFlow());

        gdmContainer.setChildCount(5.0);
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE","HANDLE_AS_CASEPACK"));
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        instructionRequest.setReceivingType("SSCC");
        instructionRequest.setProblemTagId(null);
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder5 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("HNDL-AS-CSPK-PLT-UNPACKED-AND-CASES-RCVD", dataHolder5.getReceivingFlow());


        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK",  "FLOOR_LOADED_PACKAGE"));
        gdmRootContainer.setId("parent1");
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE", "HANDLE_AS_CASEPACK",  "FLOOR_LOADED_PACKAGE"));
        gdmContainer.setParentId("parent1");
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder6 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("HNDL-AS-CSPK-FLOOR-LOADED-CASE", dataHolder6.getReceivingFlow());

        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK",  "FLOOR_LOADED_PACKAGE"));
        gdmRootContainer.setId("parent1");
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE",  "FLOOR_LOADED_PACKAGE"));
        gdmContainer.setParentId("parent1");
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder7 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("FLOOR-LOADED-CASE", dataHolder7.getReceivingFlow());

        gdmRootContainer.setHints(Arrays.asList("CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE"));
        gdmRootContainer.setId("parent1");
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE"));
        gdmContainer.setParentId("parent1");
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder8 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("PLT-UNPACKED-AND-CASES-RCVD", dataHolder8.getReceivingFlow());

        gdmRootContainer.setHints(Arrays.asList("HANDLE_AS_CASEPACK"));
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM", "SINGLE_SKU_PACKAGE", "HANDLE_AS_CASEPACK"));
        gdmContainer.setUnitCount(1.0);
        gdmContainer.setChildCount(1.0);
        instructionRequest.setReceivingType("MULTI_SKU_FLOW");
        instructionRequest.setProblemTagId(null);
        gdmDeliveryDocument.setGdmCurrentNodeDetail( new DeliveryDocument.GdmCurrentNodeDetail(
                Arrays.asList(gdmContainer), additionalInfo));
        Mockito.when(deliveryDocumentsSearchHandlerV2.fetchDeliveryDocument(any(InstructionRequest.class), any(HttpHeaders.class))).thenReturn(Arrays.asList(gdmDeliveryDocument));
        DataHolder dataHolder9 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD", dataHolder9.getReceivingFlow());

        instructionRequest.setProblemTagId("problem123");
        DataHolder dataHolder10 = createInstructionServiceHelper.getDataForCreateInstruction(instructionRequest, MockHttpHeaders.getHeaders());
        Assert.assertEquals("PROBLEM-HNDL-AS-CSPK-MULTI-SKU-PLT-UNPACKED-AND-RCVD", dataHolder10.getReceivingFlow());

    }

    @Test
    public void testIsMultiSkuRootNode() throws IOException {
        File gdmPackResponseFile = new ClassPathResource("currentNodeCase.json").getFile();
        SsccScanResponse ssccScanResponse =
                JacksonParser.convertJsonToObject(
                        new String(Files.readAllBytes(gdmPackResponseFile.toPath())), SsccScanResponse.class);

        AdditionalInfo additionalInfo = new AdditionalInfo();
        SsccScanResponse.Container gdmRootContainer = new SsccScanResponse.Container();
        SsccScanResponse.Container gdmContainer = ssccScanResponse.getContainers().get(0);
        gdmRootContainer.setHints(Arrays.asList("MULTI_SKU_PACKAGE"));
        gdmRootContainer.setId("parent1");
        additionalInfo.setContainers(Arrays.asList(gdmRootContainer));
        gdmContainer.setHints(Arrays.asList( "CASE_PACK_ITEM"));
        gdmContainer.setParentId("parent1");
        ssccScanResponse.setAdditionalInfo(additionalInfo);
        ssccScanResponse.setContainers(Arrays.asList(gdmContainer));

        // SHOULD RETURN TRUE
        Assert.assertTrue(createInstructionServiceHelper.isMultiSkuRootNode(ssccScanResponse));

        gdmContainer.setHints(Arrays.asList( "UNIT_ITEM"));
        ssccScanResponse.setContainers(Arrays.asList(gdmContainer));
        // SHOULD RETURN FALSE
        Assert.assertFalse(createInstructionServiceHelper.isMultiSkuRootNode(ssccScanResponse));

    }


    @Test
    public void testGetReceivingTypeFromUI() {
        InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
        instructionRequest.setReceivingType("SSCC");
        RxReceivingType rxReceivingType = createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest);
        Assert.assertEquals("SSCC", rxReceivingType.getReceivingType());

        instructionRequest.setReceivingType("INVALID");
        Throwable exception = Assertions.assertThrows(ReceivingBadDataException.class,
                () -> createInstructionServiceHelper.getReceivingTypeFromUI(instructionRequest));
        Assertions.assertEquals(   "No Receiving type in the request from client", exception.getMessage());

    }

    @Test
    public void testIsEpcisSmartReceivingEnabledFromClient() {
        HttpHeaders mockHeaders = MockHttpHeaders.getHeaders();
        mockHeaders.add("isEpcisSmartReceivingEnabledFromClient", "true");

        Assert.assertTrue(createInstructionServiceHelper.isEpcisSmartReceivingEnabledFromClient(mockHeaders));
        Assert.assertFalse(createInstructionServiceHelper.isEpcisSmartReceivingEnabledFromClient(MockHttpHeaders.getHeaders()));
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