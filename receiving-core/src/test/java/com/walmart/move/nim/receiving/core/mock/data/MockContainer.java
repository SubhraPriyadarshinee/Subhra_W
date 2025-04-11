package com.walmart.move.nim.receiving.core.mock.data;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import com.walmart.move.nim.receiving.utils.constants.PurchaseReferenceType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;

public class MockContainer {

  public static Container getContainerInfo() {
    Container container = new Container();
    ContainerItem containerItem = new ContainerItem();
    List<ContainerItem> containerItems = new ArrayList<>();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("1008799412");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(557959102L);
    containerItem.setQuantity(90);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setActualTi(5);
    containerItem.setActualHi(3);
    containerItem.setVnpkWgtQty(2.0f);
    containerItem.setVnpkWgtUom("LB");
    containerItems.add(containerItem);
    container.setDeliveryNumber(15057089L);
    container.setCreateUser("sysadmin");
    container.setTrackingId("a329870000000000000000001");
    container.setContainerStatus("");
    container.setWeight(30.0f);
    container.setWeightUOM("LB");
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

  public static Map<String, String> getDestinationInfo() {
    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32987");
    return facility;
  }

  public static Container getSSTKContainer() {
    Container sstkContainer = new Container();
    sstkContainer.setDeliveryNumber(1234L);
    sstkContainer.setParentTrackingId(null);
    sstkContainer.setTrackingId("a329870000000000000000001");
    sstkContainer.setInstructionId(123L);
    sstkContainer.setContainerStatus("");
    sstkContainer.setCompleteTs(new Date());
    sstkContainer.setLastChangedUser("sysadmin");
    sstkContainer.setDestination(getDestinationInfo());
    sstkContainer.setFacility(getFacilityInfo());
    sstkContainer.setFacilityCountryCode("US");
    sstkContainer.setFacilityNum(32987);
    sstkContainer.setLocation("14B");
    sstkContainer.setCreateUser("sysadmin");
    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(100000L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setDistributions(getDistributionInfo());
    containerItem.setActualTi(6);
    containerItem.setActualHi(4);
    containerItem.setDescription("Dummy Desc1");
    containerItem.setWarehouseAreaCode("4");
    containerItems.add(containerItem);

    sstkContainer.setContainerItems(containerItems);
    return sstkContainer;
  }

  public static Container getPBYLContainer() {
    Container sstkContainer = new Container();
    sstkContainer.setDeliveryNumber(1234L);
    sstkContainer.setParentTrackingId(null);
    sstkContainer.setTrackingId("a329870000000000000000001");
    sstkContainer.setContainerStatus("");
    sstkContainer.setCompleteTs(new Date());
    sstkContainer.setFacility(getFacilityInfo());
    sstkContainer.setFacilityCountryCode("US");
    sstkContainer.setFacilityNum(32987);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(100000L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setDistributions(new ArrayList<>());
    containerItems.add(containerItem);

    sstkContainer.setContainerItems(containerItems);
    return sstkContainer;
  }

  public static Container getDAContainer() {
    Container parentContainer = new Container();
    parentContainer.setDeliveryNumber(1234L);
    parentContainer.setParentTrackingId(null);
    parentContainer.setTrackingId("a329870000000000000000001");
    parentContainer.setContainerStatus("");
    parentContainer.setCompleteTs(new Date());
    parentContainer.setLastChangedUser("sysadmin");
    parentContainer.setDestination(getDestinationInfo());
    parentContainer.setFacility(getFacilityInfo());
    parentContainer.setFacilityCountryCode("US");
    parentContainer.setFacilityNum(32987);
    parentContainer.setLocation("14B");
    parentContainer.setCreateUser("sysadmin");

    Set<Container> childContainers = new HashSet<>();

    Container childContainer1 = new Container();
    childContainer1.setDeliveryNumber(1234L);
    childContainer1.setTrackingId("a329870000000000000000002");
    childContainer1.setContainerStatus("");
    childContainer1.setCompleteTs(new Date());
    childContainer1.setDestination(getDestinationInfo());
    childContainer1.setFacility(getFacilityInfo());
    childContainer1.setParentTrackingId("a329870000000000000000001");

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem1 = new ContainerItem();
    containerItem1.setTrackingId("a329870000000000000000002");
    containerItem1.setPurchaseReferenceNumber("34734743");
    containerItem1.setPurchaseReferenceLineNumber(1);
    containerItem1.setInboundChannelMethod("CROSSU");
    containerItem1.setVnpkQty(24);
    containerItem1.setWhpkQty(6);
    containerItem1.setItemNumber(100000L);
    containerItem1.setQuantity(24);
    containerItem1.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem1.setDistributions(getDistributionInfo());
    containerItem1.setGtin("7437838348");
    containerItem1.setDescription("Dummy desc item1");
    containerItems.add(containerItem1);
    childContainer1.setContainerItems(containerItems);

    Container childContainer2 = new Container();
    childContainer2.setDeliveryNumber(1234L);
    childContainer2.setTrackingId("a329870000000000000000003");
    childContainer2.setContainerStatus("");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setDestination(getDestinationInfo());
    childContainer2.setFacility(getFacilityInfo());
    childContainer2.setParentTrackingId("a329870000000000000000001");

    containerItems.clear();
    ContainerItem containerItem2 = new ContainerItem();
    containerItem2.setTrackingId("a329870000000000000000003");
    containerItem2.setPurchaseReferenceNumber("34734743");
    containerItem2.setPurchaseReferenceLineNumber(1);
    containerItem2.setInboundChannelMethod("CROSSU");
    containerItem2.setVnpkQty(24);
    containerItem2.setWhpkQty(6);
    containerItem2.setItemNumber(100000L);
    containerItem2.setQuantity(24);
    containerItem2.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem2.setDistributions(getDistributionInfo());
    containerItem2.setGtin("7437838349");
    containerItem2.setDescription("Dummy desc item2");
    containerItems.add(containerItem2);
    childContainer2.setContainerItems(containerItems);

    childContainers.add(childContainer1);
    childContainers.add(childContainer2);

    parentContainer.setChildContainers(childContainers);
    return parentContainer;
  }

  public static Container getChildContainer() {
    Container childContainer = new Container();
    childContainer.setDeliveryNumber(1234L);
    childContainer.setParentTrackingId("a329870000000000000000000");
    childContainer.setTrackingId("a329870000000000000000001");
    childContainer.setContainerStatus("");
    childContainer.setCompleteTs(new Date());
    childContainer.setFacility(getFacilityInfo());
    childContainer.setFacilityCountryCode("US");
    childContainer.setFacilityNum(32987);

    Map<String, Object> containerMiscInfo = new HashMap<>();
    containerMiscInfo.put(ReceivingConstants.PRO_DATE, new Date());
    containerMiscInfo.put(ReceivingConstants.ORIGIN_TYPE, "DC");
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_NUMBER, 32987);
    containerMiscInfo.put(ReceivingConstants.ORIGIN_FACILITY_COUNTRY_CODE, "US");
    containerMiscInfo.put(ReceivingConstants.PURCHASE_REF_LEGACY_TYPE, "42");
    childContainer.setContainerMiscInfo(containerMiscInfo);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(100000L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setDistributions(new ArrayList<>());
    containerItems.add(containerItem);

    childContainer.setContainerItems(containerItems);
    return childContainer;
  }

  public static Instruction getInstruction() {
    Instruction instruction = new Instruction();
    instruction.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    instruction.setDeliveryNumber(Long.valueOf("18278904"));
    instruction.setGtin("10016017103990");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build Container");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setActivityName("SSTK");
    instruction.setPurchaseReferenceNumber("199557349");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(16);
    instruction.setProjectedReceiveQtyUOM("ZA");
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM("ZA");
    instruction.setFirstExpiryFirstOut(Boolean.TRUE);
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"18278904\",\n"
            + "        \"purchaseReferenceNumber\": \"199557349\",\n"
            + "        \"poDCNumber\": \"32612\",\n"
            + "        \"baseDivCode\": \"WM\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"SSTKU\",\n"
            + "                \"purchaseReferenceNumber\": \"199557349\",\n"
            + "                \"purchaseReferenceLineNumber\": 1,\n"
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
            + "                \"event\": \"POS REPLEN\",\n"
            + "                \"palletTi\": 30,\n"
            + "                \"palletHi\": 4,\n"
            + "                \"department\": \"7\",\n"
            + "                \"isHazmat\": false,\n"
            + "                \"isConveyable\": true,\n"
            + "                \"overageQtyLimit\": 11,\n"
            + "                \"overageThresholdQty\": 11,\n"
            + "                \"color\": \"76118\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"deptNumber\": \"7\",\n"
            + "        \"vendorNumber\": \"299404\",\n"
            + "        \"weight\": 15816.0,\n"
            + "        \"freightTermCode\": \"COLL\",\n"
            + "        \"financialGroupCode\": \"US\"\n"
            + "    }");

    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("sequenceNbr", 192681318);
    move.put("containerTag", "B67387000020002031");
    move.put("correlationID", "a1-b2-c3-d4-e5");
    move.put("toLocation", "induct_slot1");
    instruction.setMove(move);

    List<String> availableLabels = new ArrayList<String>();
    availableLabels.add("B67387000020002031");
    Labels labels = new Labels();
    labels.setAvailableLabels(availableLabels);
    labels.setUsedLabels(new ArrayList<String>());
    instruction.setLabels(labels);

    Map<String, Object> ctrLabel = new HashMap<String, Object>();
    ctrLabel.put("clientId", "OF");

    List<Map<String, Object>> printRequestArray = new ArrayList<Map<String, Object>>();
    Map<String, Object> printRequest = new HashMap<String, Object>();
    printRequest.put("labelIdentifier", "B67387000020002031");
    printRequest.put("formatName", "pallet_lpn_format");
    printRequest.put("ttlInHours", 72);

    List<Map<String, Object>> dataArray = new ArrayList<Map<String, Object>>();
    Map<String, Object> data = new HashMap<String, Object>();
    data.put("key", "ITEM");
    data.put("value", "556565795");
    data.put("key", "DESTINATION");
    data.put("value", "induct_slot1");
    data.put("key", "UPCBAR");
    data.put("value", "10016017103990");
    data.put("key", "LPN");
    data.put("value", "B67387000020002031");
    data.put("key", "TYPE");
    data.put("value", "SSTK");
    dataArray.add(data);
    printRequest.put("data", dataArray);
    printRequestArray.add(printRequest);
    ctrLabel.put("printRequests", printRequestArray);

    ContainerDetails container = new ContainerDetails();
    container.setCtrLabel(ctrLabel);
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setCtrType("PALLET");
    container.setInventoryStatus("AVAILABLE");
    container.setTrackingId("B67387000020002031");
    instruction.setContainer(container);

    String deliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"199557349\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"12344\",\n"
            + "    \"deptNumber\": \"14\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"33\",\n"
            + "    \"poDCNumber\": \"32988\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"itemUPC\": \"00076501380104\",\n"
            + "            \"caseUPC\": \"00076501380104\",\n"
            + "            \"purchaseReferenceNumber\": \"199557349\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 8.22,\n"
            + "            \"vendorPackCost\": 6.6,\n"
            + "            \"vnpkQty\": 2,\n"
            + "            \"whpkQty\": 2,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"expectedQty\": 400,\n"
            + "            \"overageQtyLimit\": 11,\n"
            + "            \"itemNbr\": 556565795,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 6,\n"
            + "            \"palletHi\": 2,\n"
            + "            \"vnpkWgtQty\": 14.84,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.432,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"\",\n"
            + "            \"size\": \"\",\n"
            + "            \"itemDescription1\": \"70QT XTREME BLUE\",\n"
            + "            \"itemDescription2\": \"WH TO ASM\",\n"
            + "            \"isConveyable\": true\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 106,\n"
            + "    \"weight\": 12.0,\n"
            + "    \"weightUOM\": \"LB\",\n"
            + "    \"cubeQty\": 23.5,\n"
            + "    \"cubeUOM\": \"CF\",\n"
            + "    \"freightTermCode\": \"COLL\"\n"
            + "}";
    instruction.setDeliveryDocument(deliveryDocument);

    return instruction;
  }

  public static UpdateInstructionRequest getUpdateInstructionRequest() {
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();
    updateInstructionRequest.setDeliveryNumber(Long.valueOf("18278904"));
    updateInstructionRequest.setDoorNumber("100");
    updateInstructionRequest.setContainerType("Chep Pallet");

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    updateInstructionRequest.setFacility(facility);

    List<DocumentLine> documentLines = new ArrayList<DocumentLine>();
    DocumentLine documentLine = new DocumentLine();
    documentLine.setPurchaseReferenceNumber("199557349");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setTotalPurchaseReferenceQty(100);
    documentLine.setPurchaseCompanyId(1);
    documentLine.setPoDCNumber("32612");
    documentLine.setPoDeptNumber("92");
    documentLine.setDeptNumber(1);
    documentLine.setItemNumber(Long.parseLong("556565795"));
    documentLine.setGtin("00049807100011");
    documentLine.setVnpkQty(4);
    documentLine.setWhpkQty(4);
    documentLine.setQuantity(20);
    documentLine.setQuantityUOM("ZA");
    documentLine.setVendorPackCost(25.0);
    documentLine.setWhpkSell(25.0);
    documentLine.setBaseDivisionCode("WM");
    documentLine.setFinancialReportingGroupCode("US");
    documentLine.setRotateDate(null);
    documentLine.setWarehouseMinLifeRemainingToReceive(10);
    documentLine.setProfiledWarehouseArea("OPM");
    documentLine.setPromoBuyInd("Y");
    documentLine.setVendorNumber("1234");
    documentLine.setPalletTi(5);
    documentLine.setPalletHi(4);
    documentLine.setMaxAllowedStorageDays(120);
    documentLine.setMaxAllowedStorageDate(new Date());
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static Container getContainer() {
    Container container = new Container();
    container.setTrackingId("B67387000020002031");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("Chep Pallet");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setIsConveyable(Boolean.TRUE);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    container.setCompleteTs(new Date());

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("B67387000020002031");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setActualTi(6);
    containerItem.setActualHi(2);
    containerItem.setVnpkWgtQty(14.84F);
    containerItem.setVnpkWgtUom("LB");
    containerItem.setVnpkcbqty(0.432F);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setDescription("70QT XTREME BLUE");
    containerItem.setSecondaryDescription("WH TO ASM");
    containerItem.setRotateDate(new Date());
    containerItem.setPoTypeCode(20);
    containerItem.setVendorNbrDeptSeq(1234);
    containerItem.setLotNumber("LOT555");
    containerItem.setHybridStorageFlag("MFC");
    containerItems.add(containerItem);
    container.setContainerItems(containerItems);

    container.setPublishTs(new Date());

    return container;
  }

  public static ContainerDTO getContainerWithChildContainers() {
    ContainerDTO container = new ContainerDTO();
    container.setTrackingId("B67387000020002031");
    container.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    container.setLocation("100");
    container.setDeliveryNumber(Long.parseLong("18278904"));
    container.setParentTrackingId(null);
    container.setContainerType("Chep Pallet");
    container.setCtrShippable(Boolean.FALSE);
    container.setCtrReusable(Boolean.FALSE);
    container.setInventoryStatus("AVAILABLE");
    container.setIsConveyable(Boolean.TRUE);
    container.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    container.setCompleteTs(new Date());

    Map<String, String> facility = new HashMap<>();
    facility.put("countryCode", "US");
    facility.put("buNumber", "32612");
    container.setFacility(facility);

    Set<Container> childContainers = new HashSet<>();
    Container childContainer = new Container();
    childContainer.setTrackingId("e3267902323232323");
    childContainer.setMessageId("a0e24c65-991b-46ac-ae65-dc7bc52edfe6");
    childContainer.setLocation("100");
    childContainer.setDeliveryNumber(Long.parseLong("18278904"));
    childContainer.setParentTrackingId(null);
    childContainer.setContainerType("Chep Pallet");
    childContainer.setCtrShippable(Boolean.FALSE);
    childContainer.setCtrReusable(Boolean.FALSE);
    childContainer.setInventoryStatus("AVAILABLE");
    childContainer.setIsConveyable(Boolean.TRUE);
    childContainer.setContainerStatus(ReceivingConstants.STATUS_PUTAWAY_COMPLETE);
    childContainer.setCompleteTs(new Date());

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("e3267902323232323");
    containerItem.setPurchaseReferenceNumber("199557349");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setDeptNumber(1);
    containerItem.setPoDeptNumber("92");
    containerItem.setItemNumber(Long.parseLong("556565795"));
    containerItem.setGtin("00049807100011");
    containerItem.setQuantity(80);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setVnpkQty(4);
    containerItem.setWhpkQty(4);
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setVendorNumber(1234);
    containerItem.setPackagedAsUom(ReceivingConstants.Uom.VNPK);
    containerItem.setPromoBuyInd("Y");
    containerItem.setActualTi(6);
    containerItem.setActualHi(2);
    containerItem.setVnpkWgtQty(14.84F);
    containerItem.setVnpkWgtUom("LB");
    containerItem.setVnpkcbqty(0.432F);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setDescription("70QT XTREME BLUE");
    containerItem.setSecondaryDescription("WH TO ASM");
    containerItem.setRotateDate(new Date());
    containerItem.setPoTypeCode(20);
    containerItem.setVendorNbrDeptSeq(1234);
    containerItem.setLotNumber("LOT555");
    containerItems.add(containerItem);
    childContainer.setContainerItems(containerItems);

    childContainers.add(childContainer);
    container.setPublishTs(new Date());
    container.setChildContainers(childContainers);

    return container;
  }

  public static List<ContainerItem> getMockContainerItem() {
    List<ContainerItem> containerItemList = new ArrayList<ContainerItem>();
    ContainerItem containerItem = new ContainerItem();
    containerItem.setPurchaseReferenceNumber("7");
    containerItem.setPurchaseReferenceLineNumber(5);
    containerItem.setInboundChannelMethod("CROSSU");
    containerItem.setOutboundChannelMethod("CROSSU");
    containerItem.setTotalPurchaseReferenceQty(100);
    containerItem.setPurchaseCompanyId(1);
    containerItem.setPoDeptNumber("0092");
    containerItem.setDeptNumber(1);
    containerItem.setItemNumber(10844432L);
    containerItem.setVendorGS128("");
    containerItem.setGtin("00049807100025");
    containerItem.setVnpkQty(1);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setActualHi(9);
    containerItem.setActualTi(9);
    containerItem.setQuantityUOM("EA"); // by default EA
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(null);
    containerItem.setTrackingId("a329870000000000000000001");
    containerItemList.add(containerItem);
    return containerItemList;
  }

  public static Container getValidContainer() {
    Container sstkContainer = new Container();
    sstkContainer.setDeliveryNumber(1234L);
    sstkContainer.setParentTrackingId(null);
    sstkContainer.setTrackingId("a329870000000000000000001");
    sstkContainer.setLocation("105");
    sstkContainer.setContainerStatus("");
    sstkContainer.setCompleteTs(new Date());
    sstkContainer.setLastChangedUser("sysadmin");
    sstkContainer.setDestination(getDestinationInfo());
    sstkContainer.setFacility(getFacilityInfo());
    sstkContainer.setFacilityCountryCode("US");
    sstkContainer.setFacilityNum(32987);

    List<ContainerItem> containerItems = new ArrayList<>();
    ContainerItem containerItem = new ContainerItem();

    containerItem.setTrackingId("a329870000000000000000001");
    containerItem.setPurchaseReferenceNumber("34734743");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setInboundChannelMethod("SSTKU");
    containerItem.setVnpkQty(24);
    containerItem.setWhpkQty(6);
    containerItem.setItemNumber(100000L);
    containerItem.setQuantity(24);
    containerItem.setQuantityUOM(ReceivingConstants.Uom.EACHES);
    containerItem.setDistributions(getDistributionInfo());
    containerItem.setActualTi(6);
    containerItem.setActualHi(4);
    containerItems.add(containerItem);
    containerItem.setPoTypeCode(28);
    containerItem.setPoDCNumber("32612");
    sstkContainer.setCtrShippable(true);
    sstkContainer.setCtrReusable(true);
    sstkContainer.setIsConveyable(true);
    containerItem.setVnpkWgtQty(3.3f);
    containerItem.setVnpkWgtUom("LB");
    containerItem.setPurchaseCompanyId(0);
    containerItem.setWhpkSell(1d);
    containerItem.setVnpkcbqty(3.3f);
    containerItem.setVnpkcbuomcd("CF");
    containerItem.setDeptNumber(80);
    containerItem.setVendorNumber(10);
    containerItem.setOrderableQuantity(5);
    Map<String, String> containerItemMiscInfo = new HashMap<>();
    containerItemMiscInfo.put(FROM_SUBCENTER, "5");
    containerItemMiscInfo.put(PO_TYPE, "28");
    containerItemMiscInfo.put(IS_RECEIVE_FROM_OSS, "true");
    containerItemMiscInfo.put("isAtlasConvertedItem", "true");
    containerItem.setContainerItemMiscInfo(containerItemMiscInfo);
    containerItem.setBaseDivisionCode("WM");
    containerItem.setFinancialReportingGroupCode("US");
    sstkContainer.setContainerItems(containerItems);
    return sstkContainer;
  }
}
