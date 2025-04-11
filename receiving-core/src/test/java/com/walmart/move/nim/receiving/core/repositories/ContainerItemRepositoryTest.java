package com.walmart.move.nim.receiving.core.repositories;

import static org.testng.Assert.assertEquals;

import com.walmart.move.nim.receiving.base.ReceivingTestBase;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class ContainerItemRepositoryTest extends ReceivingTestBase {

  @Autowired private ContainerItemRepository containerItemRepository;

  private ContainerItem containerItem;

  @BeforeClass
  public void setRootUp() {
    containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("23");
    containerItem.setTrackingId("a328990000000000000106519");
    containerItem.setPurchaseReferenceLineNumber(5);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setOutboundChannelMethod("CROSSU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setPoDeptNumber("0092");
    containerItem.setDeptNumber(1);
    containerItem.setItemNumber(1084445L);
    containerItem.setVendorGS128("");
    containerItem.setGtin("00049807100025");
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES); // by default EA
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(new Date());
    containerItem.setVendorNumber(123456789);
    containerItem.setLotNumber("LOT-12345");
    containerItem.setActualTi(3);
    containerItem.setActualHi(2);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);

    Distribution distribution = new Distribution();
    distribution.setAllocQty(1);
    distribution.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item = new HashMap<String, String>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "1084445");
    distribution.setItem(item);
    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(2);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    Map<String, String> item1 = new HashMap<String, String>();
    item1.put("financialReportingGroup", "US");
    item1.put("baseDivisionCode", "WM");
    item1.put("itemNbr", "1084445");
    distribution1.setItem(item1);
    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);
    distributions.add(distribution);

    containerItem.setDistributions(distributions);

    containerItemRepository.save(containerItem);
  }

  /**
   * This method will test
   * FindContainerItemByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndTrackId().
   */
  @Test
  public void
      testFindContainerItemByPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndTrackId() {

    ContainerItem resultContainerItem =
        containerItemRepository
            .findByTrackingIdAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                "a328990000000000000106519", "23", 5);
    assertEquals(
        resultContainerItem.getPurchaseReferenceNumber(),
        containerItem.getPurchaseReferenceNumber());
    assertEquals(resultContainerItem.getTrackingId(), containerItem.getTrackingId());
    assertEquals(
        resultContainerItem.getPurchaseReferenceLineNumber(),
        containerItem.getPurchaseReferenceLineNumber());
    assertEquals(
        resultContainerItem.getPurchaseReferenceNumber(),
        containerItem.getPurchaseReferenceNumber());
    assertEquals(
        resultContainerItem.getInboundChannelMethod(), containerItem.getInboundChannelMethod());
    assertEquals(
        resultContainerItem.getOutboundChannelMethod(), containerItem.getOutboundChannelMethod());
    assertEquals(
        resultContainerItem.getTotalPurchaseReferenceQty(),
        containerItem.getTotalPurchaseReferenceQty());
    assertEquals(resultContainerItem.getPurchaseCompanyId(), containerItem.getPurchaseCompanyId());
    assertEquals(resultContainerItem.getPoDeptNumber(), containerItem.getPoDeptNumber());
    assertEquals(resultContainerItem.getDeptNumber(), containerItem.getDeptNumber());
    assertEquals(resultContainerItem.getItemNumber(), containerItem.getItemNumber());
    assertEquals(resultContainerItem.getVendorGS128(), containerItem.getVendorGS128());
    assertEquals(resultContainerItem.getGtin(), containerItem.getGtin());
    assertEquals(resultContainerItem.getVnpkQty(), containerItem.getVnpkQty());
    assertEquals(resultContainerItem.getWhpkQty(), containerItem.getWhpkQty());
    assertEquals(resultContainerItem.getQuantity(), containerItem.getQuantity());
    assertEquals(resultContainerItem.getQuantityUOM(), containerItem.getQuantityUOM());
    assertEquals(resultContainerItem.getVendorPackCost(), containerItem.getVendorPackCost());
    assertEquals(resultContainerItem.getWhpkSell(), containerItem.getWhpkSell());
    assertEquals(resultContainerItem.getBaseDivisionCode(), containerItem.getBaseDivisionCode());
    assertEquals(resultContainerItem.getVendorNumber(), containerItem.getVendorNumber());
    assertEquals(resultContainerItem.getLotNumber(), containerItem.getLotNumber());
    assertEquals(resultContainerItem.getActualTi(), containerItem.getActualTi());
    assertEquals(resultContainerItem.getActualHi(), containerItem.getActualHi());
    assertEquals(resultContainerItem.getPackagedAsUom(), containerItem.getPackagedAsUom());
    assertEquals(
        resultContainerItem.getFinancialReportingGroupCode(),
        containerItem.getFinancialReportingGroupCode());
    assertEquals(
        resultContainerItem.getDistributions().get(0).getAllocQty(),
        containerItem.getDistributions().get(0).getAllocQty());
    assertEquals(
        resultContainerItem.getDistributions().get(0).getOrderId(),
        containerItem.getDistributions().get(0).getOrderId());
  }

  @Test
  public void testFindFirstByItemNumber() {
    Optional<ContainerItem> optionalContainerItem =
        containerItemRepository.findFirstByItemNumberOrderByIdDesc(Long.parseLong("1084445"));
    assertEquals(optionalContainerItem.get().getTrackingId(), containerItem.getTrackingId());
  }
}
