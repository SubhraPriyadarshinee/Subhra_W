package com.walmart.move.nim.receiving.rx.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
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

public class RxInstructionSearchRequestHandlerTest {

  @InjectMocks private RxInstructionSearchRequestHandler rxInstructionSearchRequestHandler;

  @Mock private InstructionRepository instructionRepository;
  @Mock private TenantSpecificConfigReader configUtils;

  @Mock private DeliveryStatusPublisher deliveryStatusPublisher;

  private InstructionSearchRequest instructionSearchRequest;
  private List<Instruction> instructionList = new ArrayList<Instruction>();

  @BeforeClass
  public void initMocks() {
    MockitoAnnotations.initMocks(this);

    instructionSearchRequest = new InstructionSearchRequest();
    instructionSearchRequest.setDeliveryNumber(Long.valueOf("21119003"));
    instructionSearchRequest.setIncludeInstructionSet(true);

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
    instruction1.setInstructionCode("RxBuildUnitScan");
    instruction1.setInstructionMsg("RxBuildContainerByScanningUnits");
    instruction1.setItemDescription("HEM VALUE PACK (5)");
    instruction1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction1.setMove(null);
    instruction1.setPoDcNumber("32899");
    instruction1.setPrintChildContainerLabels(false);
    instruction1.setPurchaseReferenceNumber("9763140005");
    instruction1.setPurchaseReferenceLineNumber(Integer.valueOf(1));
    instruction1.setProjectedReceiveQty(3);
    instruction1.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
    instruction1.setReceivedQuantity(3);
    instruction1.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    instruction1.setProviderId("Sstk");
    instruction1.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction1.setActivityName("RxSstk");
    instruction1.setDeliveryDocument(
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
    instruction2.setInstructionCode("RxBuildPallet");
    instruction2.setInstructionMsg("RxBuildPallet");
    instruction2.setItemDescription("HEM VALUE PACK (4)");
    instruction2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction2.setMove(null);
    instruction2.setPoDcNumber("32899");
    instruction2.setPrintChildContainerLabels(false);
    instruction2.setPurchaseReferenceNumber("9763140004");
    instruction2.setPurchaseReferenceLineNumber(1);
    instruction2.setProjectedReceiveQty(18);
    instruction2.setProviderId("RxSstk");
    instruction2.setReceivedQuantity(9);
    instruction2.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.EACHES);
    instruction2.setReceivedQuantityUOM(ReceivingConstants.Uom.EACHES);
    instruction2.setActivityName("RxSstk");
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
  public void testGetInstructionSummaryWhenDeliveryIsArrived() {
    TenantContext.setFacilityNum(32679);
    instructionSearchRequest.setDeliveryStatus(DeliveryStatus.ARV.toString());

    when(instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(anyLong()))
        .thenReturn(instructionList);
    when(deliveryStatusPublisher.publishDeliveryStatus(
            Long.valueOf("21119003"), DeliveryStatus.OPEN.toString(), null, new HashMap<>()))
        .thenReturn(null);
    doReturn(false)
        .when(configUtils)
        .getConfiguredFeatureFlag(
            getFacilityNum().toString(),
            ReceivingConstants.IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP,
            false);
    List<InstructionSummary> response =
        rxInstructionSearchRequestHandler.getInstructionSummary(
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
    assertEquals(response.get(0).getProjectedReceiveQty().intValue(), 1);
    assertEquals(response.get(0).getReceivedQuantity().intValue(), 1);
    assertEquals(response.get(0).getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.WHPK);
    assertEquals(response.get(0).getReceivedQuantityUOM(), ReceivingConstants.Uom.WHPK);

    assertEquals(response.get(1).getProjectedReceiveQty().intValue(), 6);
    assertEquals(response.get(1).getReceivedQuantity().intValue(), 3);
    assertEquals(response.get(1).getProjectedReceiveQtyUOM(), ReceivingConstants.Uom.VNPK);
    assertEquals(response.get(1).getReceivedQuantityUOM(), ReceivingConstants.Uom.VNPK);

    assertEquals(
        response.get(1).getPurchaseReferenceNumber(),
        instructionList.get(1).getPurchaseReferenceNumber());
    assertEquals(
        Integer.valueOf(response.get(1).getPurchaseReferenceLineNumber()),
        instructionList.get(1).getPurchaseReferenceLineNumber());

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndInstructionCodeIsNotNull(Long.valueOf("21119003"));
    verify(deliveryStatusPublisher, times(1))
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
        rxInstructionSearchRequestHandler.getInstructionSummary(
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
        rxInstructionSearchRequestHandler.getInstructionSummary(
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
        rxInstructionSearchRequestHandler.getInstructionSummary(
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

    verify(instructionRepository, times(1))
        .findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
            Long.valueOf("21119003"), "123456789");
  }
}
