package com.walmart.move.nim.receiving.rx.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.rx.service.v2.data.CreateInstructionServiceHelper;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.mockito.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class RxInstructionPersisterServiceTest {

  @Mock private InstructionRepository instructionRepository;
  @Mock private InstructionPersisterService instructionPersisterService;
  @Spy private CreateInstructionServiceHelper createInstructionServiceHelper;

  @InjectMocks private RxInstructionPersisterService rxInstructionPersisterService;

  private static Gson gson = new Gson();
  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    TenantContext.setFacilityCountryCode("US");
    TenantContext.setFacilityNum(32897);
  }

  @AfterMethod
  public void afterMethod() {
    reset(instructionRepository);
  }


  @Test
  public void test_fetchExistingInstructionIfExists() {
    InstructionRequest request = MockInstruction.getInstructionRequest();
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
    request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
    request.setReceivingType("SPLIT_PALLET_2D_BARCODE");
    request.setInstructionSetId(123L);
    Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");

    // SET INSTRUCTION MESSAGE
    mockInstruction.setInstructionMsg("RxSerCntrCaseScan");
    Mockito.when(instructionRepository.findByMessageId(anyString())).thenReturn(mockInstruction);
    Instruction returnedObj = rxInstructionPersisterService.fetchExistingInstructionIfexists(request);
    Assert.assertNotNull(returnedObj); // RETURNED INSTRUCTION IS NOT NULL

    // SET InstructionMessage to NULL
    mockInstruction.setInstructionMsg(null);
    Mockito.when(instructionRepository.findByMessageId(anyString())).thenReturn(mockInstruction);
    Instruction returnedObj1 = rxInstructionPersisterService.fetchExistingInstructionIfexists(request);
    Assert.assertNull(returnedObj1); // RETURNED INSTRUCTION IS NULL


  }

  @Test
  public void test_fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2_split_pallet_with_set_id() {
    InstructionRequest request = MockInstruction.getInstructionRequest();
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
    request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
    request.setReceivingType("SPLIT_PALLET_2D_BARCODE");
    request.setInstructionSetId(123L);
    Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");

    SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
    gdmContainer.setGtin("200109395464720439");
    gdmContainer.setSerial("testserial");
    gdmContainer.setExpiryDate("2026-12-31");
    gdmContainer.setLotNumber("testLot");
    mockInstruction.setInstructionCreatedByPackageInfo(gson.toJson(gdmContainer));
    mockInstruction.setCreateUserId("sysadmin");
    mockInstruction.setLastChangeUserId("sysadmin");
    mockInstruction.setInstructionMsg("RxSerCntrCaseScan");
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(anyLong(),
                    anyString(), anyString(), anyLong())).thenReturn(Arrays.asList(mockInstruction));
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));

    Instruction returnedObj = rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(request, "sysadmin");

    Assert.assertNotNull(returnedObj);
    Mockito.verify(instructionRepository).
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(anyLong(),
                    anyString(), anyString(), anyLong());

  }


  @Test
  public void test_fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2_split_pallet_with_no_set_id() {
    InstructionRequest request = MockInstruction.getInstructionRequest();
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
    request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
    request.setReceivingType("SPLIT_PALLET_2D_BARCODE");
    request.setInstructionSetId(null);
    Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");

    SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
    gdmContainer.setGtin("200109395464720439");
    gdmContainer.setSerial("testserial");
    gdmContainer.setExpiryDate("2026-12-31");
    gdmContainer.setLotNumber("testLot");
    mockInstruction.setInstructionCreatedByPackageInfo(gson.toJson(gdmContainer));
    mockInstruction.setCreateUserId("sysadmin");
    mockInstruction.setLastChangeUserId("sysadmin");
    mockInstruction.setInstructionMsg("RxSerCntrCaseScan");
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(anyLong(),
                    anyString(), anyString(), anyLong())).thenReturn(Arrays.asList(mockInstruction));

    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));

    Instruction returnedObj = rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(request, "sysadmin");

    Assert.assertNotNull(returnedObj);
    Mockito.verify(instructionRepository).
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(anyLong(),
                    anyString(), anyString());

  }

  @Test
  public void test_fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2_regular_recev() {
    InstructionRequest request = MockInstruction.getInstructionRequest();
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
    request.setScannedDataList(Arrays.asList(scannedData1, scannedData2, scannedData3, scannedData4));
    request.setReceivingType("2D_BARCODE");
    request.setInstructionSetId(null);
    Instruction mockInstruction = MockInstruction.getInstructionV2("RxSerCntrCaseScan");

    SsccScanResponse.Container gdmContainer = new SsccScanResponse.Container();
    gdmContainer.setGtin("200109395464720439");
    gdmContainer.setSerial("testserial");
    gdmContainer.setExpiryDate("2026-12-31");
    gdmContainer.setLotNumber("testLot");
    mockInstruction.setInstructionCreatedByPackageInfo(gson.toJson(gdmContainer));
    mockInstruction.setCreateUserId("sysadmin");
    mockInstruction.setLastChangeUserId("sysadmin");
    mockInstruction.setInstructionMsg("RxSerCntrCaseScan");
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(anyLong(),
                    anyString(), anyString(), anyLong())).thenReturn(Arrays.asList(mockInstruction));
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));
    Mockito.when(instructionRepository.
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(anyLong(),
                    anyString(), anyString())).thenReturn(Arrays.asList(mockInstruction));

    Instruction returnedObj = rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdV2(request, "sysadmin");


    Assert.assertNotNull(returnedObj);
    Mockito.verify(instructionRepository).
            findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(anyLong(),
                    anyString(), anyString());

  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId() {

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");

    doReturn(mockInstruction4mDB)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNotNull(fetchInstructionBySSCCAndUserIdResponse);
    assertSame(fetchInstructionBySSCCAndUserIdResponse.getId(), mockInstruction4mDB.getId());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId_splitPallet() {

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");
    mockInstruction4mDB.setInstructionSetId(1l);

    doReturn(mockInstruction4mDB)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");
    mockInstructionRequest.setInstructionSetId(1l);
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_SSCC");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNotNull(fetchInstructionBySSCCAndUserIdResponse);
    assertSame(fetchInstructionBySSCCAndUserIdResponse.getId(), mockInstruction4mDB.getId());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyString(), anyLong());
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId_splitPallet_first_item() {

    Instruction mockInstruction4mDB = MockInstruction.getCreatedInstruction();
    mockInstruction4mDB.setCreateUserId("rxTestUser");
    mockInstruction4mDB.setInstructionSetId(1l);

    doReturn(mockInstruction4mDB)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
            anyLong(), anyString(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_SSCC");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNotNull(fetchInstructionBySSCCAndUserIdResponse);
    assertSame(fetchInstructionBySSCCAndUserIdResponse.getId(), mockInstruction4mDB.getId());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void test_fetchInstructionBySSCCAndUserId_none_indb() {

    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setSscc("00100700302232310006");
    mockInstructionRequest.setDeliveryNumber("12345");

    Instruction fetchInstructionBySSCCAndUserIdResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryNumberAndSSCCAndUserId(
            mockInstructionRequest, "rxTestUser");

    assertNull(fetchInstructionBySSCCAndUserIdResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndSsccNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString(), anyString());
  }

  @Test
  public void
      test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setProblemTagId("MOCK_PROBLEM_TAG_ID");

    String mockUserId = "MOCK_UNIT_TEST_USER";

    Instruction mockInstructionWithRxDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithRxDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithRxDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());

    rxInstructionPersisterService
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            mockInstructionRequest, mockUserId);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());
  }

  @Test
  public void
      test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull_splitPallet() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setInstructionSetId(1l);
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_2D_BARCODE");

    String mockUserId = "MOCK_UNIT_TEST_USER";

    Instruction mockInstructionWithRxDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithRxDetails.setCreateUserId(mockUserId);
    mockInstructionWithRxDetails.setInstructionSetId(1l);

    doReturn(Arrays.asList(mockInstructionWithRxDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyLong());

    rxInstructionPersisterService
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            mockInstructionRequest, mockUserId);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetId(
            anyLong(), anyString(), anyString(), anyLong());
  }

  @Test
  public void
      test_fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull_splitPallet_first_item() {

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setUpcNumber("00000943037194");
    mockInstructionRequest.setSscc("MOCK_SSCC_CODE");
    mockInstructionRequest.setDeliveryNumber("1234");
    mockInstructionRequest.setReceivingType("SPLIT_PALLET_2D_BARCODE");

    String mockUserId = "MOCK_UNIT_TEST_USER";

    Instruction mockInstructionWithRxDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithRxDetails.setCreateUserId(mockUserId);
    mockInstructionWithRxDetails.setInstructionSetId(1l);

    doReturn(Arrays.asList(mockInstructionWithRxDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
            anyLong(), anyString(), anyString());

    rxInstructionPersisterService
        .fetchInstructionByDeliveryAndGtinAndUserIdOrLastChangeUserIdAndProblemTagIdIsNull(
            mockInstructionRequest, mockUserId);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNotNull(
            anyLong(), anyString(), anyString());
  }

  @Test
  public void fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdReturnsInstruction() {
    String mockUserId = "sysadmin";
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setKey(ReceivingConstants.KEY_GTIN);
    gtinScannedData.setValue("10368645556540");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getKey());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");
    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setApplicationIdentifier(ApplicationIdentifier.SERIAL.getKey());
    serialScannedData.setKey(ReceivingConstants.KEY_SERIAL);
    serialScannedData.setValue("1234567890");
    scannedDataList.add(gtinScannedData);
    scannedDataList.add(lotNumberScannedData);
    scannedDataList.add(serialScannedData);
    Instruction mockInstructionWithManufactureDetails =
        MockInstruction.getInstructionWithManufactureDetails();
    mockInstructionWithManufactureDetails.setCreateUserId(mockUserId);

    doReturn(Arrays.asList(mockInstructionWithManufactureDetails))
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setDeliveryNumber("798001");
    mockInstructionRequest.setUpcNumber("10368645556540");
    mockInstructionRequest.setScannedDataList(scannedDataList);

    Instruction instructionResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            mockInstructionRequest, mockUserId);
    assertNotNull(instructionResponse);

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());
  }

  @Test
  public void fetchInstructionByDeliveryAndGtinAndLotNumberAndUserIdReturnsNull() {
    String mockUserId = "sysadmin";
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ReceivingConstants.KEY_GTIN);
    gtinScannedData.setValue("00028000114603");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(
        ApplicationIdentifier.LOT.getApplicationIdentifier());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");
    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setApplicationIdentifier(
        ApplicationIdentifier.SERIAL.getApplicationIdentifier());
    serialScannedData.setKey(ReceivingConstants.KEY_SERIAL);
    serialScannedData.setValue("1234567890");
    scannedDataList.add(gtinScannedData);
    scannedDataList.add(lotNumberScannedData);
    scannedDataList.add(serialScannedData);
    doReturn(null)
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());

    InstructionRequest mockInstructionRequest = new InstructionRequest();
    mockInstructionRequest.setDeliveryNumber("798001");
    mockInstructionRequest.setScannedDataList(scannedDataList);

    Instruction instructionResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            mockInstructionRequest, mockUserId);
    assertNull(instructionResponse);
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "A new pallet cannot be created until the pallets owned by user mock_unit_test_user for this item are completed. Please work on another item or request for pallet transfer.")
  public void test_checkIfNewInstructionCanBeCreated() throws ReceivingException {

    doReturn(1l)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    doReturn(1)
        .when(instructionPersisterService)
        .findNonSplitPalletInstructionCount(anyString(), anyInt());
    doReturn(Arrays.asList("mock_unit_test_user"))
        .when(instructionRepository)
        .getOpenInstructionsLastChangedUserByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt());

    rxInstructionPersisterService.checkIfNewInstructionCanBeCreated(
        "123", 1, 10, 10, false, "sysadmin1");

    verify(instructionRepository, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    verify(instructionPersisterService, times(1))
        .findNonSplitPalletInstructionCount(anyString(), anyInt());
    verify(instructionRepository, times(1))
        .getOpenInstructionsUsersByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt());
  }

  @Test(
      expectedExceptions = ReceivingException.class,
      expectedExceptionsMessageRegExp =
          "A new pallet cannot be created until open instructions for this item is completed. Please work on another item.")
  public void test_checkIfNewInstructionCanBeCreated_split_pallet() throws ReceivingException {

    doReturn(1l)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    doReturn(0)
        .when(instructionPersisterService)
        .findNonSplitPalletInstructionCount(anyString(), anyInt());
    doReturn(Arrays.asList("mock_unit_test_user"))
        .when(instructionRepository)
        .getOpenInstructionsUsersByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt());

    rxInstructionPersisterService.checkIfNewInstructionCanBeCreated(
        "123", 1, 10, 10, true, "sysadmin");

    verify(instructionRepository, times(1))
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    verify(instructionPersisterService, times(1))
        .findNonSplitPalletInstructionCount(anyString(), anyInt());
    verify(instructionRepository, times(1))
        .getOpenInstructionsUsersByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            anyString(), anyInt());
  }

  @Test
  public void fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId_EpcisMatch() {
    // given
    String mockUserId = "sysadmin";
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getKey());
    scannedData.setKey(ReceivingConstants.KEY_GTIN);
    scannedData.setValue("10368645556540");
    scannedDataList.add(scannedData);

    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getKey());
    scannedData.setKey(ReceivingConstants.KEY_LOT);
    scannedData.setValue("ABCDEF1234");
    scannedDataList.add(scannedData);

    scannedData = new ScannedData();
    scannedData.setApplicationIdentifier(ApplicationIdentifier.SERIAL.getKey());
    scannedData.setKey(ReceivingConstants.KEY_SERIAL);
    scannedData.setValue("1234567890");
    scannedDataList.add(scannedData);

    Instruction instruction = MockInstruction.getInstructionWithEpcis();
    instruction.setCreateUserId(mockUserId);

    doReturn(Collections.singletonList(instruction))
        .when(instructionRepository)
        .findByDeliveryNumberAndCreateUserIdOrLastChangeUserIdAndCompleteTsIsNullAndProblemTagIdIsNullAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyString());

    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setDeliveryNumber("798001");
    instructionRequest.setUpcNumber("10368645556540");
    instructionRequest.setScannedDataList(scannedDataList);

    // when
    Instruction instructionResponse =
        rxInstructionPersisterService.fetchInstructionByDeliveryAndGtinAndLotNumberAndUserId(
            instructionRequest, mockUserId);

    // then
    assertNotNull(instructionResponse);
  }
}
