package com.walmart.move.nim.receiving.rx.common;

import static org.testng.Assert.*;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.rx.constants.RxConstants;
import com.walmart.move.nim.receiving.rx.mock.MockInstruction;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.platform.messages.MetaData;
import com.walmart.platform.repositories.OutboxEvent;
import io.strati.libs.commons.lang3.time.DateUtils;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.junit.jupiter.api.Assertions;
import org.testng.annotations.Test;

public class RxUtilsTest {


  @Test
  public void test_verifyScanned2DWithSerializedInfo_matching_entries() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsEpcisSmartReceivingEnabled(true);
    additionalInfo.setIsEpcisEnabledVendor(true);
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setSerial("testserial");
    manufactureDetail.setGtin("200109395464720439");
    manufactureDetail.setExpiryDate("2026-12-31");
    manufactureDetail.setLot("testLot");
    additionalInfo.setSerializedInfo(Arrays.asList(manufactureDetail));
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

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
    Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1,scannedData2,scannedData3,scannedData4));

    // EXCEPTION IS NOT THROWN
    Assertions.assertDoesNotThrow(() ->  RxUtils.verifyScanned2DWithSerializedInfo(deliveryDocumentLine, scannedDataMap));

  }

  @Test
  public void test_verifyScanned2DWithSerializedInfo_mismatching_entries() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsEpcisSmartReceivingEnabled(true);
    additionalInfo.setIsEpcisEnabledVendor(true);
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setSerial("somerandomserial");
    manufactureDetail.setGtin("200109395464720439");
    manufactureDetail.setExpiryDate("2026-12-31");
    manufactureDetail.setLot("testLot");
    additionalInfo.setSerializedInfo(Arrays.asList(manufactureDetail));
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

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
    Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(Arrays.asList(scannedData1,scannedData2,scannedData3,scannedData4));

    Assertions.assertThrows(ReceivingBadDataException.class, () ->  RxUtils.verifyScanned2DWithSerializedInfo(deliveryDocumentLine, scannedDataMap));

  }

  @Test
  public void test_isDscsaExemptionIndEnabled_happy_path() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsDscsaExemptionInd(true);

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    assertTrue(RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false));
  }

  @Test
  public void test_isDscsaExemptionIndEnabled_happy_path_flag_disabled() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsDscsaExemptionInd(false);

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    assertFalse(RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false));
  }

  @Test
  public void test_isDscsaExemptionIndEnabled_D40_CheckDeptFeatureFlag() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setDepartment("40");

    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsDscsaExemptionInd(false);

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    assertTrue(RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, true));
  }

  public void test_isDscsaExemptionIndEnabled_missing_DscsaExemptionInd() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    ItemData additionalInfo = new ItemData();

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false);
  }

  public void test_isDscsaExemptionIndEnabled_missing_DscsaExemptionInd_default_false() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    ItemData additionalInfo = new ItemData();

    deliveryDocumentLine.setAdditionalInfo(additionalInfo);

    assertFalse(RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false));
  }

  public void test_isDscsaExemptionIndEnabled_missing_ItemData() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false);
  }

  public void test_isDscsaExemptionIndEnabled_missing_ItemData_default_false() {

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();

    assertFalse(RxUtils.isDscsaExemptionIndEnabled(deliveryDocumentLine, false));
  }

  @Test
  public void test_isMultiSKUPallet() {

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();

    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setItemNbr(12345l);
    deliveryDocumentLine1.setGtin("00369315312285");

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setItemNbr(987654l);
    deliveryDocumentLine2.setGtin("00369315312286");

    deliveryDocument1.setDeliveryDocumentLines(
        Arrays.asList(deliveryDocumentLine1, deliveryDocumentLine2));

    assertTrue(RxUtils.isMultiSKUPallet(Arrays.asList(deliveryDocument1)));
  }

  @Test
  public void test_isMultiSKUPallet_false() {

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();

    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setItemNbr(12345l);
    deliveryDocumentLine1.setGtin("00369315312285");

    deliveryDocument1.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine1));

    assertFalse(RxUtils.isMultiSKUPallet(Arrays.asList(deliveryDocument1)));
  }

  @Test
  public void test_isPartialCase() {
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setQty(20);
    manufactureDetail.setReportedUom(ReceivingConstants.Uom.WHPK);
    Pair<Boolean, Integer> partialCaseDetails =
        RxUtils.isPartialCaseAndReportedQty(Arrays.asList(manufactureDetail), 48, 2);
    assertTrue(partialCaseDetails.getKey());
  }

  @Test
  public void test_isPartialCase_false() {
    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setQty(48);
    manufactureDetail.setReportedUom(ReceivingConstants.Uom.WHPK);
    Pair<Boolean, Integer> partialCaseDetails =
        RxUtils.isPartialCaseAndReportedQty(Arrays.asList(manufactureDetail), 48, 2);
    assertFalse(partialCaseDetails.getKey());
  }

  @Test
  public void test_is2DScanInstructionRequest_returns_true() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey("lot");
    lotNumberScannedData.setApplicationIdentifier("10");
    lotNumberScannedData.setValue("1131444");
    scannedDataList.add(lotNumberScannedData);
    assertTrue(RxUtils.is2DScanInstructionRequest(scannedDataList));
  }

  @Test
  public void test_is2DScanInstructionRequest_returns_false() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setKey("gtin");
    lotNumberScannedData.setApplicationIdentifier("01");
    lotNumberScannedData.setValue("1131444");
    scannedDataList.add(lotNumberScannedData);
    assertFalse(RxUtils.is2DScanInstructionRequest(scannedDataList));
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_CloseDatedItem() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expiryScannedData = new ScannedData();
    expiryScannedData.setKey("exp");
    expiryScannedData.setApplicationIdentifier("17");
    expiryScannedData.setValue("102112");
    scannedDataList.add(expiryScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expiryScannedData);

    RxUtils.checkIfContainerIsCloseDated(scannedDataMap, 365, "", null);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_InvalidDate() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expiryScannedData = new ScannedData();
    expiryScannedData.setKey("exp");
    expiryScannedData.setApplicationIdentifier("17");
    expiryScannedData.setValue("aaaaaa");
    scannedDataList.add(expiryScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expiryScannedData);

    RxUtils.checkIfContainerIsCloseDated(scannedDataMap, 365, "", null);
  }

  @Test
  public void test_closeDatedItem_ProblemFlow() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    LocalDate now = LocalDate.now();
    LocalDate futureDate = now.plus(100, ChronoUnit.DAYS);
    expDateScannedData.setValue(futureDate.format(DateTimeFormatter.ofPattern("yyMMdd")));
    scannedDataList.add(expDateScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expDateScannedData);
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Issue issue = new Issue();
    issue.setType(ReceivingConstants.FIXIT_ISSUE_TYPE_DI);
    fitProblemTagResponse.setIssue(issue);
    RxUtils.checkIfContainerIsCloseDated(
        scannedDataMap, 365, "problemLabel", fitProblemTagResponse);
  }

  @Test(expectedExceptions = ReceivingBadDataException.class)
  public void test_closeDatedItem_ProblemFlow_ExpiredItem() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    LocalDate now = LocalDate.now();
    LocalDate futureDate = now.plus(-1, ChronoUnit.DAYS);
    expDateScannedData.setValue(futureDate.format(DateTimeFormatter.ofPattern("yyMMdd")));
    scannedDataList.add(expDateScannedData);
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, expDateScannedData);
    FitProblemTagResponse fitProblemTagResponse = new FitProblemTagResponse();
    Issue issue = new Issue();
    issue.setType(ReceivingConstants.FIXIT_ISSUE_TYPE_DI);
    fitProblemTagResponse.setIssue(issue);
    RxUtils.checkIfContainerIsCloseDated(
        scannedDataMap, 365, "problemLabel", fitProblemTagResponse);
  }

  @Test
  public void test_validateScannedDataForUpcAndLotNumber() {
    List<ScannedData> scannedDataList = new ArrayList<>();
    Map<String, ScannedData> scannedDataMap = RxUtils.scannedDataMap(scannedDataList);

    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INVALID_SCANNED_DATA);
      assertEquals(rbde.getDescription(), RxConstants.INVALID_SCANNED_DATA);
    }

    scannedDataList.add(new ScannedData());

    scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INVALID_SCANNED_DATA_GTIN);
      assertEquals(rbde.getDescription(), RxConstants.INVALID_SCANNED_DATA_GTIN);
    }

    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setKey("gtin");
    gtinScannedData.setApplicationIdentifier("01");
    gtinScannedData.setValue("1131444");
    scannedDataList.add(gtinScannedData);

    scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INVALID_SCANNED_DATA_LOT);
      assertEquals(rbde.getDescription(), RxConstants.INVALID_SCANNED_DATA_LOT);
    }

    ScannedData lotScannedData = new ScannedData();
    lotScannedData.setKey("lot");
    lotScannedData.setApplicationIdentifier("10");
    lotScannedData.setValue("00L032C09A");
    scannedDataList.add(lotScannedData);

    scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INVALID_SCANNED_DATA_SERIAL);
      assertEquals(rbde.getDescription(), RxConstants.INVALID_SCANNED_DATA_SERIAL);
    }

    ScannedData serialNumberScannedData = new ScannedData();
    serialNumberScannedData.setKey("serial");
    serialNumberScannedData.setApplicationIdentifier("21");
    serialNumberScannedData.setValue("2079830991");
    scannedDataList.add(serialNumberScannedData);

    scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      assertEquals(rbde.getErrorCode(), ExceptionCodes.INVALID_SCANNED_DATA_EXPIRY_DATE);
      assertEquals(rbde.getDescription(), RxConstants.INVALID_SCANNED_DATA_EXPIRY_DATE);
    }

    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setKey("expiryDate");
    expDateScannedData.setApplicationIdentifier("17");
    expDateScannedData.setValue("220331");
    scannedDataList.add(expDateScannedData);

    scannedDataMap = RxUtils.scannedDataMap(scannedDataList);
    try {
      RxUtils.validateScannedDataForUpcAndLotNumber(scannedDataMap);
    } catch (ReceivingBadDataException rbde) {
      fail(rbde.getMessage());
    }
  }

  @Test
  public void test_checkIfContainerIsCloseDated_expired_item() {

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData mockDateScannedData = new ScannedData();
    mockDateScannedData.setValue(
        DateFormatUtils.format(
            DateUtils.addDays(new Date(), -1), ReceivingConstants.EXPIRY_DATE_FORMAT));
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, mockDateScannedData);

    int closeDateLimitDays = 0; // Expired items
    String problemTagId = "DUMMY_PROBLEM_TAG";
    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Issue mockIssue = new Issue();
    mockIssue.setType("DI");
    mockFitProblemTagResponse.setIssue(mockIssue);
    try {
      RxUtils.checkIfContainerIsCloseDated(
          scannedDataMap, closeDateLimitDays, problemTagId, mockFitProblemTagResponse);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.EXPIRED_ITEM);
      assertEquals(e.getDescription(), RxConstants.EXPIRED_ITEM);
    }
  }

  @Test
  public void test_checkIfContainerIsCloseDated_less_than_365() {

    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    ScannedData mockDateScannedData = new ScannedData();
    mockDateScannedData.setValue(
        DateFormatUtils.format(
            DateUtils.addDays(new Date(), 364), ReceivingConstants.EXPIRY_DATE_FORMAT));
    scannedDataMap.put(ReceivingConstants.KEY_EXPIRY_DATE, mockDateScannedData);

    try {
      RxUtils.checkIfContainerIsCloseDated(scannedDataMap, 365, null, null);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), ExceptionCodes.CLOSE_DATED_ITEM);
      assertEquals(e.getDescription(), RxConstants.CLOSE_DATED_ITEM);
    }
  }

  @Test
  public void test_findEachQtySummary() throws Exception {

    int eachQty =
        RxUtils.findEachQtySummary(MockInstruction.getInstructionWithManufactureDetails());
    assertEquals(eachQty, 2);
  }

  @Test
  public void test_verify2DBarcodeLotWithShipmentLot() {

    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    ManufactureDetail mockManufactureDetails = new ManufactureDetail();
    mockManufactureDetails.setLot("MOCK_LOT");
    mockDeliveryDocumentLine.setManufactureDetails(Arrays.asList(mockManufactureDetails));

    Map<String, ScannedData> mockScannedDataMap = new HashMap<>();
    ScannedData mockScannedData = new ScannedData();
    mockScannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getKey());
    mockScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    mockScannedData.setValue("MOCK_LOT");

    mockScannedDataMap.put(ApplicationIdentifier.LOT.getKey(), mockScannedData);

    RxUtils.verify2DBarcodeLotWithShipmentLot(
        true, mockDeliveryDocument, mockDeliveryDocumentLine, mockScannedDataMap, false);
  }

  @Test
  public void test_verify2DBarcodeLotWithShipmentLot_lower_case() {

    DeliveryDocument mockDeliveryDocument = new DeliveryDocument();
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    ManufactureDetail mockManufactureDetails = new ManufactureDetail();
    mockManufactureDetails.setLot("MOCK_LOT");
    mockDeliveryDocumentLine.setManufactureDetails(Arrays.asList(mockManufactureDetails));

    Map<String, ScannedData> mockScannedDataMap = new HashMap<>();
    ScannedData mockScannedData = new ScannedData();
    mockScannedData.setApplicationIdentifier(ApplicationIdentifier.LOT.getKey());
    mockScannedData.setKey(ApplicationIdentifier.LOT.getKey());
    mockScannedData.setValue("mock_lot");

    mockScannedDataMap.put(ApplicationIdentifier.LOT.getKey(), mockScannedData);

    RxUtils.verify2DBarcodeLotWithShipmentLot(
        true, mockDeliveryDocument, mockDeliveryDocumentLine, mockScannedDataMap, false);
  }

  @Test
  public void test_deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow() {
    DeliveryDocumentLine mockDeliveryDocumentLine = new DeliveryDocumentLine();
    mockDeliveryDocumentLine.setTotalOrderQty(100);
    mockDeliveryDocumentLine.setOverageQtyLimit(50);
    mockDeliveryDocumentLine.setShippedQty(10);
    mockDeliveryDocumentLine.setShippedQtyUom("ZA");
    mockDeliveryDocumentLine.setVendorPack(15);
    mockDeliveryDocumentLine.setWarehousePack(5);
    int result =
        RxUtils.deriveProjectedReceiveQtyInEachesForEpcisEnabledFlow(
            mockDeliveryDocumentLine, 0, 150);
    assertEquals(result, 150);
  }

  @Test
  public void test_buildOutboxEvent() {
    // when
    OutboxEvent outboxEvent =
        RxUtils.buildOutboxEvent(
            Collections.singletonMap("key", "value"),
            "body",
            "eventId",
            MetaData.with("meta", "value"),
            "policyId",
            Instant.now());
    // then
    assertNotNull(outboxEvent);
    assertNotNull(outboxEvent.getEventIdentifier());
    assertNotNull(outboxEvent.getMetaData());
    assertNotNull(outboxEvent.getPublisherPolicyId());
    assertNotNull(outboxEvent.getPayloadRef());
  }

  @Test
  public void test_isItemXBlocked_asnEpcis() {
    // given
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setHandlingCode("X");
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    // when
    boolean itemXBlocked = RxUtils.isItemXBlocked(deliveryDocumentLine);
    // then
    assertTrue(itemXBlocked);
  }

  @Test
  public void test_isControlledSubstance() {
    // given
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    ItemData additionalInfo = new ItemData();
    additionalInfo.setIsControlledSubstance(true);
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    // when
    boolean isControlledSubstance = RxUtils.isControlledSubstance(deliveryDocumentLine);
    // then
    assertTrue(isControlledSubstance);
  }

  @Test
  public void test_isItemXBlocked_upc() {
    // given
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setHandlingCode("X");
    ItemData additionalInfo = new ItemData();
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    // when
    boolean itemXBlocked = RxUtils.isItemXBlocked(deliveryDocumentLine);
    // then
    assertTrue(itemXBlocked);
  }
}
