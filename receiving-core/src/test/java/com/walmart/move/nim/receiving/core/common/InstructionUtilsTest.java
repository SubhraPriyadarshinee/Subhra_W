package com.walmart.move.nim.receiving.core.common;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CANCELLED_PO_INSTRUCTION_MORE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.CLOSE_DATE_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.EXPIRED_PRODUCT_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.INVALID_EXP_DATE_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.PAST_DATE_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_CODE;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_HEADER;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_MGR_MSG;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.UPDATE_DATE_ERROR_MSG;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DEFAULT_OPEN_QTY_CALCULATOR;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.OPEN_QTY_CALCULATOR;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigApiClient;
import com.walmart.move.nim.receiving.core.client.itemconfig.ItemConfigRestApiClientException;
import com.walmart.move.nim.receiving.core.client.itemconfig.model.ItemConfigDetails;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.mock.data.MockDeliveryDocuments;
import com.walmart.move.nim.receiving.core.mock.data.MockInstruction;
import com.walmart.move.nim.receiving.core.mock.data.MockTransportationModes;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.DefaultOpenQtyCalculator;
import com.walmart.move.nim.receiving.core.service.FbqBasedOpenQtyCalculator;
import com.walmart.move.nim.receiving.core.service.OpenQtyCalculator;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.commons.lang3.tuple.ImmutablePair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.testng.AssertJUnit;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests to validate methods in instruction utils having logic
 *
 * @author g0k0072
 */
@ActiveProfiles("test")
public class InstructionUtilsTest extends ReceivingTestBase {

  private Gson gson = new Gson();
  private Instruction instruction;
  private DocumentLine documentLine;
  private HttpHeaders httpHeaders = GdcHttpHeaders.getHeaders();
  private List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocumentsForDSDC();
  private Instruction instructionWithDocument;
  final String INVALID_EXP_DATE_ERROR_MSG_PARTIAL =
      ", doesn't meet this item's minimum life expectancy threshold of ";
  final String PAST_DATE_MSG_PARTIAL = ", is earlier than today's date, ";
  private static final String itemNumber = "5689452";
  private static final String itemDesc = "test desc";
  private static final String createTs = "2021-08-11T03:48:27.133Z";

  public static final String UNLOADER = "UNLR"; // WFT to use this user Role for Performance for GDC

  @Mock private ItemConfigApiClient itemConfigApiClient;
  @Mock private TenantSpecificConfigReader configUtils;
  @InjectMocks private InstructionUtils instructionUtils;
  @InjectMocks @Spy private FbqBasedOpenQtyCalculator fbqBasedOpenQtyCalculator;
  @InjectMocks @Spy private DefaultOpenQtyCalculator defaultOpenQtyCalculator;
  @Mock private InstructionRepository instructionRepository;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);
    ReflectionTestUtils.setField(instructionUtils, "itemConfigApiClient", itemConfigApiClient);
    TenantContext.setFacilityNum(32612);
    TenantContext.setFacilityCountryCode("US");

    instruction = new Instruction();
    documentLine = new DocumentLine();
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseReferenceNumber("4166030001");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setPoDCNumber("32612");
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setPurchaseCompanyId(1);
    documentLine.setDeptNumber(14);
    documentLine.setPoDeptNumber("14");
    documentLine.setGtin("00028000114602");
    documentLine.setItemNumber(573170821L);
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setVendorPackCost(15.88);
    documentLine.setWhpkSell(16.98);
    documentLine.setMaxOverageAcceptQty(10L);
    documentLine.setExpectedQty(100L);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");
    documentLine.setRotateDate(new Date());
    documentLine.setVnpkWgtQty(12.36F);
    documentLine.setVnpkWgtUom("lb");
    documentLine.setVnpkcbqty(0.533F);
    documentLine.setVnpkcbuomcd("CF");
    documentLine.setDescription("TEST ITEM DESCR   ");
    documentLine.setWarehouseMinLifeRemainingToReceive(0);

    instructionWithDocument = new Instruction();
    String deliveryDocumentJsonString =
        "{\"purchaseReferenceNumber\":\"8222248770\",\"financialGroupCode\":\"US\",\"baseDivCode\":\"WM\",\"vendorNumber\":\"480889\",\"vendorNbrDeptSeq\":\"480889940\",\"deptNumber\":\"94\",\"purchaseCompanyId\":\"1\",\"purchaseReferenceLegacyType\":\"28\",\"poDCNumber\":\"32612\",\"purchaseReferenceStatus\":\"ACTV\",\"deliveryDocumentLines\":[{\"gtin\":\"01123840356119\",\"itemUPC\":\"01123840356119\",\"caseUPC\":\"11188122713797\",\"purchaseReferenceNumber\":\"8222248770\",\"purchaseReferenceLineNumber\":1,\"event\":\"POS REPLEN\",\"purchaseReferenceLineStatus\":\"ACTIVE\",\"whpkSell\":23.89,\"vendorPackCost\":23.89,\"vnpkQty\":6,\"whpkQty\":6,\"orderableQuantity\":11,\"warehousePackQuantity\":11,\"expectedQtyUOM\":\"ZA\",\"openQty\":0,\"expectedQty\":81,\"overageQtyLimit\":20,\"itemNbr\":9773149,\"purchaseRefType\":\"SSTKU\",\"palletTi\":9,\"palletHi\":9,\"vnpkWgtQty\":10.0,\"vnpkWgtUom\":\"LB\",\"vnpkcbqty\":0.852,\"vnpkcbuomcd\":\"CF\",\"color\":\"8DAYS\",\"size\":\"EA\",\"isHazmat\":false,\"itemDescription1\":\"4\\\" MKT BAN CRM N\",\"itemDescription2\":\"\\u003cT\\u0026S\\u003e\",\"isConveyable\":false,\"warehouseRotationTypeCode\":\"3\",\"firstExpiryFirstOut\":true,\"warehouseMinLifeRemainingToReceive\":30,\"profiledWarehouseArea\":\"CPS\",\"promoBuyInd\":\"N\",\"additionalInfo\":{\"warehouseAreaCode\":\"4\",\"warehouseGroupCode\":\"DD\",\"isNewItem\":false,\"profiledWarehouseArea\":\"CPS\",\"warehouseRotationTypeCode\":\"3\",\"recall\":false,\"weight\":3.325,\"weightFormatTypeCode\":\"V\",\"weightUOM\":\"LB\",\"warehouseMinLifeRemainingToReceive\":30},\"operationalInfo\":{\"state\":\"ACTIVE\"},\"freightBillQty\":243,\"bolWeight\":0.4115,\"activeChannelMethods\":[]}],\"totalPurchaseReferenceQty\":243,\"weight\":0.0,\"cubeQty\":0.0,\"freightTermCode\":\"PRP\",\"deliveryStatus\":\"WRK\",\"poTypeCode\":28,\"totalBolFbq\":0,\"deliveryLegacyStatus\":\"WRK\"}";
    instructionWithDocument.setDeliveryDocument(deliveryDocumentJsonString);
  }

  @AfterMethod
  public void resetMocks() {
    reset(itemConfigApiClient);
  }

  @Test
  public void testGetMoveQuantity_whenContainerHasContainerItems() {
    Container container = new Container();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setQuantity(5);
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setQuantity(3);
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem1);
    containerItems.add(containerItem2);
    container.setContainerItems(containerItems);
    assertEquals(InstructionUtils.getMoveQuantity(container), 8);
  }

  @Test
  public void testCheckIfProblemTagPresent() {

    DeliveryDocument deliveryDocument = populateDeliveryDocument();
    ProblemData problemData = new ProblemData();
    problemData.setId("1234567890");
    problemData.setType("OVG");
    problemData.setStatus("ANSWERED_AND_READY_TO_RECEIVE");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .setProblems(Collections.singletonList(problemData));

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocument.getDeliveryDocumentLines().get(0));
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    List<String> problemTagsList = Arrays.asList("OVG");
    try {
      InstructionUtils.checkIfProblemTagPresent(
          "00032247267847", deliveryDocument, problemTagsList);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), PROBLEM_TAG_FOUND_FOR_SCANNED_UPC);
    }
  }

  @Test
  public void testCheckIfProblemTagPresent_WhenProblemTagNotPresentInReadyToReceiveState() {

    DeliveryDocument deliveryDocument = populateDeliveryDocument();
    ProblemData problemData = new ProblemData();
    problemData.setId("1234567890");
    problemData.setType("OVG");
    problemData.setStatus("CLOSED");
    deliveryDocument
        .getDeliveryDocumentLines()
        .get(0)
        .setProblems(Collections.singletonList(problemData));

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocument.getDeliveryDocumentLines().get(0));
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    List<String> problemTagsList = Arrays.asList("OVG");
    try {
      InstructionUtils.checkIfProblemTagPresent(
          "00032247267847", deliveryDocument, problemTagsList);
    } catch (ReceivingBadDataException e) {
      assert (false);
    }
  }

  private DeliveryDocument populateDeliveryDocument() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setSellerType("WFS");
    deliveryDocument.setSellerId("AE70EDC9EA4D455D908B70ACB7B43393");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(Long.parseLong("555429067"));
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setGtin("00032247267847");
    deliveryDocumentLine.setItemUpc("00032247267847");
    deliveryDocumentLine.setCaseUpc("00032247267847");
    deliveryDocumentLine.setPurchaseRefType(ReceivingConstants.WFS_CHANNEL_METHOD);
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setVendorPack(6);
    deliveryDocumentLine.setWarehousePack(6);
    deliveryDocumentLine.setTotalOrderQty(80);
    deliveryDocumentLine.setPalletHigh(10);
    deliveryDocumentLine.setPalletTie(8);
    deliveryDocumentLine.setOpenQty(80);
    deliveryDocumentLine.setWarehouseRotationTypeCode("3");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setProfiledWarehouseArea("OPM");
    deliveryDocumentLine.setImageUrl("https://walmart.com/test.jpg");
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    return deliveryDocument;
  }

  @Test
  public void testGetMoveQuantity_whenContainerHasNoContainerItems() {
    Container container = new Container();
    Container childContainer1 = new Container();
    Container childContainer2 = new Container();
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(childContainer1);
    childContainers.add(childContainer2);
    container.setChildContainers(childContainers);
    assertEquals(
        InstructionUtils.getMoveQuantity(container), container.getChildContainers().size());
  }

  /*
   * Ignore the validation if firstExpiryFirstOut is null
   */
  @Test
  public void test1_FEFO_null_NoRotateDate_noMinRcvDays() {

    try {
      documentLine.setRotateDate(null);
      documentLine.setWarehouseMinLifeRemainingToReceive(null);
      InstructionUtils.validateThresholdForSellByDate(false, null, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Ignore the validation if firstExpiryFirstOut is false
   */
  @Test
  public void test2_FEFO_false_hasRoateDate_hasMinRcvDays() {
    try {
      documentLine.setRotateDate(new Date());
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.FALSE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true and minReceivingDays=null
   */
  @Test
  public void test3_FEFO_true_hasRoateDate_nullMinRcvDays() {
    try {
      documentLine.setRotateDate(new Date());
      documentLine.setWarehouseMinLifeRemainingToReceive(null);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          String.format(ReceivingException.INVALID_ITEM_ERROR_MSG, documentLine.getItemNumber()));
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, sellByDate=null, minReceivingDays=5
   */
  @Test
  public void test3_FEFO_true_nullRoateDate_positiveMinRcvDays() {
    try {
      documentLine.setRotateDate(null);
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getMessage(), ReceivingException.INVALID_EXP_DATE);
    }
  }

  /*
   * Ignore the validation if firstExpiryFirstOut=true, sellByDate=today+9, minReceivingDays=5
   */
  @Test
  public void test4_sellByDateWithinLimit_PositiveMinReceivingDays_happypath() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(9, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Throw an error if  minReceivingDays=5 (positive)  | sellByDate=today (still less than minReceivingDays=5 days)
   */
  @Test
  public void
      test5_0_PositiveMinReceivingDays_today_OutOfThreashold_isMinLifeExpectancyV2validationDisabled() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage().contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL),
          INVALID_EXP_DATE_ERROR_MSG.contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL));
    }
  }
  /*
   * Throw an error if  minReceivingDays=5 (positive)  | sellByDate=today (still less than minReceivingDays=5 days)
   */
  @Test
  public void test5_PositiveMinReceivingDays_today_OutOfThreashold() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage().contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL),
          INVALID_EXP_DATE_ERROR_MSG.contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL));
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, minReceivingDays=5 (positive), sellByDate=today+3, (still less than minReceivingDays=5 days)
   */
  @Test
  public void test6_PositiveMinReceivingDays_sellByDate_OutOfThreashold() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(3, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), CLOSE_DATE_ERROR_HEADER);
      assertEquals(
          e.getMessage().contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL),
          INVALID_EXP_DATE_ERROR_MSG.contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL));
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, minReceivingDays=5 (positive), sellByDate=today-1=past date to current date
   */
  @Test
  public void test6_2_PositiveMinReceivingDays_sellByDate_PastDateToCurrent() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), UPDATE_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), EXPIRED_PRODUCT_HEADER);
      assertEquals(
          e.getMessage().contains(PAST_DATE_MSG_PARTIAL),
          PAST_DATE_MSG.contains(PAST_DATE_MSG_PARTIAL));
    }
  }
  /*
   * error if firstExpiryFirstOut=true, minReceivingDays=5 (positive), sellByDate=today (not a past date, no override)
   */
  @Test
  public void test6_3_PositiveMinReceivingDays_sellByDate_Today_NotPastDate() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(0, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), CLOSE_DATE_ERROR_HEADER);
      assertEquals(
          e.getMessage().contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL),
          INVALID_EXP_DATE_ERROR_MSG.contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL));
    }
  }
  /*
   * happy path, NO error if minReceivingDays=5 (positive), sellByDate=+5 Exactly Threshold Date
   */
  @Test
  public void test6_4_PositiveMinReceivingDays_sellByDate_ExactlyThresholdDate() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(5, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(5);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      fail();
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, sellByDate=null, minReceivingDays=-5
   */
  @Test
  public void test7_FEFO_true_nullRoateDate_negativeMinRcvDays() {
    try {
      documentLine.setRotateDate(null);
      documentLine.setWarehouseMinLifeRemainingToReceive(-5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getMessage(), ReceivingException.INVALID_EXP_DATE);
    }
  }

  /*
   * Ignore the validation packagedDate=today-2, minReceivingDays=-5 , no manager override
   * (last 2 days is within min life expectancy of last 5 days)
   */
  @Test
  public void test8_NegativeMinReceivingDays_past_sellByDate_withInRange_happy_path() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(2, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-5);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Throw error if packagedDate=today-8, minReceivingDays=-6 (2 days past than 6 days threshold days), no mgr override
   */
  @Test
  public void test9_NegativeMinReceivingDays_pastSellByDate_outOfRange_NoManagerOverride() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(8, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-6);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), UPDATE_DATE_ERROR_HEADER);
      final String matchString =
          "The manufacture date doesn't meet the date limit for this item. Acceptable dates must be between";
      assertEquals(
          e.getMessage().contains(matchString), UPDATE_DATE_ERROR_MGR_MSG.contains(matchString));
    }
  }
  /*
   * Throw error if packagedDate=today, minReceivingDays=-6 (0 days past than 6 days threshold days), no mgr override
   */
  @Test
  public void test9_1_NegativeMinReceivingDays_TodaySellByDate_NoManagerOverride() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-6);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      fail();
    }
  }
  /*
   * Throw error if packagedDate=-30, minReceivingDays=-30 (30 days past matching 30 days min remaining life), no mgr override
   */
  @Test
  public void test9_2_NegativeMinReceivingDays_EnterDateSameAsMnfgDate_NoManagerOverride() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(30, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-30);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      fail();
    }
  }

  /*
   * no error if packagedDate=today-8, minReceivingDays=-6 (2 days past than 6 days threshold days), with mgr override
   */
  @Test
  public void test10_NegativeMinReceivingDays_pastSellByDate_outOfRange_ManagerOverride() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(8, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-6);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, true, false);
    } catch (ReceivingException e) {
      fail();
    }
  }

  /*
   * no error if entered=today, minReceivingDays=0 (0 days past than 0 days threshold days), no mgr override
   */
  @Test
  public void test11_ZeroMinReceivingDays_enteredDateToday() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().truncatedTo(ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(0);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * no error if entered=today+1, minReceivingDays=0 (1 days past than 0 days threshold days), no mgr override
   */
  @Test
  public void test12_ZeroMinReceivingDays_enteredDateFuture() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(0);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, packagedDate=today+0(current date), minReceivingDays=1 positive number
   */
  @Test
  public void testIsKotlinFlagEnabledValidateThresholdForSellByDate_throwErrorOrLog() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(0, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(1);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, true);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), EXPIRED_PRODUCT_DATE_ERROR_CODE);
      final String matchString = "The expiration date entered,";
      assertEquals(
          e.getMessage().contains(matchString), INVALID_EXP_DATE_ERROR_MSG.contains(matchString));
    } catch (Exception e) {
      assert (false);
    }
  }

  /*
   * Throw an error if firstExpiryFirstOut=true, packagedDate=today+1(future date), minReceivingDays=-1 negative number
   */
  @Test
  public void testIsKotlinFlagEnabledValidateNegativeThresholdForSellByDate_throwErrorOrLog() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, true);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), INVALID_PRODUCT_DATE_ERROR_CODE);
      final String matchString =
          "The manufacture date cannot be in the future. Acceptable dates must be between";
      assertEquals(
          e.getMessage().contains(matchString), UPDATE_DATE_ERROR_MSG.contains(matchString));
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * no error if entered=today+1, minReceivingDays=0 (1 days past than 0 days threshold days), no mgr override
   */
  @Test
  public void test13_ZeroMinReceivingDays_enteredDatePast() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(0);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), EXPIRED_PRODUCT_HEADER);
      final String matchString = "can not be past date. Please enter a future or current date";
      assertEquals(
          e.getMessage().contains(matchString), UPDATE_DATE_ERROR_MSG.contains(matchString));
    }
  }

  /*
   * Ignore the validation if firstExpiryFirstOut=true, sellByDate=today+1, minReceivingDays=1
   */
  @Test
  public void test14ValidateThresholdForSellByDate() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(1);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assert (false);
    }
  }

  /*
   * Do NOT throw if packagedDate=today-1(past date), minReceivingDays=-1 negative number
   */
  @Test
  public void test15ValidateThresholdForSellByDate_noError() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      fail();
    }
  }
  /*
   * Throw an error if firstExpiryFirstOut=true, packagedDate=today+1(future date), minReceivingDays=-1 negative number
   */
  @Test
  public void test16ValidateThresholdForSellByDate_throwErrorOrLog() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorCode(), UPDATE_DATE_ERROR_CODE);
      assertEquals(e.getErrorResponse().getErrorHeader(), UPDATE_DATE_ERROR_HEADER);
      final String matchString =
          "The manufacture date cannot be in the future. Acceptable dates must be between";
      assertEquals(
          e.getMessage().contains(matchString), UPDATE_DATE_ERROR_MSG.contains(matchString));
    }
  }
  /*
   * error when sellByDate=past date (current-1), minReceivingDays is positive (1)
   */
  @Test
  public void test17ValidateThresholdForSellByDate() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(1);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, true, false);
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(e.getErrorResponse().getErrorHeader(), EXPIRED_PRODUCT_HEADER);
      assertTrue(e.getMessage().contains(", is earlier than today's date,"));
    }
  }

  /*
   * Throw an error if isManagerOverrideIgnoreExpiry=false
   */
  @Test
  public void testIsManagerOverrideIgnoreExpiryFalseExpectException() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(30, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, false);
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorHeader(), UPDATE_DATE_ERROR_HEADER);
      assertEquals(err.getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
    }
  }
  /*
   * Throw an error if isManagerOverrideIgnoreExpiry=true still if minReceivingDays -ve & is NOT past date error
   */
  @Test
  public void testIsManagerOverrideIgnoreExpiryTrueShouldSupressException() {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().minus(30, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          false, Boolean.TRUE, documentLine, true, false);
    } catch (ReceivingException e) {
      final ErrorResponse err = e.getErrorResponse();
      assertEquals(err.getErrorHeader(), EXPIRED_PRODUCT_HEADER);
      assertEquals(err.getErrorCode(), INVALID_EXP_DATE_ERROR_CODE);
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
    }
  }

  @Test
  public void testPopulateCreateContainerRequest() {
    // Prepare input request
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("msgx1");
    instructionRequest.setDeliveryNumber("121212121");
    instructionRequest.setDoorNumber("101");
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(Long.parseLong("555429067"));
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setGtin("00032247267847");
    deliveryDocumentLine.setItemUpc("00032247267847");
    deliveryDocumentLine.setCaseUpc("00032247267847");
    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("ZA");
    deliveryDocumentLine.setVendorPack(6);
    deliveryDocumentLine.setWarehousePack(6);
    deliveryDocumentLine.setTotalOrderQty(80);
    deliveryDocumentLine.setPalletHigh(10);
    deliveryDocumentLine.setPalletTie(8);
    deliveryDocumentLine.setOpenQty(80);
    deliveryDocumentLine.setWarehouseRotationTypeCode("3");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setProfiledWarehouseArea("OPM");
    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    // Prepare expected response
    Content content = new Content();
    Facility facility = new Facility();
    ContainerModel cm = new ContainerModel();
    List<Content> contents = new ArrayList<>();
    FdeCreateContainerRequest expectedResponse = new FdeCreateContainerRequest();
    expectedResponse.setMessageId("msgx1");
    expectedResponse.setCorrelationId("3a2b6c1d2e");
    expectedResponse.setDeliveryNumber("121212121");
    expectedResponse.setDoorNumber("101");
    expectedResponse.setUserId("witronTest");
    facility.setCountryCode("US");
    facility.setBuNumber("32612");
    expectedResponse.setFacility(facility);
    content.setGtin("00032247267847");
    content.setItemNbr(Long.parseLong("555429067"));
    content.setBaseDivisionCode("WM");
    content.setFinancialReportingGroup("US");
    content.setPurchaseReferenceNumber("4763030227");
    content.setPurchaseReferenceLineNumber(1);
    content.setPurchaseRefType("SSTKU");
    content.setQtyUom(ReceivingConstants.Uom.VNPK);
    content.setVendorPack(6);
    content.setWarehousePack(6);
    content.setOpenQty(80);
    content.setTotalOrderQty(80);
    content.setPalletTie(8);
    content.setPalletHigh(10);
    content.setMaxReceiveQty(80);
    content.setIsConveyable(Boolean.FALSE);
    content.setVendorNumber("482497180");
    content.setProfiledWarehouseArea("OPM");
    content.setWarehouseAreaCode("1");
    content.setWarehouseAreaCodeValue("M");
    content.setCaseUPC("00032247267847");
    contents.add(content);
    cm.setContents(contents);
    expectedResponse.setContainer(cm);

    FdeCreateContainerRequest actualResponse =
        InstructionUtils.mapHttpHeadersToFdeCreateContainerRequest(
            httpHeaders, InstructionUtils.createFdeCreateContainerRequest(instructionRequest));
    actualResponse.setContainer(InstructionUtils.createContainerModel(deliveryDocument));

    assertEquals(gson.toJson(actualResponse), gson.toJson(expectedResponse));
  }

  @Test
  public void testPopulateCreateContainerRequestForWFS() {
    // Preparing headers
    httpHeaders.set(ReceivingConstants.USER_ID_HEADER_KEY, "userId");

    // Prepare input request
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("msgx1");
    instructionRequest.setDeliveryNumber("121212121");
    instructionRequest.setDoorNumber("101");
    instructionRequest.setEnteredQty(10);
    instructionRequest.setEnteredQtyUOM("EA");
    instructionRequest.setMultiSKUItem(true);
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setSellerType("WFS");
    deliveryDocument.setSellerId("AE70EDC9EA4D455D908B70ACB7B43393");
    PoAdditionalInfo poAdditionalInfo = new PoAdditionalInfo();
    poAdditionalInfo.setReReceivingShipmentNumber("ASN_0654216GDM_m040930000200000004897595");
    deliveryDocument.setAdditionalInfo(poAdditionalInfo);
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setItemNbr(Long.parseLong("555429067"));
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setGtin("00032247267847");
    deliveryDocumentLine.setItemUpc("00032247267847");
    deliveryDocumentLine.setCaseUpc("00032247267847");
    deliveryDocumentLine.setPurchaseRefType(ReceivingConstants.WFS_CHANNEL_METHOD);
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setVendorPack(6);
    deliveryDocumentLine.setWarehousePack(6);
    deliveryDocumentLine.setTotalOrderQty(80);
    deliveryDocumentLine.setPalletHigh(10);
    deliveryDocumentLine.setPalletTie(8);
    deliveryDocumentLine.setOpenQty(80);
    deliveryDocumentLine.setWarehouseRotationTypeCode("3");
    deliveryDocumentLine.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setProfiledWarehouseArea("OPM");
    deliveryDocumentLine.setImageUrl("https://walmart.com/test.jpg");
    ItemData itemData = new ItemData();
    List<Map<String, String>> pairs = new ArrayList<>();
    Map<String, String> pair1 = new LinkedTreeMap<>();
    pair1.put("serviceType", "LABEL");
    Map<String, String> pair2 = new LinkedTreeMap<>();
    pair2.put("serviceType", "TAPE");
    Map<String, String> pair3 = new LinkedTreeMap<>();
    pair3.put("serviceType", "BAG");
    pairs.add(pair1);
    pairs.add(pair2);
    pairs.add(pair3);
    itemData.setAddonServices(pairs);
    itemData.setWarehouseAreaCode("1");
    itemData.setWeightQty("2.0");
    itemData.setWeightQtyUom(ReceivingConstants.Uom.LB);
    itemData.setCubeQty("1.0");
    itemData.setCubeUomCode("CF");
    deliveryDocumentLine.setAdditionalInfo(itemData);
    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    // Prepare expected response
    Content content = new Content();
    Facility facility = new Facility();
    ContainerModel cm = new ContainerModel();
    List<Content> contents = new ArrayList<>();
    FdeCreateContainerRequest expectedResponse = new FdeCreateContainerRequest();
    expectedResponse.setMessageId("msgx1");
    expectedResponse.setCorrelationId("3a2b6c1d2e");
    expectedResponse.setDeliveryNumber("121212121");
    expectedResponse.setDoorNumber("101");
    expectedResponse.setUserId("userId");
    facility.setCountryCode("US");
    facility.setBuNumber("32612");
    expectedResponse.setFacility(facility);
    content.setGtin("00032247267847");
    content.setItemNbr(Long.parseLong("555429067"));
    content.setBaseDivisionCode("WM");
    content.setFinancialReportingGroup("US");
    content.setPurchaseReferenceNumber("4763030227");
    content.setPurchaseReferenceLineNumber(1);
    content.setPurchaseRefType("WFS");
    content.setQtyUom(ReceivingConstants.Uom.EACHES);
    content.setVendorPack(6);
    content.setWarehousePack(6);
    content.setOpenQty(80);
    content.setTotalOrderQty(80);
    content.setPalletTie(8);
    content.setPalletHigh(10);
    content.setMaxReceiveQty(80);
    content.setIsConveyable(Boolean.FALSE);
    content.setVendorNumber("482497180");
    content.setProfiledWarehouseArea("OPM");
    content.setWarehouseAreaCode("1");
    content.setWarehouseAreaCodeValue("M");
    content.setCaseUPC("00032247267847");
    content.setRecommendedFulfillmentType(ReceivingConstants.PUT_FULFILLMENT_TYPE);
    content.setSellerType(ReceivingConstants.WFS_CHANNEL_METHOD);
    content.setSellerId("AE70EDC9EA4D455D908B70ACB7B43393");
    content.setReceiveQty(instructionRequest.getEnteredQty());
    content.setReceivingUnit(ReceivingConstants.EACH);
    content.setReReceivingShipmentNumber("ASN_0654216GDM_m040930000200000004897595");
    content.setAdditionalInfo(itemData);
    contents.add(content);
    cm.setContents(contents);
    cm.setWeight(2.0f);
    cm.setWeightUom(ReceivingConstants.Uom.LB);
    cm.setCube(1.0f);
    cm.setCubeUom("CF");
    expectedResponse.setContainer(cm);

    FdeCreateContainerRequest actualResponse =
        InstructionUtils.createFdeCreateContainerRequestForWFS(
            instructionRequest, deliveryDocument, httpHeaders);
    assertEquals(gson.toJson(actualResponse), gson.toJson(expectedResponse));
  }

  @Test
  public void testProcessInstructionResponseForWFS() throws IOException {
    // Preparing FdeCreateContainerResponse
    String dataPathOPResponseWFS =
        new File(
                "../receiving-test/src/main/resources/json/wfs/wfsOPResponseForCasePackBuildInstruction.json")
            .getCanonicalPath();
    FdeCreateContainerResponse fdeCreateContainerResponse =
        new Gson()
            .fromJson(
                new String(Files.readAllBytes(Paths.get(dataPathOPResponseWFS))),
                FdeCreateContainerResponse.class);
    // Preparing Container
    String dataPathOPResponseContainerWFS =
        new File("../receiving-test/src/main/resources/json/wfs/wfsOPResponseContainer.json")
            .getCanonicalPath();
    ContainerDetails container =
        new Gson()
            .fromJson(
                new String(Files.readAllBytes(Paths.get(dataPathOPResponseContainerWFS))),
                ContainerDetails.class);
    Instruction expectedInstruction = new Instruction();
    expectedInstruction.setProjectedReceiveQty(6);
    expectedInstruction.setManualInstruction(false);
    expectedInstruction.setPrintChildContainerLabels(false);
    expectedInstruction.setInstructionMsg("No action required");
    expectedInstruction.setInstructionCode("Pass");
    expectedInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
    expectedInstruction.setContainer(container);
    expectedInstruction.setProviderId("AllocPlanner");
    expectedInstruction.setActivityName("CASEPACK");

    Instruction actualInstruction = new Instruction();
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setManualReceivingEnabled(false);
    actualInstruction =
        InstructionUtils.processInstructionResponseForWFS(
            actualInstruction, instructionRequest, fdeCreateContainerResponse);

    assertEquals(gson.toJson(actualInstruction), gson.toJson(expectedInstruction));
  }

  /** This method is used to check isNational PO successfull case */
  @Test
  public void testIsNationalPOSuccessfullCase() {
    documentLine.setPoDCNumber("32612");
    documentLine.setPurchaseRefType("SSTKU");
    Boolean actualResult = InstructionUtils.isNationalPO(documentLine.getPurchaseRefType());
    assertEquals(actualResult, Boolean.TRUE);
  }

  /** This method is used to check isNational PO failure case */
  @Test
  public void testIsNationalPOFailureCase() {
    documentLine.setPurchaseRefType("POCON");
    Boolean actualResult = InstructionUtils.isNationalPO(documentLine.getPurchaseRefType());
    assertEquals(actualResult, Boolean.FALSE);
  }

  @Test
  public void testIsCancelledPO() {
    assertEquals(InstructionUtils.isCancelledPOOrPOLine(getCancelledPO()), true);
  }

  @Test
  public void testIsCancelledPOPOL() {
    assertEquals(InstructionUtils.isCancelledPOOrPOLine(getCancelledPOLine()), true);
  }

  @Test
  public void testCancelPOPOLCheckNotRequired_POCONPOL_cancelledPO_notAllowed() {
    assertEquals(InstructionUtils.cancelPOPOLCheckNotRequired(getCancelledPOCONPO(), false), false);
  }

  @Test
  public void testCancelPOPOLCheckNotRequired_POCONPOL_cancelledPO_allowed() {
    assertEquals(InstructionUtils.cancelPOPOLCheckNotRequired(getCancelledPOCONPO(), true), true);
  }

  @Test
  public void testCancelPOPOLCheckNotRequired_POCONPOL_cancelledPOL_notAllowed() {
    assertEquals(
        InstructionUtils.cancelPOPOLCheckNotRequired(getCancelledPOCONPOLine(), false), false);
  }

  @Test
  public void testCancelPOPOLCheckNotRequired_POCONPOL_cancelledPOL_allowed() {
    assertEquals(
        InstructionUtils.cancelPOPOLCheckNotRequired(getCancelledPOCONPOLine(), true), true);
  }

  @Test
  public void testgetPrintJobWithWitronAttributes() {
    String printerName = "Room123Printer";
    String rotateDate = "12/31/2020";
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setContainer(MockInstruction.getWitronContainerData());
    String dcTimeZone = "US/Central";

    Map<String, Object> printjob =
        InstructionUtils.getPrintJobWithWitronAttributes(
            instruction, rotateDate, userId, printerName, dcTimeZone);

    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printjob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> data = (List<Map<String, Object>>) printRequest.get("data");

    assertEquals(data.size(), 5);

    assertEquals(data.get(0).get("key"), "LPN");
    assertEquals(data.get(0).get("value"), "A47341986287612711");
    assertEquals(data.get(4).get("key"), "printerId");
    assertEquals(data.get(4).get("value"), printerName);

    // printDate should have given input time zone
    assertEquals(data.get(2).get("key"), "printDate");
    final String printDateHavingDC_TimeZone = (data.get(2).get("value")).toString();
    assertTrue(
        printDateHavingDC_TimeZone.contains("CDT") || printDateHavingDC_TimeZone.contains("CST"));
  }

  @Test
  public void testgetPrintJobWithWitronAttributes_NullOrEmptyDcZoneThenReturnUTC() {
    String printerName = "Room123Printer";
    String rotateDate = "12/31/2020";
    String userId = httpHeaders.getFirst(ReceivingConstants.USER_ID_HEADER_KEY);
    instruction.setContainer(MockInstruction.getWitronContainerData());
    String dcTimeZone = "";

    Map<String, Object> printjob =
        InstructionUtils.getPrintJobWithWitronAttributes(
            instruction, rotateDate, userId, printerName, dcTimeZone);

    List<Map<String, Object>> printRequests =
        (List<Map<String, Object>>) printjob.get("printRequests");
    Map<String, Object> printRequest = printRequests.get(0);
    List<Map<String, Object>> data = (List<Map<String, Object>>) printRequest.get("data");

    assertEquals(data.size(), 5);

    assertEquals(data.get(0).get("key"), "LPN");
    assertEquals(data.get(0).get("value"), "A47341986287612711");
    assertEquals(data.get(4).get("key"), "printerId");
    assertEquals(data.get(4).get("value"), printerName);

    // printDate should have given input time zone
    assertEquals(data.get(2).get("key"), "printDate");
    final String printDateHavingDC_TimeZone = (data.get(2).get("value")).toString();
    assertTrue(printDateHavingDC_TimeZone.contains("UTC"));
  }

  private static DeliveryDocument getCancelledPO() {
    DeliveryDocument cancelledDeliveryDoc = new DeliveryDocument();
    cancelledDeliveryDoc.setPurchaseReferenceNumber(POStatus.CNCL.toString());
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    cancelledDeliveryDoc.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    return cancelledDeliveryDoc;
  }

  private static DeliveryDocument getCancelledPOLine() {
    DeliveryDocument cancelledDeliveryDoc = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    cancelledDeliveryDoc.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    return cancelledDeliveryDoc;
  }

  private static DeliveryDocument getCancelledPOCONPO() {
    DeliveryDocument cancelledDeliveryDoc = new DeliveryDocument();
    cancelledDeliveryDoc.setPurchaseReferenceNumber(POStatus.CNCL.toString());
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseRefType(ReceivingConstants.POCON_ACTIVITY_NAME);
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    cancelledDeliveryDoc.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    return cancelledDeliveryDoc;
  }

  private static DeliveryDocument getCancelledPOCONPOLine() {
    DeliveryDocument activeDeliveryDoc = new DeliveryDocument();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseRefType(ReceivingConstants.POCON_ACTIVITY_NAME);
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.toString());
    activeDeliveryDoc.setDeliveryDocumentLines(Arrays.asList(deliveryDocumentLine));
    return activeDeliveryDoc;
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForCROSSMU() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSMU.toString(), null),
        PurchaseReferenceType.CROSSMU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSMU.toString(),
            Arrays.asList(PurchaseReferenceType.CROSSU.toString())),
        PurchaseReferenceType.CROSSMU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSMU.toString(),
            Arrays.asList(
                PurchaseReferenceType.CROSSU.toString(), PurchaseReferenceType.SSTKU.toString())),
        PurchaseReferenceType.CROSSMU.toString());
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForPOCON_WithMultipleChannelMethods() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(), null),
        PurchaseReferenceType.POCON.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(),
            Collections.singletonList(PurchaseReferenceType.POCON.name())),
        PurchaseReferenceType.POCON.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(),
            Arrays.asList(PurchaseReferenceType.CROSSU.name(), PurchaseReferenceType.SSTKU.name())),
        PurchaseReferenceType.POCON.name());
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForPOCON_WithCROSSUChannelMethod() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(), null),
        PurchaseReferenceType.POCON.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(),
            Collections.singletonList(PurchaseReferenceType.POCON.name())),
        PurchaseReferenceType.POCON.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.POCON.name(), Collections.singletonList("CROSSU")),
        PurchaseReferenceType.POCON.name());
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForDSDC() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.DSDC.name(), null),
        PurchaseReferenceType.DSDC.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.DSDC.name(),
            Collections.singletonList(PurchaseReferenceType.CROSSU.name())),
        PurchaseReferenceType.DSDC.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.DSDC.name(),
            Collections.singletonList(PurchaseReferenceType.SSTKU.name())),
        PurchaseReferenceType.DSDC.name());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.DSDC.name(),
            Arrays.asList(PurchaseReferenceType.SSTKU.name(), PurchaseReferenceType.CROSSU.name())),
        PurchaseReferenceType.DSDC.name());
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForSSTKU() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.SSTKU.toString(), null),
        PurchaseReferenceType.SSTKU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSU.toString(),
            Arrays.asList(PurchaseReferenceType.SSTKU.toString())),
        PurchaseReferenceType.SSTKU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSU.toString(),
            Arrays.asList(
                PurchaseReferenceType.CROSSU.toString(), PurchaseReferenceType.SSTKU.toString())),
        PurchaseReferenceType.SSTKU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.SSTKU.toString(),
            Arrays.asList(
                PurchaseReferenceType.CROSSU.toString(), PurchaseReferenceType.SSTKU.toString())),
        PurchaseReferenceType.SSTKU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.SSTKU.toString(),
            Arrays.asList(PurchaseReferenceType.SSTKU.toString())),
        PurchaseReferenceType.SSTKU.toString());
  }

  @Test
  public void testGetPurchaseRefTypeIncludingChannelFlipForCROSSU() {
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSU.toString(), null),
        PurchaseReferenceType.CROSSU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.CROSSU.toString(),
            Arrays.asList(PurchaseReferenceType.CROSSU.toString())),
        PurchaseReferenceType.CROSSU.toString());
    assertEquals(
        InstructionUtils.getPurchaseRefTypeIncludingChannelFlip(
            PurchaseReferenceType.SSTKU.toString(),
            Arrays.asList(PurchaseReferenceType.CROSSU.toString())),
        PurchaseReferenceType.CROSSU.toString());
  }

  @Test
  public void testIsDAConFreight_POCON() {
    assertFalse(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE, "POCON", Collections.singletonList("CROSSU")));
  }

  @Test
  public void testIsDAConFreight_DSDC() {
    assertFalse(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE,
            PurchaseReferenceType.DSDC.name(),
            Collections.singletonList(PurchaseReferenceType.CROSSU.name())));
  }

  @Test
  public void testIsDAConFreight_DSDC_NoChannelMethod() {
    assertFalse(
        InstructionUtils.isDAConFreight(Boolean.TRUE, PurchaseReferenceType.DSDC.name(), null));
  }

  @Test
  public void testIsDAConFreight_CROSSU() {
    assertTrue(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE, "CROSSU", Collections.singletonList("CROSSU")));
  }

  @Test
  public void testIsDAConFreight_CROSSU_NONCON() {
    assertFalse(
        InstructionUtils.isDAConFreight(
            Boolean.FALSE, "CROSSU", Collections.singletonList("CROSSU")));
  }

  @Test
  public void testIsDAConFreight_CROSSU_NONCON_Flipped() {
    assertFalse(
        InstructionUtils.isDAConFreight(
            Boolean.FALSE, "CROSSU", Collections.singletonList("SSTKU")));
  }

  @Test
  public void testIsDAConFreight_CROSSU_Flipped() {
    assertFalse(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE, "CROSSU", Collections.singletonList("SSTKU")));
  }

  @Test
  public void testIsDAConFreight_CROSSU_MultipleChannelMethods() {
    assertFalse(
        InstructionUtils.isDAConFreight(Boolean.TRUE, "CROSSU", Arrays.asList("SSTKU", "CROSSU")));
  }

  @Test
  public void testIsDAConFreight_SSTKU() {
    assertFalse(
        InstructionUtils.isDAConFreight(Boolean.TRUE, "SSTKU", Collections.singletonList("SSTKU")));
  }

  @Test
  public void testIsDAConFreight_SSTKU_MultipleChannelMethods() {
    assertFalse(
        InstructionUtils.isDAConFreight(Boolean.TRUE, "SSTKU", Arrays.asList("SSTKU", "CROSSU")));
  }

  @Test
  public void testIsDAConFreight_SSTKU_Flipped() {
    assertTrue(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE, "SSTKU", Collections.singletonList("CROSSU")));
  }

  @Test
  public void testIsDAConFreight_CROSSA() {
    assertTrue(
        InstructionUtils.isDAConFreight(
            Boolean.TRUE, "CROSSA", Collections.singletonList("SSTKU")));
  }

  @Test
  public void testfilteroutDSDCPos() throws ReceivingException {

    List<DeliveryDocument> deliveryDocumentList =
        InstructionUtils.filteroutDSDCPos(deliveryDocuments);
    assertEquals(deliveryDocumentList.size(), 1);
  }

  @Test
  public void testFilterDAConDeliveryDocumentsForManualReceiving() throws ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        InstructionUtils.filterDAConDeliveryDocumentsForManualReceiving(
            MockInstruction.getDeliveryDocumentsForPOCONisDAFreight());
    assertEquals(deliveryDocumentList.size(), 1);
  }

  @Test
  public void testIsRegulatedItemType() {
    assertTrue(InstructionUtils.isRegulatedItemType(VendorCompliance.LIMITED_QTY));
    assertTrue(InstructionUtils.isRegulatedItemType(VendorCompliance.LITHIUM_ION));
    assertTrue(
        InstructionUtils.isRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY));
  }

  @Test
  public void testIsRegulatedItemTypeNull() {
    assertFalse(InstructionUtils.isRegulatedItemType(null));
  }

  @Test
  public void testfilteroutDSDCPosIfAllDeliveryDocumentsAreDSDCPo() throws ReceivingException {
    try {
      deliveryDocuments.get(0).setPurchaseReferenceLegacyType("73");
      deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).setPurchaseRefType("DSDC");
      InstructionUtils.filteroutDSDCPos(deliveryDocuments);
    } catch (ReceivingException ex) {
      assertEquals(ex.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          ex.getMessage(),
          "This freight belongs to a DSDC PO. Please receive using Print DSDC Label option");
    }
  }

  @Test
  public void test_getDeliveryDocumentLine() {
    final DeliveryDocumentLine deliveryDocumentLine =
        InstructionUtils.getDeliveryDocumentLine(instructionWithDocument);
    assertNotNull(deliveryDocumentLine);
  }

  @Test
  public void test_getDeliveryDocumentAndItsAttributes() {
    final DeliveryDocument deliveryDocument_Obj =
        InstructionUtils.getDeliveryDocument(instructionWithDocument);
    assertNotNull(deliveryDocument_Obj);
    final Integer vendorNbrDeptSeq = deliveryDocument_Obj.getVendorNbrDeptSeq();
    assertNotNull(vendorNbrDeptSeq);
  }

  @Test
  public void test_getDeliveryDocument_Null() {
    Instruction instructionNullDocument = new Instruction();
    instructionNullDocument.setDeliveryDocument(null);
    final DeliveryDocument deliveryDocument =
        InstructionUtils.getDeliveryDocument(instructionNullDocument);
    assertNull(deliveryDocument);
  }

  @Test
  public void getCloseDateAlertMessage() {
    Date sellBy = new Date("10/23/2020");
    Date threshold = new Date("10/28/2020");
    final String expiryThresholdDateAsString = InstructionUtils.getLocalDateTimeAsString(threshold);

    final String closeDateAlertMessage =
        InstructionUtils.getUpdateDateErrorMessage(
            INVALID_EXP_DATE_ERROR_MSG, sellBy, expiryThresholdDateAsString);
    assertNotNull(closeDateAlertMessage);
    assertTrue(closeDateAlertMessage.contains(INVALID_EXP_DATE_ERROR_MSG_PARTIAL));
  }

  @Test
  public void testCheckIfDeliveryStatusReceivable() {
    try {
      deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.WRK);
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));

    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testCheckIfDeliveryStatusNotReceivable() throws ReceivingException {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.FNL);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(DeliveryStatus.FNL.toString());
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));

    } catch (ReceivingException e) {

      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          "This delivery can not be received as the status is in FNL in GDM .Please contact your supervisor");
    }
  }

  @Test
  public void testCheckIfDeliveryStatusPendingProblem() {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(DeliveryStatus.PNDPT.toString());
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    } catch (ReceivingException e) {
      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          "This delivery is in PNDPT status in GDM. Please reopen this delivery by entering the delivery number");
    }
  }

  @Test
  public void testCheckIfDeliveryStatusPNDFNL() {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.PNDFNL);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(DeliveryStatus.PNDFNL.toString());
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    } catch (ReceivingException e) {

      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          "This delivery is in PNDFNL status in GDM. Please reopen this delivery by entering the delivery number");
    }
  }

  @Test
  public void testCheckIfDeliveryStatusARV() {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.ARV);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(DeliveryStatus.ARV.toString());
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    } catch (ReceivingException e) {

      assertEquals(e.getHttpStatus(), BAD_REQUEST);
      assertEquals(
          e.getMessage(),
          "This delivery can not be received as the status is in ARV in GDM .Please contact your supervisor");
    }
  }

  @Test
  public void testCheckIfDeliveryStatusOpenDockTagReceivable() {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocuments.get(0).setDeliveryLegacyStatus("PNDDT");
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testCheckIfDeliveryStatusOpenReceivable() {
    deliveryDocuments.get(0).setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocuments.get(0).setDeliveryLegacyStatus(DeliveryStatus.OPN.toString());
    try {
      InstructionUtils.checkIfDeliveryStatusReceivable(deliveryDocuments.get(0));
    } catch (ReceivingException e) {
      assertTrue(false);
    }
  }

  @Test
  public void testIsReceiptPostingRequired_DelieryWRK() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            DeliveryStatus.WRK.name(), Arrays.asList("PENDING_DOCK_TAG", "DELIVERY_REOPENED")));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryPNDFNL() {
    assertEquals(
        true,
        InstructionUtils.isReceiptPostingRequired(DeliveryStatus.PNDFNL.name(), new ArrayList<>()));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryOPNEmptyStatusReasonCode() {
    assertEquals(
        true,
        InstructionUtils.isReceiptPostingRequired(DeliveryStatus.OPN.name(), new ArrayList<>()));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryOPN_REO() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            DeliveryStatus.OPN.name(), Arrays.asList("DELIVERY_REOPENED")));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryOPN_PNDDT() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            DeliveryStatus.OPN.name(), Arrays.asList("PENDING_DOCK_TAG")));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryOPN_REO_PNDDT() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            DeliveryStatus.OPN.name(), Arrays.asList("PENDING_DOCK_TAG", "DELIVERY_REOPENED")));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryStatusNull() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            null, Arrays.asList("PENDING_DOCK_TAG", "DELIVERY_REOPENED")));
  }

  @Test
  public void testIsReceiptPostingRequired_DeliveryStatusEmpty() {
    assertEquals(
        false,
        InstructionUtils.isReceiptPostingRequired(
            "", Arrays.asList("PENDING_DOCK_TAG", "DELIVERY_REOPENED")));
  }

  @Test
  public void test_IgnoreCompletedNonNationalInstructionsOnSummary() {
    Instruction instruction1 = MockInstruction.getInstruction();
    Instruction instruction2 = MockInstruction.getCompleteInstruction();
    instruction2.setPurchaseReferenceNumber(ReceivingConstants.DUMMY_PURCHASE_REF_NUMBER);
    instruction2.setActivityName("POCON");
    assertNotNull(InstructionUtils.getDeliveryDocument(instruction1));
    assertNull(InstructionUtils.getDeliveryDocument(instruction2));
  }

  @Test
  public void testConstructUpdateInstructionRequest() {
    ReceiveInstructionRequest receiveInstructionRequest = new ReceiveInstructionRequest();
    receiveInstructionRequest.setDoorNumber("105");
    receiveInstructionRequest.setContainerType("Chep Pallet");
    receiveInstructionRequest.setRotateDate(new Date());
    receiveInstructionRequest.setPrinterName("TestPrinter");
    receiveInstructionRequest.setQuantity(1);
    receiveInstructionRequest.setQuantityUOM("ZA");

    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("297103"));
    instruction.setDeliveryNumber(Long.valueOf("12345678"));
    instruction.setDeliveryDocument(MockInstruction.getMockDeliveryDocument());

    UpdateInstructionRequest updateInstructionRequest =
        InstructionUtils.constructUpdateInstructionRequest(instruction, receiveInstructionRequest);

    assertNotNull(updateInstructionRequest);
    assertEquals(updateInstructionRequest.getDeliveryNumber(), instruction.getDeliveryNumber());
    assertEquals(updateInstructionRequest.getContainerType(), "Chep Pallet");
    assertEquals(
        updateInstructionRequest.getDeliveryDocumentLines().get(0).getItemNumber(),
        Long.valueOf("9745387"));
    assertEquals(
        updateInstructionRequest
            .getDeliveryDocumentLines()
            .get(0)
            .getWarehouseMinLifeRemainingToReceive(),
        Integer.valueOf("30"));
  }

  @Test
  public void testConstructUpdateInstructionRequestForCancelContainer() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("297103"));
    instruction.setCreateUserId("crperry");
    instruction.setDeliveryNumber(Long.valueOf("12345678"));
    instruction.setReceivedQuantity(34);
    instruction.setDeliveryDocument(MockInstruction.getMockDeliveryDocument());

    Container container = getContainer();
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(ReceivingConstants.USER_ROLE, UNLOADER);
    container.getContainerItems().get(0).setWarehouseAreaCode("2");
    container.getContainerItems().get(0).setContainerItemMiscInfo(containerItemMiscInfo);
    container.setInstructionId(Long.valueOf("297103"));

    UpdateInstructionRequest updateInstructionRequest =
        InstructionUtils.constructUpdateInstructionRequestForCancelContainer(
            instruction, container);

    assertNotNull(updateInstructionRequest);
    assertEquals(
        updateInstructionRequest.getDeliveryDocumentLines().get(0).getItemNumber(),
        Long.valueOf("9745387"));
    assertEquals(
        updateInstructionRequest
            .getDeliveryDocumentLines()
            .get(0)
            .getWarehouseMinLifeRemainingToReceive(),
        Integer.valueOf("30"));

    assertEquals(
        updateInstructionRequest.getDeliveryDocumentLines().get(0).getQuantity(),
        Integer.valueOf("34"));

    assertEquals(updateInstructionRequest.getUserRole(), "UNLR");
    assertEquals(updateInstructionRequest.getCreateUser(), "crperry");
    assertEquals(updateInstructionRequest.getEventType(), "VTR");
  }

  public static Container getContainer() {
    Container container = new Container();
    container.setDeliveryNumber(Long.valueOf("12345678"));
    container.setTrackingId("027734368100444931");
    container.setContainerType("Chep Pallet");
    container.setLocation("101");
    container.setWeight((float) 160.0);
    container.setWeightUOM("LB");
    container.setContainerStatus(null);
    container.setCompleteTs(new Date());
    container.setPublishTs(new Date());

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("027734368100444931");
    containerItem.setPurchaseReferenceNumber("6712345678");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setOutboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(80);
    containerItem.setGtin("7874213228");
    containerItem.setItemNumber(Long.valueOf("554930276"));
    containerItem.setQuantity(480);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setActualTi(8);
    containerItem.setActualHi(10);
    containerItem.setLotNumber("555");
    containerItem.setVendorNumber(579284);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setVnpkWgtQty((float) 2.0);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    return container;
  }

  @Test
  public void isDAConReturnsFalseForNonConFreights() {
    // MockInstruction.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("CROSSMU");
    deliveryDocumentLine.setIsConveyable(false);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertFalse(InstructionUtils.isDAConRequest(instructionRequest));
  }

  @Test
  public void isDAConReturnsTrueForManualPOWithConveyableFreights() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("CROSSMU");
    deliveryDocumentLine.setIsConveyable(true);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertTrue(InstructionUtils.isDAConRequest(instructionRequest));
  }

  @Test
  public void isDAConReturnsTrueForDAConveyableFreightWithNoActiveChannelMethods() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setIsConveyable(true);
    List<String> activeChannelMethods = new ArrayList<>();
    deliveryDocumentLine.setActiveChannelMethods(activeChannelMethods);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertTrue(InstructionUtils.isDAConRequest(instructionRequest));
  }

  @Test
  public void isDAConReturnsFalseWhenDAItemFlippedToSSTK() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setIsConveyable(true);
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add("SSTKU");
    deliveryDocumentLine.setActiveChannelMethods(activeChannelMethods);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertFalse(InstructionUtils.isDAConRequest(instructionRequest));
  }

  @Test
  public void isDAConReturnsTrueWhenItemFlippedFromSSTKToDA() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setIsConveyable(true);
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add("CROSSU");
    deliveryDocumentLine.setActiveChannelMethods(activeChannelMethods);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertTrue(InstructionUtils.isDAConRequest(instructionRequest));
  }

  @Test
  public void isDAConReturnsFalseWhenActiveChannelMethodHasBothDAAndSSTK() {
    List<DeliveryDocument> deliveryDocumentList = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    List<DeliveryDocumentLine> deliveryDocumentLineList = new ArrayList<>();
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    InstructionRequest instructionRequest = new InstructionRequest();

    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setIsConveyable(true);
    List<String> activeChannelMethods = new ArrayList<>();
    activeChannelMethods.add("SSTKU");
    activeChannelMethods.add("CROSSU");
    deliveryDocumentLine.setActiveChannelMethods(activeChannelMethods);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocumentLineList.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLineList);
    deliveryDocumentList.add(deliveryDocument);
    instructionRequest.setOnline(Boolean.TRUE);
    instructionRequest.setDeliveryDocuments(deliveryDocumentList);

    AssertJUnit.assertFalse(InstructionUtils.isDAConRequest(instructionRequest));
  }

  /*@Test
  public void testExpiryDateAsString() {
    Date testDate = new Date("06/15/2020");
    final String expiryDateAsString = InstructionUtils.getExpiryDateAsString(testDate);
    assertEquals(
        "The expiration date entered, 06/15/2020, doesn't meet this item's minimum life expectancy threshold. How would you like to proceed?",
        expiryDateAsString);
  }*/

  @Test
  public void testValidateCancelledPoPoLineStatus_NoCancelledPoPolines() {
    List<DeliveryDocument> deliveryDocuments = MockInstruction.getDeliveryDocuments();
    try {
      List<DeliveryDocument> activeDeliveryDocuments =
          InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.assertEquals(deliveryDocuments.size(), activeDeliveryDocuments.size());
    } catch (ReceivingException e) {
      AssertJUnit.fail();
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_PartialCancelledMultiPo() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getPartialCancelledMultiPoDeliveryDocuments();
    try {
      List<DeliveryDocument> activeDeliveryDocuments =
          InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.assertEquals(deliveryDocuments.size() - 1, activeDeliveryDocuments.size());
    } catch (ReceivingException e) {
      AssertJUnit.fail();
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_PartialCancelledMultiPoLine() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getPartialCancelledMultiPoLineDeliveryDocuments();
    int numberOfOriginalLines = deliveryDocuments.get(0).getDeliveryDocumentLines().size();
    try {
      List<DeliveryDocument> activeDeliveryDocuments =
          InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.assertEquals(deliveryDocuments.size(), activeDeliveryDocuments.size());
      AssertJUnit.assertEquals(
          numberOfOriginalLines - 1,
          activeDeliveryDocuments.get(0).getDeliveryDocumentLines().size());
    } catch (ReceivingException e) {
      AssertJUnit.fail();
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_AllCancelledMultiPo() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getAllCancelledMultiPoDeliveryDocuments();
    try {
      InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(
          String.format(
              CANCELLED_PO_INSTRUCTION,
              deliveryDocuments.get(0).getPurchaseReferenceNumber(),
              deliveryDocuments.size() > 1
                  ? " + " + (deliveryDocuments.size() - 1) + CANCELLED_PO_INSTRUCTION_MORE
                  : ReceivingConstants.EMPTY_STRING,
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()),
          e.getMessage());
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_AllCancelledMultiPoLine() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getAllCancelledMultiPoLineDeliveryDocuments();
    try {
      InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(
          String.format(
              CANCELLED_PO_INSTRUCTION,
              deliveryDocuments.get(0).getPurchaseReferenceNumber(),
              deliveryDocuments.size() > 1
                  ? " + " + (deliveryDocuments.size() - 1) + CANCELLED_PO_INSTRUCTION_MORE
                  : ReceivingConstants.EMPTY_STRING,
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()),
          e.getMessage());
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_SingleCancelledMultiPo() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getCancelledSinglePoDeliveryDocuments();
    try {
      InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(
          String.format(
              CANCELLED_PO_INSTRUCTION,
              deliveryDocuments.get(0).getPurchaseReferenceNumber(),
              deliveryDocuments.size() > 1
                  ? " + " + (deliveryDocuments.size() - 1) + CANCELLED_PO_INSTRUCTION_MORE
                  : ReceivingConstants.EMPTY_STRING,
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()),
          e.getMessage());
    }
  }

  @Test
  public void testValidateCancelledPoPoLineStatus_SingleCancelledMultiPoLine() {
    List<DeliveryDocument> deliveryDocuments =
        MockInstruction.getCancelledSinglePoLineDeliveryDocuments();
    try {
      InstructionUtils.filterCancelledPoPoLine(deliveryDocuments);
      AssertJUnit.fail();
    } catch (ReceivingException e) {
      AssertJUnit.assertEquals(
          String.format(
              ReceivingException.CANCELLED_PO_INSTRUCTION,
              deliveryDocuments.get(0).getPurchaseReferenceNumber(),
              deliveryDocuments.size() > 1
                  ? " + " + (deliveryDocuments.size() - 1) + CANCELLED_PO_INSTRUCTION_MORE
                  : ReceivingConstants.EMPTY_STRING,
              deliveryDocuments.get(0).getDeliveryDocumentLines().get(0).getItemNbr()),
          e.getMessage());
    }
  }

  @Test
  public void testSetFdeCreateContainerRequestReceivingUnit() {
    Content content = new Content();
    ContainerModel containerModel = new ContainerModel();
    containerModel.setContents(Collections.singletonList(content));
    FdeCreateContainerRequest fdeCreateContainerRequest = new FdeCreateContainerRequest();
    fdeCreateContainerRequest.setContainer(containerModel);

    InstructionUtils.setFdeCreateContainerRequestReceivingUnit(
        fdeCreateContainerRequest, ReceivingConstants.PALLET);
    assertEquals(
        fdeCreateContainerRequest.getContainer().getContents().get(0).getReceivingUnit(),
        ReceivingConstants.PALLET);
  }

  @Test
  public void testAtlasConvertItemReturnsFalseForAllItems() throws IOException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    instructionUtils.validateAtlasConvertedItems(deliveryDocumentList, httpHeaders);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertFalse(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
  }

  @Test
  public void testAtlasConvertItemReturnsTrueForAllItems()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(572730927L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(572730927L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenReturn(Collections.singletonList(itemConfigDetails));
    instructionUtils.validateAtlasConvertedItems(deliveryDocumentList, httpHeaders);
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        assertTrue(deliveryDocumentLine.getAdditionalInfo().isAtlasConvertedItem());
      }
    }
  }

  @Test(expectedExceptions = ReceivingException.class)
  public void testAtlasConvertItemReturnsException()
      throws IOException, ItemConfigRestApiClientException, ReceivingException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      for (DeliveryDocumentLine deliveryDocumentLine :
          deliveryDocument.getDeliveryDocumentLines()) {
        deliveryDocumentLine.setItemNbr(572730927L);
      }
    }
    ItemConfigDetails itemConfigDetails = mockItemConfigDetails();
    itemConfigDetails.setItem(String.valueOf(572730927L));
    when(itemConfigApiClient.searchAtlasConvertedItems(anySet(), any(HttpHeaders.class)))
        .thenThrow(
            new ItemConfigRestApiClientException(
                "error-123", HttpStatus.SERVICE_UNAVAILABLE, "service down"));
    instructionUtils.validateAtlasConvertedItems(deliveryDocumentList, httpHeaders);
  }

  private ItemConfigDetails mockItemConfigDetails() {
    return ItemConfigDetails.builder()
        .createdDateTime(createTs)
        .desc(itemDesc)
        .item(itemNumber)
        .build();
  }

  @Test
  public void testValidateItemXBlocked_xBlockedItem() {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    DeliveryDocumentLine documentLine1 =
        deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0);
    documentLine1.setHandlingCode("X");
    try {
      InstructionUtils.validateItemXBlocked(documentLine1);
      fail();
    } catch (ReceivingBadDataException exc) {
      assertEquals(ExceptionCodes.ITEM_X_BLOCKED_ERROR, exc.getErrorCode());
    }
  }

  @Test
  public void testFilterOutXBlockedItems_XBlockItemsPresent() throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    DeliveryDocumentLine documentLine1 =
        deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0);
    documentLine1.setHandlingCode("X");
    try {
      InstructionUtils.checkForXBlockedItems(deliveryDocuments1);
      fail();
    } catch (ReceivingBadDataException exc) {
      assertEquals(ExceptionCodes.ITEM_X_BLOCKED_ERROR, exc.getErrorCode());
    }
  }

  @Test
  public void testFilterOutXBlockedItems_XBlockItemsPresent_NoPoLineAdditionalInfo()
      throws ReceivingException {
    List<DeliveryDocument> deliveryDocuments1 = MockInstruction.getDeliveryDocuments();
    DeliveryDocumentLine documentLine1 =
        deliveryDocuments1.get(0).getDeliveryDocumentLines().get(0);
    documentLine1.setAdditionalInfo(null);
    documentLine1.setHandlingCode("X");
    try {
      InstructionUtils.checkForXBlockedItems(deliveryDocuments1);
      fail();
    } catch (ReceivingBadDataException exc) {
      assertEquals(ExceptionCodes.ITEM_X_BLOCKED_ERROR, exc.getErrorCode());
    }
  }

  @Test
  public void testFilterValidDeliveryDocumentsWithLineLevelFbq_Valid() {
    // create a delivery document with valid line level FBQ
    DeliveryDocumentLine line1 = new DeliveryDocumentLine();
    line1.setPurchaseRefType(PurchaseReferenceType.CROSSMU.name());
    line1.setActiveChannelMethods(Collections.singletonList(PurchaseReferenceType.CROSSMU.name()));
    line1.setFreightBillQty(1);

    DeliveryDocument deliveryDoc1 = new DeliveryDocument();
    deliveryDoc1.setImportInd(false);
    deliveryDoc1.setDeliveryDocumentLines(Collections.singletonList(line1));

    // test the method with the valid delivery document
    try {
      InstructionUtils.filterValidDeliveryDocumentsWithLineLevelFbq(deliveryDoc1);
    } catch (ReceivingBadDataException e) {
      fail();
    }
  }

  @Test
  public void testFilterValidDeliveryDocumentsWithLineLevelFbq_SSTKU() {
    // create a delivery document with valid line level FBQ
    DeliveryDocumentLine line1 = new DeliveryDocumentLine();
    line1.setPurchaseRefType(PurchaseReferenceType.SSTKU.name());
    line1.setActiveChannelMethods(Collections.singletonList(PurchaseReferenceType.SSTKU.name()));
    line1.setFreightBillQty(0);

    DeliveryDocument deliveryDoc1 = new DeliveryDocument();
    deliveryDoc1.setImportInd(false);
    deliveryDoc1.setDeliveryDocumentLines(Collections.singletonList(line1));

    // test the method with the valid delivery document
    try {
      InstructionUtils.filterValidDeliveryDocumentsWithLineLevelFbq(deliveryDoc1);
    } catch (ReceivingBadDataException e) {
      fail();
    }
  }

  @Test
  public void testFilterValidDeliveryDocumentsWithLineLevelFbq_Invalid() {
    // create a delivery document with invalid line level FBQ
    DeliveryDocumentLine line2 = new DeliveryDocumentLine();
    line2.setPurchaseRefType(PurchaseReferenceType.CROSSMU.name());
    line2.setFreightBillQty(0);

    DeliveryDocument deliveryDoc2 = new DeliveryDocument();
    deliveryDoc2.setDeliveryNumber(1234L);
    deliveryDoc2.setPurchaseReferenceNumber("abc1234");
    deliveryDoc2.setImportInd(true);
    deliveryDoc2.setDeliveryDocumentLines(Collections.singletonList(line2));

    // test the method with the invalid delivery document
    try {
      InstructionUtils.filterValidDeliveryDocumentsWithLineLevelFbq(deliveryDoc2);
      fail();
    } catch (ReceivingBadDataException exc) {
      assertEquals(ExceptionCodes.LINE_LEVEL_FBQ_MISSING_ERROR, exc.getErrorCode());
    }
  }

  @Test
  public void testIsHazmatItem_WithValidScenario() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getValidHazmat());
    assertTrue(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithNoTransportationModes() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(null);
    assertFalse(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithTransportationModesAsORMD() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getORMD());
    assertFalse(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_WithNoGroundTransportationModes() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getNotGroundTransport());
    assertFalse(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_NoDotNumberId() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getNoDotNumber());
    assertFalse(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testIsHazmatItem_NoDotHazardousClass() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setIsHazmat(true);
    deliveryDocumentLine.setTransportationModes(MockTransportationModes.getNoDotHazardousClass());
    assertFalse(InstructionUtils.isHazmatItem(deliveryDocumentLine));
  }

  @Test
  public void testValidateThresholdForSellByDate_PackDateError() throws ReceivingException {
    try {
      documentLine.setRotateDate(Date.from(Instant.now().plus(1, ChronoUnit.DAYS)));
      documentLine.setWarehouseMinLifeRemainingToReceive(-1);
      InstructionUtils.validateThresholdForSellByDate(
          true, Boolean.TRUE, documentLine, false, true);
    } catch (ReceivingBadDataException e) {
      assertEquals(e.getErrorCode(), "GLS-RCV-INVALID-PRODUCT-DATE-ERROR-CODE-400");
      assertTrue(e.getMessage().contains("The pack date cannot be in the future"));
    }
  }

  @Test
  public void checkIfNewInstructionCanBeCreated_trueWithFBQ() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(7653763);
    deliveryDocument.setPurchaseReferenceNumber("12344");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(2);
    doReturn(fbqBasedOpenQtyCalculator)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class));
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder()
            .openQty(4L)
            .totalReceivedQty(1)
            .maxReceiveQty(5L)
            .flowType(OpenQtyFlowType.FBQ)
            .build();
    doReturn(openQtyResult).when(fbqBasedOpenQtyCalculator).calculate(anyLong(), any(), any());
    doReturn(1)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    ImmutablePair<Long, Long> openQtyReceivedQtyPair = new ImmutablePair<>(4L, 1L);
    Boolean isQtyAvailable =
        instructionUtils.checkIfNewInstructionCanBeCreated(
            deliveryDocumentLine, deliveryDocument, openQtyReceivedQtyPair);
    assertEquals(isQtyAvailable, Boolean.TRUE);
  }

  @Test
  public void checkIfNewInstructionCanBeCreated_falseWithFBQ() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(7653763);
    deliveryDocument.setPurchaseReferenceNumber("12344");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(2);
    doReturn(fbqBasedOpenQtyCalculator)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class));
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder()
            .openQty(1L)
            .totalReceivedQty(4)
            .maxReceiveQty(5L)
            .flowType(OpenQtyFlowType.FBQ)
            .build();
    doReturn(openQtyResult).when(fbqBasedOpenQtyCalculator).calculate(anyLong(), any(), any());
    doReturn(4)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByDeliveryAndPoPoLineAndInstructionSetIdIsNull(
            anyLong(), anyString(), anyInt());
    ImmutablePair<Long, Long> openQtyReceivedQtyPair = new ImmutablePair<>(1L, 4L);
    Boolean isQtyAvailable =
        instructionUtils.checkIfNewInstructionCanBeCreated(
            deliveryDocumentLine, deliveryDocument, openQtyReceivedQtyPair);
    assertEquals(isQtyAvailable, Boolean.FALSE);
  }

  @Test
  public void checkIfNewInstructionCanBeCreated_trueWithDefault() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(7653763);
    deliveryDocument.setPurchaseReferenceNumber("12344");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(2);
    doReturn(defaultOpenQtyCalculator)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class));
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder().openQty(4L).totalReceivedQty(1).maxReceiveQty(5L).build();
    doReturn(openQtyResult).when(defaultOpenQtyCalculator).calculate(anyLong(), any(), any());
    doReturn(1L)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    ImmutablePair<Long, Long> openQtyReceivedQtyPair = new ImmutablePair<>(4L, 1L);
    Boolean isQtyAvailable =
        instructionUtils.checkIfNewInstructionCanBeCreated(
            deliveryDocumentLine, deliveryDocument, openQtyReceivedQtyPair);
    assertEquals(isQtyAvailable, Boolean.TRUE);
  }

  @Test
  public void checkIfNewInstructionCanBeCreated_falseWithDefault() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setDeliveryNumber(7653763);
    deliveryDocument.setPurchaseReferenceNumber("12344");
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setVendorPack(2);
    deliveryDocumentLine.setWarehousePack(2);
    doReturn(defaultOpenQtyCalculator)
        .when(configUtils)
        .getConfiguredInstance(
            anyString(),
            eq(OPEN_QTY_CALCULATOR),
            eq(DEFAULT_OPEN_QTY_CALCULATOR),
            eq(OpenQtyCalculator.class));
    OpenQtyResult openQtyResult =
        OpenQtyResult.builder().openQty(4L).totalReceivedQty(1).maxReceiveQty(5L).build();
    doReturn(openQtyResult).when(defaultOpenQtyCalculator).calculate(anyLong(), any(), any());
    doReturn(4L)
        .when(instructionRepository)
        .getSumOfProjectedReceivedQuantityOfPendingInstructionsByPoPoLine(anyString(), anyInt());
    ImmutablePair<Long, Long> openQtyReceivedQtyPair = new ImmutablePair<>(4L, 1L);
    Boolean isQtyAvailable =
        instructionUtils.checkIfNewInstructionCanBeCreated(
            deliveryDocumentLine, deliveryDocument, openQtyReceivedQtyPair);
    assertEquals(isQtyAvailable, Boolean.FALSE);
  }

  @Test
  public void getItemCollisionInstruction_successTest() {
    Long DELIVERY_NO = Long.valueOf(34355);
    String COMMONITEMS_LIST = "3243243,934343";
    String EXPECTED_CONTENT =
        "There are items [3243243,934343] on delivery 34355 already being received at a location on this conveyor. Please receive this delivery at different conveyor or manually.";
    Instruction collisionInstructionMsg =
        InstructionUtils.getItemCollisionInstruction(DELIVERY_NO, COMMONITEMS_LIST);
    assertEquals(collisionInstructionMsg.getInstructionMsg(), EXPECTED_CONTENT);
  }
}
