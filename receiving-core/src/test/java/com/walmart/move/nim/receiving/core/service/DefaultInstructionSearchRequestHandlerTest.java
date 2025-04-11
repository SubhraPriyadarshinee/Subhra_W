package com.walmart.move.nim.receiving.core.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class DefaultInstructionSearchRequestHandlerTest {

  @InjectMocks
  private DefaultInstructionSearchRequestHandler defaultInstructionSearchRequestHandler;

  @Mock private InstructionRepository instructionRepository;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  @Mock private TenantSpecificConfigReader configUtils;

  private InstructionSearchRequest instructionSearchRequest;
  private List<Instruction> instructionList = new ArrayList<Instruction>();
  private Gson gson;

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(Long.valueOf("21119003"));

    Instruction instruction1 = new Instruction();
    instruction1.setId(Long.valueOf("1"));
    instruction1.setContainer(null);
    instruction1.setChildContainers(null);
    instruction1.setCreateTs(new Date());
    instruction1.setCreateUserId("sysadmin");
    instruction1.setLastChangeTs(new Date());
    instruction1.setLastChangeUserId("sysadmin");
    instruction1.setDeliveryNumber(Long.valueOf("21119003"));
    instruction1.setGtin("00000943037204");
    instruction1.setInstructionCode("Build Container");
    instruction1.setInstructionMsg("Build the Container");
    instruction1.setItemDescription("HEM VALUE PACK (5)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction1.setMove(null);
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(false);
    instruction1.setPurchaseReferenceNumber("9763140005");
    instruction1.setPurchaseReferenceLineNumber(Integer.valueOf(1));
    instruction1.setProjectedReceiveQty(2);
    instruction1.setProviderId("DA");
    instruction1.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction1.setActivityName("DANonCon");
    instruction1.setDeliveryDocument(
        "{\n"
            + "    \"purchaseReferenceNumber\": \"1422634802\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"480889\",\n"
            + "    \"vendorNbrDeptSeq\": 480889940,\n"
            + "    \"deptNumber\": \"94\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"28\",\n"
            + "    \"poDCNumber\": \"32612\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"gtin\": \"01123840356119\",\n"
            + "            \"itemUPC\": \"01123840356119\",\n"
            + "            \"caseUPC\": \"11188122713797\",\n"
            + "            \"purchaseReferenceNumber\": \"1422634802\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 23.89,\n"
            + "            \"vendorPackCost\": 23.89,\n"
            + "            \"vnpkQty\": 1,\n"
            + "            \"whpkQty\": 1,\n"
            + "            \"orderableQuantity\": 1,\n"
            + "            \"warehousePackQuantity\": 1,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"openQty\": 475,\n"
            + "            \"expectedQty\": 500,\n"
            + "            \"overageQtyLimit\": 20,\n"
            + "            \"itemNbr\": 6332831,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 5,\n"
            + "            \"palletHi\": 5,\n"
            + "            \"vnpkWgtQty\": 10,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.852,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"8DAYS\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"Ice Cream\",\n"
            + "            \"itemDescription2\": \"<T&S>\",\n"
            + "            \"isConveyable\": false,\n"
            + "            \"warehouseRotationTypeCode\": \"3\",\n"
            + "            \"firstExpiryFirstOut\": true,\n"
            + "            \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "            \"profiledWarehouseArea\": \"CPS\",\n"
            + "            \"promoBuyInd\": \"N\",\n"
            + "            \"additionalInfo\": {\n"
            + "                \"warehouseAreaCode\": \"8\",\n"
            + "                \"warehouseAreaDesc\": \"Dry Produce\",\n"
            + "                \"warehouseGroupCode\": \"DD\",\n"
            + "                \"isNewItem\": false,\n"
            + "                \"profiledWarehouseArea\": \"CPS\",\n"
            + "                \"warehouseRotationTypeCode\": \"3\",\n"
            + "                \"recall\": false,\n"
            + "                \"weight\": 3.325,\n"
            + "                \"weightFormatTypeCode\": \"F\",\n"
            + "                \"omsWeightFormatTypeCode\": \"F\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "                \"isHACCP\": false,\n"
            + "                \"primeSlotSize\": 0,\n"
            + "                \"isHazardous\": 0,\n"
            + "                \"atlasConvertedItem\": false,\n"
            + "                \"isWholesaler\": false\n"
            + "            },\n"
            + "            \"operationalInfo\": {\n"
            + "                \"state\": \"ACTIVE\"\n"
            + "            },\n"
            + "            \"activeChannelMethods\": [],\n"
            + "            \"department\": \"98\",\n"
            + "            \"vendorStockNumber\": \"11357\",\n"
            + "            \"totalReceivedQty\": 25,\n"
            + "            \"maxAllowedOverageQtyIncluded\": false,\n"
            + "            \"lithiumIonVerificationRequired\": false,\n"
            + "            \"limitedQtyVerificationRequired\": false,\n"
            + "            \"isNewItem\": false\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 243,\n"
            + "    \"weight\": 0,\n"
            + "    \"cubeQty\": 0,\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"WRK\",\n"
            + "    \"poTypeCode\": 28,\n"
            + "    \"totalBolFbq\": 0,\n"
            + "    \"deliveryLegacyStatus\": \"WRK\",\n"
            + "    \"purchaseReferenceMustArriveByDate\": \"Nov 28, 2019 12:00:00 AM\",\n"
            + "    \"stateReasonCodes\": [\n"
            + "        \"WORKING\"\n"
            + "    ],\n"
            + "    \"deliveryNumber\": 12345678,\n"
            + "    \"importInd\": false\n"
            + "}");
    instructionList.add(instruction1);

    Instruction instruction2 = new Instruction();
    instruction2.setId(Long.valueOf("2"));
    instruction2.setContainer(null);
    instruction2.setChildContainers(null);
    instruction2.setCreateTs(new Date());
    instruction2.setCreateUserId("sysadmin");
    instruction2.setLastChangeTs(new Date());
    instruction2.setLastChangeUserId("sysadmin");
    instruction2.setCompleteTs(new Date());
    instruction2.setCompleteUserId("sysadmin");
    instruction2.setDeliveryNumber(Long.valueOf("21119003"));
    instruction2.setGtin("00000943037194");
    instruction2.setInstructionCode("Build Container");
    instruction2.setInstructionMsg("Build the Container");
    instruction2.setItemDescription("HEM VALUE PACK (4)");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction2.setMove(null);
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(false);
    instruction2.setPurchaseReferenceNumber("9763140004");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(2);
    instruction2.setProviderId("DA");
    instruction2.setReceivedQuantity(2);
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction2.setFirstExpiryFirstOut(Boolean.TRUE);
    instruction2.setActivityName("DANonCon");
    instruction2.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"CROSSU\",\n"
            + "                \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "                \"purchaseReferenceLineNumber\": 9,\n"
            + "                \"purchaseReferenceLineStatus\": \"PARTIALLY_RECEIVED\",\n"
            + "                \"itemNbr\": 574322171,\n"
            + "                \"itemUPC\": \"00673419302784\",\n"
            + "                \"caseUPC\": \"10673419302781\",\n"
            + "                \"expectedQty\": 600,\n"
            + "                \"expectedQtyUOM\": \"ZA\",\n"
            + "                \"vnpkQty\": 3,\n"
            + "                \"whpkQty\": 3,\n"
            + "                \"vendorStockNumber\": \"6251454\",\n"
            + "                \"vendorPackCost\": 41.97,\n"
            + "                \"whpkSell\": 42.41,\n"
            + "                \"vnpkWgtQty\": 2.491,\n"
            + "                \"vnpkWgtUom\": \"LB\",\n"
            + "                \"vnpkcbqty\": 0.405,\n"
            + "                \"vnpkcbuomcd\": \"CF\",\n"
            + "                \"event\": \"POS REPLEN\",\n"
            + "                \"palletTi\": 30,\n"
            + "                \"palletHi\": 4,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\",\n"
            + "                \"size\": \"\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }");
    instructionList.add(instruction2);
  }

  @AfterMethod
  public void restRestUtilCalls() {
    reset(instructionRepository);
    reset(deliveryStatusPublisher);
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsArrivedAndFeatureFlagIsFalse() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());

    when(instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong()))
        .thenReturn(instructionList);

    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(false);

    when(deliveryStatusPublisher.publishDeliveryStatus(
            Long.valueOf("21119003"), DeliveryStatus.OPEN.toString(), null, new HashMap<>()))
        .thenReturn(null);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    assert (response.get(0).getDeliveryDocument().contains("warehouseAreaCode"));
    assert (response.get(0).getDeliveryDocument().contains("warehouseAreaDesc"));
    assertEquals(
        response.get(0).getPurchaseReferenceNumber(),
        instructionList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(0).getPurchaseReferenceLineNumber()),
        instructionList.get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(0).getFirstExpiryFirstOut(), instructionList.get(0).getFirstExpiryFirstOut());
    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(1).getFirstExpiryFirstOut(), instructionList.get(1).getFirstExpiryFirstOut());
    assertEquals(response.get(1).getActivityName(), "DANonCon");

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(Long.valueOf("21119003"));
    verify(deliveryStatusPublisher, times(1))
        .publishDeliveryStatus(
            Long.valueOf("21119003"), DeliveryStatus.OPEN.toString(), null, new HashMap<>());
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsArrivedAndFeatureFlagIsTrue() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());

    when(instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong()))
        .thenReturn(instructionList);

    when(configUtils.isFeatureFlagEnabled(
            ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED))
        .thenReturn(true);

    when(deliveryStatusPublisher.publishDeliveryStatus(
            Long.valueOf("21119003"), DeliveryStatus.OPEN.toString(), null, new HashMap<>()))
        .thenReturn(null);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    assertEquals(
        response.get(0).getPurchaseReferenceNumber(),
        instructionList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(0).getPurchaseReferenceLineNumber()),
        instructionList.get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(0).getFirstExpiryFirstOut(), instructionList.get(0).getFirstExpiryFirstOut());
    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(1).getFirstExpiryFirstOut(), instructionList.get(1).getFirstExpiryFirstOut());
    assertEquals(response.get(1).getActivityName(), "DANonCon");

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(Long.valueOf("21119003"));
    verify(deliveryStatusPublisher, times(0))
        .publishDeliveryStatus(
            Long.valueOf("21119003"), DeliveryStatus.OPEN.toString(), null, new HashMap<>());
  }

  @Test
  public void testGetInstructionSummaryWhenDeliveryIsOpen() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionSearchRequest.setProblemTagId(null);

    when(instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong()))
        .thenReturn(instructionList);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    assertEquals(
        response.get(0).getPurchaseReferenceNumber(),
        instructionList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(0).getPurchaseReferenceLineNumber()),
        instructionList.get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());
    assertEquals(response.get(1).getActivityName(), "DANonCon");

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(Long.valueOf("21119003"));
  }

  @Test
  public void testGetInstructionSummaryWhenGivenProblemTagId() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionSearchRequest.setDeliveryNumber(null);
    instructionSearchRequest.setProblemTagId("123456789");

    when(instructionRepository.findByProblemTagIdAndInstructionCodeIsNotNull(anyString()))
        .thenReturn(instructionList);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    assertEquals(
        response.get(0).getPurchaseReferenceNumber(),
        instructionList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(0).getPurchaseReferenceLineNumber()),
        instructionList.get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());
    assertEquals(response.get(1).getActivityName(), "DANonCon");

    verify(instructionRepository, times(1))
        .findByProblemTagIdAndInstructionCodeIsNotNull("123456789");
  }

  @Test
  public void testGetInstructionSummaryWhenGivenDeliveryNumberAndProblemTagId() {
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionSearchRequest.setDeliveryNumber(Long.valueOf("21119003"));
    instructionSearchRequest.setProblemTagId("123456789");

    when(instructionRepository.findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
            anyLong(), anyString()))
        .thenReturn(instructionList);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            instructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    assertEquals(
        response.get(0).getPurchaseReferenceNumber(),
        instructionList.get(0).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(0).getPurchaseReferenceLineNumber()),
        instructionList.get(0).getPurchaseReferenceLineNumber());
    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());
    assertEquals(response.get(1).getActivityName(), "DANonCon");

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
            Long.valueOf("21119003"), "123456789");
  }

  @Test
  public void testGetInstructionSummaryWhenIncludeCompletedInstructionsSetFalse() {
    InstructionSearchRequest mockInstructionSearchRequest = getMockInstructionSearchRequest();
    mockInstructionSearchRequest.setIncludeCompletedInstructions(false);

    when(instructionRepository.findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            anyLong()))
        .thenReturn(instructionList);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
    verify(instructionRepository, times(0))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
    assertEquals(mockInstructionSearchRequest.isIncludeCompletedInstructions(), false);
  }

  @Test
  public void testGetInstructionSummaryWhenIncludeCompletedInstructionsDefaultTrue() {
    InstructionSearchRequest mockInstructionSearchRequest = getMockInstructionSearchRequest();
    when(instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong()))
        .thenReturn(instructionList);

    List<InstructionSummary> response =
        defaultInstructionSearchRequestHandler.getInstructionSummary(
            mockInstructionSearchRequest, new HashMap<>());

    assertEquals(response.size(), instructionList.size());
    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong());
    verify(instructionRepository, times(0))
        .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(anyLong());
    assertEquals(mockInstructionSearchRequest.isIncludeCompletedInstructions(), true);
  }

  private InstructionSearchRequest getMockInstructionSearchRequest() {
    InstructionSearchRequest mockInstructionSearchRequest = new InstructionSearchRequest();
    mockInstructionSearchRequest.setDeliveryStatus(DeliveryStatus.WRK.toString());
    mockInstructionSearchRequest.setDeliveryNumber(27526360L);
    return mockInstructionSearchRequest;
  }
}
