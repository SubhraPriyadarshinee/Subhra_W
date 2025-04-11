package com.walmart.move.nim.receiving.controller;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.PRINT_REQUEST_KEY;

import com.walmart.move.nim.receiving.core.common.ContainerUtils;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.ContainerItemRequest;
import com.walmart.move.nim.receiving.core.model.ContainerRequest;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.ReprintLabelRequest;
import com.walmart.move.nim.receiving.core.model.printlabel.LabelData;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintLabelRequest;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO Duplicate class . Need to fix it
public class MockContainer {

  public static Container getContainerInfo() {
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(1L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItems.add(containerItem);
    container.setDeliveryNumber(1234L);
    container.setTrackingId("a329870000000000000000001");
    container.setContainerStatus("");
    container.setContainerItems(containerItems);

    return container;
  }

  public static Map<String, String> getFacilityInfo() {
    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");
    return facility;
  }

  public static List<Distribution> getDistributionInfo() {
    Map<String, String> item = new HashMap<>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "500110");

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(10);
    distribution1.setOrderId("0aa3080c-5e62-4337-a373-9e874aa7a2a3");
    distribution1.setItem(item);

    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);

    return distributions;
  }

  public static ContainerRequest getContainerRequest() {
    ContainerRequest containerRequest = new ContainerRequest();
    containerRequest.setTrackingId("a329870000000000000000001");
    containerRequest.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2a01a1");
    containerRequest.setDeliveryNumber(11111111L);
    containerRequest.setLocation("101");
    containerRequest.setOrgUnitId("");
    containerRequest.setFacility(getFacilityInfo());
    containerRequest.setCtrType("PALLET");
    containerRequest.setCtrStatus("");
    containerRequest.setCtrWght(100.12F);
    containerRequest.setCtrWghtUom("LB");
    containerRequest.setCtrCube(10.12F);
    containerRequest.setCtrCubeUom("CF");
    containerRequest.setCtrShippable(true);
    containerRequest.setCtrReusable(false);
    containerRequest.setInventoryStatus("PICKED");

    List<ContainerItemRequest> containerItemList = new ArrayList<>();
    ContainerItemRequest lineItem1 = new ContainerItemRequest();
    lineItem1.setPurchaseReferenceNumber("8536273177");
    lineItem1.setPurchaseReferenceLineNumber(1);
    lineItem1.setInboundChannelMethod(PurchaseReferenceType.SSTKU.toString());
    lineItem1.setOutboundChannelMethod("STAPLESTOCK");
    lineItem1.setTotalPurchaseReferenceQty(50);
    lineItem1.setPurchaseCompanyId(1);
    lineItem1.setDeptNumber(5);
    lineItem1.setPoDeptNumber("005");
    lineItem1.setItemNumber(500110L);
    lineItem1.setGtin("00028000114604");
    lineItem1.setQuantity(10);
    lineItem1.setQuantityUom(ReceivingConstants.Uom.EACHES);
    lineItem1.setVnpkQty(1);
    lineItem1.setWhpkQty(1);
    lineItem1.setVendorPackCost(5.99);
    lineItem1.setWhpkSell(5.99);
    lineItem1.setBaseDivisionCode("WM");
    lineItem1.setFinancialReportingGroupCode("US");
    lineItem1.setRotateDate(null);
    lineItem1.setVnpkWgtQty(null);
    lineItem1.setVnpkWgtQtyUom(null);
    lineItem1.setVnpkCubeQty(null);
    lineItem1.setVnpkCubeQtyUom(null);
    lineItem1.setDescription("AK RAISINETS 1");
    lineItem1.setSecondaryDescription("ALASKA ONLY");
    lineItem1.setVendorNumber(123456789);
    lineItem1.setLotNumber("555");
    lineItem1.setActualTi(3);
    lineItem1.setActualHi(3);
    lineItem1.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    lineItem1.setDistributions(getDistributionInfo());
    containerItemList.add(lineItem1);

    ContainerItemRequest lineItem2 = new ContainerItemRequest();
    lineItem2.setPurchaseReferenceNumber("8536273177");
    lineItem2.setPurchaseReferenceLineNumber(2);
    lineItem2.setInboundChannelMethod(PurchaseReferenceType.SSTKU.name());
    lineItem2.setOutboundChannelMethod("STAPLESTOCK");
    lineItem2.setTotalPurchaseReferenceQty(100);
    lineItem2.setPurchaseCompanyId(1);
    lineItem2.setDeptNumber(5);
    lineItem2.setPoDeptNumber("005");
    lineItem2.setItemNumber(500111L);
    lineItem2.setGtin("00028000114605");
    lineItem2.setQuantity(2);
    lineItem2.setQuantityUom(ReceivingConstants.Uom.VNPK);
    lineItem2.setVnpkQty(12);
    lineItem2.setWhpkQty(4);
    lineItem2.setVendorPackCost(5.99);
    lineItem2.setWhpkSell(5.99);
    lineItem2.setBaseDivisionCode("WM");
    lineItem2.setFinancialReportingGroupCode("US");
    lineItem2.setRotateDate(null);
    lineItem2.setVnpkWgtQty(null);
    lineItem2.setVnpkWgtQtyUom(null);
    lineItem2.setVnpkCubeQty(null);
    lineItem2.setVnpkCubeQtyUom(null);
    lineItem2.setDescription("AK RAISINETS 2");
    lineItem2.setSecondaryDescription("ALASKA ONLY");
    lineItem2.setVendorNumber(987654321);
    lineItem2.setLotNumber("666");
    lineItem2.setActualTi(2);
    lineItem2.setActualHi(2);
    lineItem2.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    lineItem2.setDistributions(getDistributionInfo());
    containerItemList.add(lineItem2);

    containerRequest.setContents(containerItemList);

    return containerRequest;
  }

  public static Map<String, Object> getMockContainerLabelResponse() {
    Map<String, Object> printJob = new HashMap<>();
    List<PrintLabelRequest> printRequestList = new ArrayList<>();
    PrintLabelRequest printRequest1 = new PrintLabelRequest();
    printRequest1.setLabelIdentifier("92343434");
    printRequest1.setPrintJobId("job124");
    printRequest1.setFormatName("format123");
    printRequest1.setFormatName("format123");
    printRequest1.setTtlInHours(100);
    PrintLabelRequest printRequest2 = new PrintLabelRequest();
    printRequest2.setLabelIdentifier("98693434");
    printRequest2.setPrintJobId("job123");
    printRequest2.setFormatName("format123");
    printRequest2.setTtlInHours(100);
    List<LabelData> labelDataList1 = new ArrayList<>();
    List<LabelData> labelDataList2 = new ArrayList<>();
    LabelData labelData1 = new LabelData();
    labelData1.setKey("labelIdentifier");
    labelData1.setValue("92343434");
    LabelData labelData2 = new LabelData();
    labelData2.setKey("labelIdentifier");
    labelData2.setValue("98693434");
    labelDataList2.add(labelData1);
    labelDataList2.add(labelData2);
    printRequest1.setData(labelDataList1);
    printRequest2.setData(labelDataList2);
    printRequestList.add(printRequest1);
    printRequestList.add(printRequest2);
    printJob.put(
        PRINT_HEADERS_KEY, ContainerUtils.getPrintRequestHeaders(MockHttpHeaders.getHeaders()));
    printJob.put(PRINT_CLIENT_ID_KEY, ATLAS_RECEIVING);
    printJob.put(PRINT_REQUEST_KEY, printRequestList);

    return printJob;
  }

  public static ReprintLabelRequest getMockReprintLabelRequest() {
    ReprintLabelRequest reprintLabelRequest = new ReprintLabelRequest();
    List<String> trackingIds = new ArrayList<>();
    trackingIds.add("92343434");
    trackingIds.add("98693434");
    reprintLabelRequest.setTrackingIds(trackingIds);
    return reprintLabelRequest;
  }
}
