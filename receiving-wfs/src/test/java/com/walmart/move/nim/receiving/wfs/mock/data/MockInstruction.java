package com.walmart.move.nim.receiving.wfs.mock.data;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpHeaders;

public class MockInstruction {

  public static Instruction getDockTagInstruction() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", "EFLCP08");
    moveTreeMap.put("correlationID", "1a2bc3d4");
    moveTreeMap.put("containerTag", "c32987000000000000000001");
    moveTreeMap.put("lastChangedOn", new Date());
    moveTreeMap.put("lastChangedBy", "sysadmin");

    Instruction instruction = new Instruction();
    instruction.setId(329870001L);
    instruction.setInstructionCode(ReceivingConstants.DOCK_TAG);
    instruction.setInstructionMsg("Create dock tag container instruction");
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(getDockTagContainerDetails());
    instruction.setMessageId("0eb0a8b6-36e1-4792-b4a1-9da242d8199e");
    instruction.setDockTagId("c32987000000000000000001");
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(261220189L);
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(0);
    instruction.setCreateUserId("sysadmin");
    instruction.setMove(moveTreeMap);
    return instruction;
  }

  public static ContainerDetails getDockTagContainerDetails() {
    ContainerDetails containerDetails;
    Map<String, Object> labelData = new HashMap<>();
    labelData.put("key", "DOOR");
    labelData.put("value", "5555");
    labelData.put("key", "DATE");
    labelData.put("value", new Date());
    labelData.put("key", "LPN");
    labelData.put("value", "c32987000000000000000001");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "sysadmin");
    labelData.put("key", "DELIVERYNBR");
    labelData.put("value", 261220189L);

    List<Map<String, Object>> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "c32987000000000000000001");
    printRequest.put("formatName", "dock_tag_atlas");
    printRequest.put("ttlInHours", 72);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", ReceivingConstants.RECEIVING_PROVIDER_ID);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.set(ReceivingConstants.WMT_PRODUCT_NAME, ReceivingConstants.APP_NAME_VALUE);
    containerLabel.put("headers", httpHeaders);
    containerLabel.put("printRequests", printRequestList);

    containerDetails = new ContainerDetails();
    containerDetails.setTrackingId("c32987000000000000000001");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("WORK_IN_PROGRESS");
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrLabel(containerLabel);
    return containerDetails;
  }

  public static PrintJob getPrintJob() {
    PrintJob printJob = new PrintJob();

    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add("a328990000000000000106509");

    printJob.setDeliveryNumber(21119003L);
    printJob.setCreateUserId("sysadmin");
    printJob.setInstructionId(1L);
    printJob.setLabelIdentifier(printJobLpnSet);

    return printJob;
  }

  public static Container getDockTagContainer() {
    Container container = new Container();
    container.setCreateUser("sysadmin");
    container.setLastChangedUser("sysadmin");
    container.setMessageId("0eb0a8b6-36e1-4792-b4a1-9da242d8199e");
    container.setInventoryStatus("WORK_IN_PROGRESS");
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrShippable(Boolean.FALSE);
    container.setTrackingId("c32987000000000000000001");
    container.setInstructionId(329870001L);
    container.setLocation("5555");
    container.setDeliveryNumber(261220189L);
    container.setContainerType("PALLET");
    container.setContainerException(ContainerException.DOCK_TAG.getText());
    container.setOnConveyor(Boolean.FALSE);

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");

    container.setFacility(facility);
    return container;
  }

  public static Instruction getCompletedDockTagInstruction() {
    Instruction instruction = getDockTagInstruction();
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysadmin");
    return instruction;
  }

  public static Instruction getInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("404"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("300001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("DA-SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setReceivedQuantity(10);
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryDocument(
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
            + "                \"itemDescription2\": \"NEW F20 WK 28\",\n"
            + "                 \"additionalInfo\" : {"
            + "                                      \"warehouseGroupCode\": \"P\","
            + "                                      \"isNewItem\": false, "
            + "                                      \"warehouseAreaCode\": \"8\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\""
            + "                                     }"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }");

    return instruction;
  }

  public static ContainerDetails getContainerData() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "ITEM");
    labelData.put("value", "573170821");
    labelData.put("key", "UPCBAR");
    labelData.put("value", "00028000114602");
    labelData.put("key", "LPN");
    labelData.put("value", "a32612000000000001");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "witronTest");
    labelData.put("key", "TYPE");
    labelData.put("value", "SSTK");
    labelData.put("key", "DESC1");
    labelData.put("value", "TR ED 3PC FRY/GRL RD");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a32612000000000001");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<Map<String, Object>>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", "OF");
    containerLabel.put("headers", GdcHttpHeaders.getHeaders());
    containerLabel.put("printRequests", printRequestList);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setProjectedWeightUom(ReceivingConstants.Uom.VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("a32612000000000001");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrShippable(Boolean.TRUE);
    containerDetails.setCtrReusable(Boolean.TRUE);
    containerDetails.setQuantity(2);
    containerDetails.setCtrLabel(containerLabel);

    return containerDetails;
  }

  public static Instruction getCancelledInstruction() {
    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setQuantity(1);

    // Mock cancelled instruction
    Instruction cancelledInstruction = new Instruction();
    cancelledInstruction.setId(Long.valueOf("11120"));
    cancelledInstruction.setContainer(containerDetails);
    cancelledInstruction.setChildContainers(null);
    cancelledInstruction.setCreateTs(new Date());
    cancelledInstruction.setCreateUserId("sysadmin");
    cancelledInstruction.setLastChangeTs(new Date());
    cancelledInstruction.setLastChangeUserId("sysadmin");
    cancelledInstruction.setCompleteTs(new Date());
    cancelledInstruction.setCompleteUserId("sysadmin");
    cancelledInstruction.setDeliveryNumber(Long.valueOf("21119003"));
    cancelledInstruction.setGtin("00000943037194");
    cancelledInstruction.setInstructionCode("Build Container");
    cancelledInstruction.setInstructionMsg("Build the Container");
    cancelledInstruction.setItemDescription("HEM VALUE PACK (4)");
    cancelledInstruction.setMessageId("12e1df00-ebf6-11e8-9c25-dd4bfc2a06fa");
    cancelledInstruction.setMove(null);
    cancelledInstruction.setPoDcNumber("32899");
    cancelledInstruction.setPrintChildContainerLabels(false);
    cancelledInstruction.setPurchaseReferenceNumber("9763140004");
    cancelledInstruction.setPurchaseReferenceLineNumber(Integer.valueOf(1));
    cancelledInstruction.setProjectedReceiveQty(1);
    cancelledInstruction.setProviderId("DA");
    cancelledInstruction.setReceivedQuantity(0);
    cancelledInstruction.setActivityName("SSTKU");
    cancelledInstruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    cancelledInstruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    cancelledInstruction.setDeliveryDocument(
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
    return cancelledInstruction;
  }

  public static Map<String, String> getDestination() {
    Map<String, String> mapCtrDestination = new HashMap<>();
    mapCtrDestination.put("countryCode", "US");
    mapCtrDestination.put("buNumber", "6012");
    return mapCtrDestination;
  }

  public static List<Distribution> getDistributions() {
    Map<String, String> item = new HashMap<>();
    item.put("financialReportingGroup", "US");
    item.put("baseDivisionCode", "WM");
    item.put("itemNbr", "1084445");

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(5);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    distribution1.setItem(item);

    Distribution distribution2 = new Distribution();
    distribution2.setAllocQty(5);
    distribution2.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c4");
    distribution2.setItem(item);

    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);
    distributions.add(distribution2);

    return distributions;
  }

  public static ContainerDetails getContainerDetails() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "ITEM");
    labelData.put("value", "100001");
    labelData.put("key", "DESTINATION");
    labelData.put("value", "06021 US");
    labelData.put("key", "UPCBAR");
    labelData.put("value", "00075486091132");
    labelData.put("key", "LPN");
    labelData.put("value", "a328990000000000000106509");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", "sysadmin");
    labelData.put("key", "TYPE");
    labelData.put("value", "DA");
    labelData.put("key", "DESC1");
    labelData.put("value", "TR ED 3PC FRY/GRL RD");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<Map<String, Object>>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", "OF");
    containerLabel.put("headers", MockHttpHeaders.getHeaders());
    containerLabel.put("printRequests", printRequestList);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOutboundChannelMethod("CROSSU");
    containerDetails.setOrgUnitId(1);
    containerDetails.setProjectedWeightUom(ReceivingConstants.Uom.VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("PICKED");
    containerDetails.setCtrShippable(Boolean.TRUE);
    containerDetails.setCtrReusable(Boolean.TRUE);
    containerDetails.setQuantity(2);
    containerDetails.setCtrDestination(getDestination());
    containerDetails.setDistributions(getDistributions());
    containerDetails.setCtrLabel(containerLabel);

    return containerDetails;
  }
}
