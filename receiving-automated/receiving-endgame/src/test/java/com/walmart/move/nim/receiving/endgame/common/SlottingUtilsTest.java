package com.walmart.move.nim.receiving.endgame.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.walmart.atlas.global.fc.service.IOutboxPublisherService;
import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationFromSlotting;
import com.walmart.move.nim.receiving.endgame.model.DivertDestinationToHawkeye;
import com.walmart.move.nim.receiving.endgame.model.DivertRequestItem;
import com.walmart.move.nim.receiving.endgame.model.EndGameSlottingData;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertRequest;
import com.walmart.move.nim.receiving.endgame.model.SlottingDivertResponse;
import java.util.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class SlottingUtilsTest extends ReceivingTestBase {

  private Map<String, DivertRequestItem> upcDivertRequestItemMap;
  private DivertDestinationFromSlotting destination;
  private SlottingDivertResponse slottingDivertResponse;
  private static long deliveryNumber = 30008889;
  private static String caseUPC = "00028000113002";
  private Map<String, String> slotingAttributes = null;
  @MockBean private IOutboxPublisherService iOutboxPublisherService;

  @BeforeClass
  public void initMocks() {
    upcDivertRequestItemMap = new HashMap<>();

    slotingAttributes = new HashMap<>();
    slotingAttributes.put(EndgameConstants.ATTRIBUTES_FTS, "false");

    slottingDivertResponse = new SlottingDivertResponse();
    slottingDivertResponse.setMessageId(UUID.randomUUID().toString());
    destination = new DivertDestinationFromSlotting();
    destination.setCaseUPC(caseUPC);
    destination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    destination.setDivertLocation("DECANT");
    destination.setItemNbr(9213971L);
    destination.setAttributes(slotingAttributes);
    slottingDivertResponse.setDivertLocations(Collections.singletonList(destination));
  }

  @Test
  public void testUPCDivertRequestMapGeneration() {
    List<PurchaseOrder> purchaseOrderList = EndGameUtilsTestCase.getDelivery().getPurchaseOrders();
    purchaseOrderList.forEach(
        po ->
            po.getLines()
                .forEach(
                    line ->
                        line.setOpenQuantity(
                            line.getOrdered().getQuantity()
                                + line.getOvgThresholdLimit().getQuantity())));
    upcDivertRequestItemMap =
        SlottingUtils.generateUPCDivertRequestMap(
            purchaseOrderList,
            prepareProcessedMap(EndGameUtilsTestCase.getDelivery()),
            Collections.singleton(9213971L),
            "$.dcProperties.isNewItem",
            "$.assortmentShipper[*]",
            Collections.EMPTY_MAP);
    assertEquals(1, upcDivertRequestItemMap.size());
  }

  @Test
  public void testAssortmentUPC() {
    List<PurchaseOrder> purchaseOrderList =
        EndGameUtilsTestCase.getAssortmentDelivery().getPurchaseOrders();
    purchaseOrderList.forEach(
        po ->
            po.getLines()
                .forEach(
                    line ->
                        line.setOpenQuantity(
                            line.getOrdered().getQuantity()
                                + line.getOvgThresholdLimit().getQuantity())));
    upcDivertRequestItemMap =
        SlottingUtils.generateUPCDivertRequestMap(
            purchaseOrderList,
            prepareProcessedMap(EndGameUtilsTestCase.getAssortmentDelivery()),
            Collections.singleton(577186241L),
            "$.dcProperties.isNewItem",
            "$.assortmentShipper[*]",
            Collections.EMPTY_MAP);

    DivertRequestItem divertRequestItem =
        upcDivertRequestItemMap.get("00405771862411-F55CDC31AB754BB68FE0B39041159D63");

    // This signifies assortedItem as possibleUPC size > 1
    assertEquals(13, divertRequestItem.getPossibleUPCs().size());
    assertTrue(
        divertRequestItem
            .getPossibleUPCs()
            .contains(EndgameConstants.AT + "00405771862411" + EndgameConstants.AT));
    assertEquals(1, upcDivertRequestItemMap.size());
  }

  @Test(dependsOnMethods = "testUPCDivertRequestMapGeneration")
  public void testSlottingDivertRequestCreation() {
    SlottingDivertRequest slottingDivertRequest =
        SlottingUtils.populateSlottingDivertRequest(upcDivertRequestItemMap);
    // As per new contracts
    assertEquals(slottingDivertRequest.getDivertRequestItems().get(0).getCaseUPC(), caseUPC);
    assertEquals(
        182, slottingDivertRequest.getDivertRequestItems().get(0).getTotalOpenQty().intValue());
    assertEquals(
        162, slottingDivertRequest.getDivertRequestItems().get(0).getTotalOrderQty().intValue());
  }

  @Test(dependsOnMethods = "testSlottingDivertRequestCreation")
  public void testPopulateEndgameSlottingData() {
    EndGameSlottingData endgameSlottingData =
        SlottingUtils.populateEndgameSlottingData(
            slottingDivertResponse.getDivertLocations(),
            deliveryNumber,
            "123",
            upcDivertRequestItemMap);
    assertEquals(deliveryNumber, endgameSlottingData.getDeliveryNumber().longValue());
    assertEquals(caseUPC, endgameSlottingData.getDestinations().get(0).getCaseUPC());
    assertEquals("DECANT", endgameSlottingData.getDestinations().get(0).getDestination());
    assertEquals(182, endgameSlottingData.getDestinations().get(0).getMaxCaseQty().intValue());
    upcDivertRequestItemMap.clear();
  }

  @Test
  public void testPopulateSlottingEntity() {
    DivertDestinationToHawkeye destination =
        DivertDestinationToHawkeye.builder()
            .destination("DECANT")
            .caseUPC(caseUPC)
            .possibleUPCs(Arrays.asList(caseUPC))
            .build();
    SlottingDestination slottingDestination = SlottingUtils.populateSlottingEntity(destination);
    assertEquals(caseUPC, slottingDestination.getCaseUPC());
    assertEquals("DECANT", slottingDestination.getDestination());
    assertTrue(slottingDestination.getId() >= 0);
  }

  private Map<String, Map<String, Object>> prepareProcessedMap(Delivery delivery) {
    Map<String, Map<String, Object>> itemAttributesMap = new HashMap<>();
    delivery
        .getPurchaseOrders()
        .forEach(
            po -> {
              po.getLines()
                  .forEach(
                      purchaseOrderLine -> {
                        itemAttributesMap.put(
                            purchaseOrderLine.getItemDetails().getNumber() + "",
                            getItemMap(purchaseOrderLine.getItemDetails().getNumber()));
                      });
            });
    return itemAttributesMap;
  }

  private Map<String, Object> getItemMap(Long number) {
    Map<String, Object> itemDetails = null;

    switch (number.intValue()) {
      case 9213971:
        itemDetails = EndGameUtilsTestCase.getItemDetails();
        break;
      case 577186241:
        itemDetails = EndGameUtilsTestCase.getAssortmentItemDetails();
        break;
      default:
        itemDetails = EndGameUtilsTestCase.getItemDetails();
        break;
    }

    return itemDetails;
  }
}
