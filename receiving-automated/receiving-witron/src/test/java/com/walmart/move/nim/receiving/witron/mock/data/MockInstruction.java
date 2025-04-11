package com.walmart.move.nim.receiving.witron.mock.data;

import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.client.gls.model.GLSReceiveResponse;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.ContainerDetails;
import com.walmart.move.nim.receiving.core.model.Content;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.core.model.DocumentLine;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerResponse;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.ItemData;
import com.walmart.move.nim.receiving.core.model.OperationalInfo;
import com.walmart.move.nim.receiving.core.model.UpdateInstructionRequest;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.constants.GdcConstants;
import com.walmart.move.nim.receiving.witron.model.GdcInstructionType;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.*;

public class MockInstruction {

  private static String userId =
      GdcHttpHeaders.getHeaders().getFirst(ReceivingConstants.USER_ID_HEADER_KEY);

  public static Map<String, String> getDestination() {
    Map<String, String> mapCtrDestination = new HashMap<>();
    mapCtrDestination.put("countryCode", "US");
    mapCtrDestination.put("buNumber", "6012");
    return mapCtrDestination;
  }

  public static LinkedTreeMap<String, Object> getMoveData() {
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");
    move.put("fromLocation", "107");

    return move;
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
    labelData.put("value", userId);
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

  public static ContainerDetails getContainerDetailsOldPrint() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "ITEM");
    labelData.put("value", "100001");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatID", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);
    printRequest.put("labelData", labelDataList);
    printRequest.put("clientId", "OF");

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
    containerDetails.setCtrLabel(printRequest);

    return containerDetails;
  }

  public static Instruction getOpenInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setActivityName("DA");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setReceivedQuantity(2);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Instruction getOldPrintOpenInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerDetailsOldPrint());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setActivityName("DA");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setReceivedQuantity(2);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Instruction getCreatedInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setActivityName("DA");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setReceivedQuantity(2);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Instruction getPendingInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037204");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (5)");
    instruction.setActivityName("DA");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140005");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
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
            + "                \"additionalInfo\" : {"
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

  public static Instruction getCompleteInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setActivityName("DA");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setReceivedQuantity(2);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Instruction getInstructionForMessageId() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("0087876804154");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("HEM VALUE PACK (5)");
    instruction.setActivityName("DA");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2b96f8");
    instruction.setMove(getMoveData());
    instruction.setPoDcNumber("32899");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("0294235326");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Container getContainer() {
    Container container = new Container();
    container.setTrackingId("a328990000000000000106509");
    container.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    container.setInventoryStatus("PICKED");
    container.setLocation("13");
    container.setDeliveryNumber(21119003L);
    container.setFacility(getDestination());
    container.setDestination(getDestination());
    container.setContainerType("PALLET");
    container.setContainerStatus("PICKED");
    container.setWeight(20F);
    container.setWeightUOM(ReceivingConstants.Uom.VNPK);
    container.setCube(1F);
    container.setCubeUOM(null);
    container.setCtrShippable(Boolean.TRUE);
    container.setCtrReusable(Boolean.TRUE);
    container.setCompleteTs(new Date());
    container.setOrgUnitId("1");
    container.setPublishTs(new Date());
    container.setCreateTs(new Date());
    container.setCreateUser(userId);
    container.setLastChangedTs(new Date());
    container.setLastChangedUser(userId);
    container.setContainerItems(null);

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
    containerItem.setQuantityUOM("EA");
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(null);
    containerItem.setDistributions(getDistributions());

    Container childContainer1 = new Container();
    childContainer1.setTrackingId("a32L8990000000000000106520");
    childContainer1.setParentTrackingId("a328990000000000000106509");
    childContainer1.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    childContainer1.setCompleteTs(new Date());
    childContainer1.setLocation("123");
    childContainer1.setDeliveryNumber(Long.valueOf(12342));
    childContainer1.setFacility(getDestination());
    childContainer1.setDestination(getDestination());
    childContainer1.setContainerType("Vendor Pack");
    childContainer1.setContainerStatus("");
    childContainer1.setWeight(5F);
    childContainer1.setWeightUOM("EA");
    childContainer1.setCube(2F);
    childContainer1.setCubeUOM("EA");
    childContainer1.setCtrShippable(true);
    childContainer1.setCtrShippable(false);
    childContainer1.setInventoryStatus("Picked");
    childContainer1.setCompleteTs(new Date());
    childContainer1.setPublishTs(new Date());
    childContainer1.setCreateTs(new Date());
    childContainer1.setCreateUser(userId);
    childContainer1.setLastChangedTs(new Date());
    childContainer1.setLastChangedUser(userId);
    childContainer1.setContainerItems(Arrays.asList(containerItem));

    Container childContainer2 = new Container();
    childContainer2.setTrackingId("a32L8990000000000000106521");
    childContainer2.setParentTrackingId("a328990000000000000106509");
    childContainer2.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setLocation("123L");
    childContainer2.setDeliveryNumber(Long.valueOf(12342));
    childContainer2.setFacility(getDestination());
    childContainer2.setDestination(getDestination());
    childContainer2.setContainerType("Vendor Pack");
    childContainer2.setContainerStatus("");
    childContainer2.setWeight(5F);
    childContainer2.setWeightUOM("EA");
    childContainer2.setCube(2F);
    childContainer2.setCubeUOM("EA");
    childContainer2.setCtrShippable(true);
    childContainer2.setCtrShippable(false);
    childContainer2.setInventoryStatus("Picked");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setPublishTs(new Date());
    childContainer2.setCreateTs(new Date());
    childContainer2.setCreateUser(userId);
    childContainer2.setLastChangedTs(new Date());
    childContainer2.setLastChangedUser(userId);
    childContainer2.setContainerItems(Arrays.asList(containerItem));

    container.setChildContainers(
        Stream.of(childContainer1, childContainer2).collect(Collectors.toCollection(HashSet::new)));

    return container;
  }

  public static List<DeliveryDocument> getDeliveryDocuments() {

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06938");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("4763030227");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039630");
    deliveryDocumentLine.setItemUpc("00016017039630");
    deliveryDocumentLine.setCaseUpc("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("SSTKU");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setVendorPack(6);
    deliveryDocumentLine.setWarehousePack(6);
    deliveryDocumentLine.setOrderableQuantity(6);
    deliveryDocumentLine.setWarehousePackQuantity(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setPromoBuyInd("N");

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    itemData.setWeightFormatTypeCode(ReceivingConstants.FIXED_WEIGHT_FORMAT_TYPE_CODE);
    itemData.setProfiledWarehouseArea("CPS");
    deliveryDocumentLine.setAdditionalInfo(itemData);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);

    return deliveryDocuments;
  }

  public static PrintJob getPrintJob() {
    PrintJob printJob = new PrintJob();

    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add("a328990000000000000106509");

    printJob = new PrintJob();
    printJob.setDeliveryNumber(Long.valueOf("21119003"));
    printJob.setCreateUserId(userId);
    printJob.setInstructionId(Long.valueOf("1"));
    printJob.setLabelIdentifier(printJobLpnSet);

    return printJob;
  }

  public static InstructionRequest getInstructionRequest() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("5e1df00-ebf6-11e8-9c25-dd4bfc2a06f5");
    instructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionRequest.setDeliveryNumber("21119003");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setUpcNumber("00000943037204");
    instructionRequest.setResolutionQty(10);
    instructionRequest.setDeliveryDocuments(getDeliveryDocuments());

    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithOpenState() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231313");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber(null);

    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithWorkingState() {
    InstructionRequest instructionReqWithWorkingState = new InstructionRequest();
    instructionReqWithWorkingState.setMessageId("1c4c5d70-0f7b-11e9-9114-1fe239bd18d1");
    instructionReqWithWorkingState.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    instructionReqWithWorkingState.setDeliveryNumber("98765819");
    instructionReqWithWorkingState.setDoorNumber("100");
    instructionReqWithWorkingState.setAsnBarcode(null);
    instructionReqWithWorkingState.setProblemTagId(null);
    instructionReqWithWorkingState.setUpcNumber("0087876804154");

    return instructionReqWithWorkingState;
  }

  public static InstructionRequest getProblemInstructionRequest() {
    InstructionRequest problemInstructionRequest = new InstructionRequest();
    problemInstructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    problemInstructionRequest.setDeliveryStatus(DeliveryStatus.WORKING.toString());
    problemInstructionRequest.setDeliveryNumber("21119003");
    problemInstructionRequest.setDoorNumber("123");
    problemInstructionRequest.setAsnBarcode(null);
    problemInstructionRequest.setProblemTagId("1");
    problemInstructionRequest.setUpcNumber("00000943037204");
    problemInstructionRequest.setDeliveryDocuments(getDeliveryDocuments());

    return problemInstructionRequest;
  }

  public static FdeCreateContainerResponse getFdeCreateContainerResponse() {
    FdeCreateContainerResponse fdeCreateContainerResponse = new FdeCreateContainerResponse();
    fdeCreateContainerResponse.setChildContainers(null);
    fdeCreateContainerResponse.setContainer(null);
    fdeCreateContainerResponse.setInstructionMsg("Build the Container");
    fdeCreateContainerResponse.setInstructionCode("Build Container");
    fdeCreateContainerResponse.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    fdeCreateContainerResponse.setPrintChildContainerLabels(false);
    fdeCreateContainerResponse.setProjectedQty(10);
    fdeCreateContainerResponse.setProjectedQtyUom("EA");
    fdeCreateContainerResponse.setProviderId("DA");
    fdeCreateContainerResponse.setActivityName("DA");
    fdeCreateContainerResponse.setMove(getMoveData());

    return fdeCreateContainerResponse;
  }

  public static FdeCreateContainerResponse getAsnFdeCreateContainerResponse() {
    FdeCreateContainerResponse asnFdeCreateContainerResponse = new FdeCreateContainerResponse();
    asnFdeCreateContainerResponse.setInstructionCode("Label");
    asnFdeCreateContainerResponse.setInstructionMsg("Label the Container");
    asnFdeCreateContainerResponse.setMessageId("62668e40-2df0-11e9-a3a4-df7d6879ad88");
    asnFdeCreateContainerResponse.setProviderId("S2S");
    LinkedTreeMap<String, Object> asnMove = new LinkedTreeMap<String, Object>();
    asnMove.put("lastChangedBy", "OF-SYS");
    asnMove.put("lastChangedOn", new Date());
    asnMove.put("sequenceNbr", 1618623190);
    asnMove.put("containerTag", "00100077672010660414");
    asnMove.put("toLocation", "STAGE");
    asnFdeCreateContainerResponse.setMove(asnMove);
    ContainerDetails asnContainer = new ContainerDetails();
    List<ContainerDetails> asnChildContainers = new ArrayList<>();
    ContainerDetails asnChildContainer = new ContainerDetails();
    asnChildContainer.setCtrShippable(Boolean.TRUE);
    asnChildContainer.setCtrReusable(Boolean.TRUE);
    asnChildContainer.setOutboundChannelMethod("S2S");
    Map<String, String> asnCtrDestination = new HashMap<>();
    asnCtrDestination.put("countryCode", "US");
    asnCtrDestination.put("buNumber", "5091");
    asnContainer.setCtrShippable(Boolean.TRUE);
    asnContainer.setCtrReusable(Boolean.FALSE);
    asnContainer.setOutboundChannelMethod("S2S");
    asnContainer.setInventoryStatus("PICKED");
    asnContainer.setCtrType("CARTON");
    asnContainer.setTrackingId("00000077670099006775");
    List<Content> asnContents = new ArrayList<>();
    Content asnChildContent = new Content();
    asnChildContent.setFinancialReportingGroup("US");
    asnChildContent.setBaseDivisionCode("WM");
    asnChildContent.setItemNbr(-1l);
    List<Distribution> asnDistributions = new ArrayList<>();
    Distribution asnDistribution = new Distribution();
    asnDistribution.setAllocQty(1);
    asnDistribution.setDestCC("US");
    asnDistribution.setDestNbr(5091);
    asnDistribution.setOrderId("0618437030");
    Map<String, String> distItem = new HashMap<>();
    distItem.put("itemNbr", "-1");
    distItem.put("baseDivisionCode", "WM");
    distItem.put("financialReportingGroup", "US");
    asnDistribution.setItem(distItem);
    asnDistributions.add(asnDistribution);
    asnChildContent.setDistributions(asnDistributions);
    asnContents.add(asnChildContent);
    asnChildContainer.setContents(asnContents);
    asnChildContainers.add(asnChildContainer);
    asnContainer.setChildContainers(asnChildContainers);

    asnFdeCreateContainerResponse.setContainer(asnContainer);

    return asnFdeCreateContainerResponse;
  }

  public static Instruction getInstructionWithFirstExpiryFirstOut() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("3587"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(50001001L);
    instruction.setGtin("00028000114602");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(2);
    instruction.setProviderId("DA-SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.TRUE);
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
            + "                \"itemDescription2\": \"NEW F20 WK 28\"\n"
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

  public static Instruction getInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1901"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("300001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("AutoGrocBuildPallet");
    instruction.setInstructionMsg("Auto Groc Build Pallet");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getMoveData());
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
            + "                                      \"warehouseAreaDesc\": \"Dry Produce\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\","
            + "                                      \"whpkDimensions\": {\n"
            + "                                              \"uom\": \"IN\",\n"
            + "                                              \"depth\": 19.75,\n"
            + "                                              \"width\": 15.75,\n"
            + "                                              \"height\": 9.75\n"
            + "                                           }"
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
    labelData.put("value", userId);
    labelData.put("key", "TYPE");
    labelData.put("value", "SSTK");
    labelData.put("key", "DESC1");
    labelData.put("value", "TR ED 3PC FRY/GRL RD");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a32612000000000001");
    printRequest.put("formatName", ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);
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

  public static Instruction getManualGdcInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1234"));
    instruction.setContainer(getManualGdcContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("95271819"));
    instruction.setGtin("01123840356119");
    instruction.setInstructionCode("ManlGrocBuildPallet");
    instruction.setInstructionMsg("Manl Groc Build Pallet");
    instruction.setItemDescription("4\" MKT BAN CRM N");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("6071");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("3864037181");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("GDC-RCV");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getManualGdcMoveData());
    instruction.setDeliveryDocument(
        "{\n"
            + "    \"purchaseReferenceNumber\": \"3864037181\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"480889\",\n"
            + "    \"vendorNbrDeptSeq\": 480889940,\n"
            + "    \"deptNumber\": \"94\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"28\",\n"
            + "    \"poDCNumber\": \"6071\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"gtin\": \"01123840356119\",\n"
            + "            \"itemUPC\": \"01123840356119\",\n"
            + "            \"caseUPC\": \"11188122713797\",\n"
            + "            \"purchaseReferenceNumber\": \"3864037181\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 23.89,\n"
            + "            \"vendorPackCost\": 23.89,\n"
            + "            \"vnpkQty\": 11,\n"
            + "            \"whpkQty\": 11,\n"
            + "            \"orderableQuantity\": 11,\n"
            + "            \"warehousePackQuantity\": 11,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"openQty\": 81,\n"
            + "            \"expectedQty\": 81,\n"
            + "            \"overageQtyLimit\": 20,\n"
            + "            \"itemNbr\": 2267740,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 9,\n"
            + "            \"palletHi\": 9,\n"
            + "            \"vnpkWgtQty\": 10,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.852,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"8DAYS\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"4\\\" MKT BAN CRM N\",\n"
            + "            \"itemDescription2\": \"<T&S>\",\n"
            + "            \"isConveyable\": false,\n"
            + "            \"warehouseRotationTypeCode\": \"3\",\n"
            + "            \"firstExpiryFirstOut\": true,\n"
            + "            \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "            \"profiledWarehouseArea\": \"CPS\",\n"
            + "            \"promoBuyInd\": \"N\",\n"
            + "            \"additionalInfo\": {\n"
            + "                \"warehouseAreaCode\": \"4\",\n"
            + "                \"warehouseGroupCode\": \"DD\",\n"
            + "                \"isNewItem\": false,\n"
            + "                \"profiledWarehouseArea\": \"CPS\",\n"
            + "                \"warehouseRotationTypeCode\": \"1\",\n"
            + "                \"recall\": false,\n"
            + "                \"weight\": 3.325,\n"
            + "                \"weightFormatTypeCode\": \"V\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "                \"primeSlotSize\": 0,\n"
            + "                \"isHazardous\": 0,\n"
            + "                \"itemHandlingMethod\": \"Case Pack Automatic Inbound\",\n"
            + "                \"atlasConvertedItem\": false,\n"
            + "                \"isWholesaler\": false\n"
            + "            },\n"
            + "            \"operationalInfo\": {\n"
            + "                \"state\": \"ACTIVE\"\n"
            + "            },\n"
            + "            \"freightBillQty\": 170,\n"
            + "            \"bolWeight\": 0.5882,\n"
            + "            \"activeChannelMethods\": [],\n"
            + "            \"department\": \"98\",\n"
            + "            \"vendorStockNumber\": \"11357\",\n"
            + "            \"totalReceivedQty\": 0,\n"
            + "            \"maxAllowedOverageQtyIncluded\": false,\n"
            + "            \"lithiumIonVerificationRequired\": false,\n"
            + "            \"limitedQtyVerificationRequired\": false,\n"
            + "            \"isNewItem\": false,\n"
            + "            \"autoPopulateReceivingQty\": false\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 170,\n"
            + "    \"weight\": 0,\n"
            + "    \"cubeQty\": 0,\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"OPN\",\n"
            + "    \"poTypeCode\": 28,\n"
            + "    \"totalBolFbq\": 0,\n"
            + "    \"deliveryLegacyStatus\": \"OPN\",\n"
            + "    \"stateReasonCodes\": [\n"
            + "        \"DOOR_OPEN\"\n"
            + "    ],\n"
            + "    \"deliveryNumber\": 22226071,\n"
            + "    \"importInd\": false,\n"
            + "    \"sellerId\": \"F55CDC31AB754BB68FE0B39041159D63\",\n"
            + "    \"sellerType\": \"WM\"\n"
            + "}");

    return instruction;
  }

  public static ContainerDetails getManualGdcContainerData() {
    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "itemNum");
    labelData.put("value", "573170821");
    labelDataList.add(labelData);
    Map<String, Object> labelData2 = new HashMap<String, Object>();
    labelData2.put("key", "itemSize");
    labelData2.put("value", "EA");
    labelDataList.add(labelData2);
    Map<String, Object> labelData3 = new HashMap<String, Object>();
    labelData3.put("key", "itemPack");
    labelData3.put("value", "11");
    labelDataList.add(labelData3);
    Map<String, Object> labelData4 = new HashMap<String, Object>();
    labelData4.put("key", "tiHi");
    labelData4.put("value", "5 - 4");
    labelDataList.add(labelData4);
    Map<String, Object> labelData5 = new HashMap<String, Object>();
    labelData5.put("key", "LPN");
    labelData5.put("value", "TAG-123");
    labelDataList.add(labelData5);
    Map<String, Object> labelData6 = new HashMap<String, Object>();
    labelData6.put("key", "slot");
    labelData6.put("value", "SLOT-123");
    labelDataList.add(labelData6);
    Map<String, Object> labelData7 = new HashMap<String, Object>();
    labelData7.put("key", "itemDesc");
    labelData7.put("value", "TR ED 3PC FRY/GRL RD");
    labelDataList.add(labelData7);
    Map<String, Object> labelData8 = new HashMap<String, Object>();
    labelData8.put("key", "vendorStockNbr");
    labelData8.put("value", "11357");
    labelDataList.add(labelData8);
    Map<String, Object> labelData9 = new HashMap<String, Object>();
    labelData9.put("key", "PO");
    labelData9.put("value", "3864037181");
    labelDataList.add(labelData9);
    Map<String, Object> labelData10 = new HashMap<String, Object>();
    labelData10.put("key", "WA");
    labelData10.put("value", "DD");
    labelDataList.add(labelData10);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "TAG-123");
    printRequest.put("formatName", GdcConstants.GDC_LABEL_FORMAT_NAME);
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<Map<String, Object>>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", "receiving");
    containerLabel.put("headers", GdcHttpHeaders.getHeaders());
    containerLabel.put("printRequests", printRequestList);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setProjectedWeightUom(ReceivingConstants.Uom.VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("TAG-123");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrShippable(Boolean.TRUE);
    containerDetails.setCtrReusable(Boolean.TRUE);
    containerDetails.setQuantity(2);
    containerDetails.setGlsWeight(362.758);
    containerDetails.setGlsWeightUOM("LB");
    containerDetails.setGlsTimestamp(LocalDateTime.now().toString());
    containerDetails.setCtrLabel(containerLabel);

    return containerDetails;
  }

  public static LinkedTreeMap<String, Object> getManualGdcMoveData() {
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "sysadmin");
    move.put("lastChangedOn", new Date());
    move.put("containerTag", "TAG-123");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "SLOT-123");
    move.put("fromLocation", "101");

    return move;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForPoCon() {

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039635");
    deliveryDocumentLine.setItemUpc("00016017039635");
    deliveryDocumentLine.setCaseUpc("00016017039635");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("POCON");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    return deliveryDocuments;
  }

  public static InstructionRequest getInstructionRequestForPoCon() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");

    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForPoCon() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039635");
    deliveryDocumentLine.setItemUpc("00016017039635");
    deliveryDocumentLine.setCaseUpc("00016017039635");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("POCON");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static DeliveryDocument getRejectedDeliveryDocument() {

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06938");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039630");
    deliveryDocumentLine.setItemUpc("00016017039630");
    deliveryDocumentLine.setCaseUpc("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    OperationalInfo operationalInfo = new OperationalInfo();
    operationalInfo.setState("REJECTED");
    deliveryDocumentLine.setOperationalInfo(operationalInfo);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    return deliveryDocument;
  }

  public static ContainerDetails getWitronContainerData() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "LPN");
    labelData.put("value", "A47341986287612711");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "A47341986287612711");
    printRequest.put("formatName", ReceivingConstants.PRINT_LABEL_WITRON_TEMPLATE);
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<Map<String, Object>>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", "receiving");
    containerLabel.put("headers", GdcHttpHeaders.getHeaders());
    containerLabel.put("printRequests", printRequestList);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOutboundChannelMethod("SSTKU");
    containerDetails.setTrackingId("A47341986287612711");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("AVAILABLE");
    containerDetails.setCtrShippable(Boolean.TRUE);
    containerDetails.setCtrReusable(Boolean.TRUE);
    containerDetails.setQuantity(12);
    containerDetails.setCtrLabel(containerLabel);

    return containerDetails;
  }

  public static Instruction getDockTagInstruction() {
    LinkedTreeMap<String, Object> moveTreeMap = new LinkedTreeMap<>();
    moveTreeMap.put("toLocation", "EFLCP08");
    moveTreeMap.put("correlationID", "1a2bc3d4");
    moveTreeMap.put("containerTag", "c32612000000000000000001");
    moveTreeMap.put("lastChangedOn", new Date());
    moveTreeMap.put("lastChangedBy", userId);

    Instruction instruction = new Instruction();
    instruction.setId(326120001L);
    instruction.setInstructionCode(ReceivingConstants.DOCK_TAG);
    instruction.setInstructionMsg("Create dock tag container instruction");
    instruction.setProviderId(ReceivingConstants.RECEIVING_PROVIDER_ID);
    instruction.setContainer(getDockTagContainerDetails());
    instruction.setMessageId("0eb0a8b6-36e1-4792-b4a1-9da242d8199e");
    instruction.setDockTagId("c32612000000000000000001");
    instruction.setActivityName(ReceivingConstants.DOCK_TAG);
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setDeliveryNumber(261220189L);
    instruction.setPurchaseReferenceNumber("");
    instruction.setPurchaseReferenceLineNumber(0);
    instruction.setCreateUserId(userId);
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
    labelData.put("value", "c32612000000000000000001");
    labelData.put("key", "FULLUSERID");
    labelData.put("value", userId);
    labelData.put("key", "DELIVERYNBR");
    labelData.put("value", 261220189L);

    List<Map<String, Object>> labelDataList = new ArrayList<>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "c32612000000000000000001");
    printRequest.put("formatName", "dock_tag_atlas");
    printRequest.put("ttlInHours", 72);
    printRequest.put("data", labelDataList);

    List<Map<String, Object>> printRequestList = new ArrayList<>();
    printRequestList.add(printRequest);

    Map<String, Object> containerLabel = new HashMap<>();
    containerLabel.put("clientId", ReceivingConstants.RECEIVING_PROVIDER_ID);
    HttpHeaders httpHeaders = MockHttpHeaders.getHeaders();
    httpHeaders.add(ReceivingConstants.WMT_PRODUCT_NAME, ReceivingConstants.APP_NAME_VALUE);
    containerLabel.put("headers", httpHeaders);
    containerLabel.put("printRequests", printRequestList);

    containerDetails = new ContainerDetails();
    containerDetails.setTrackingId("c32612000000000000000001");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("WORK_IN_PROGRESS");
    containerDetails.setCtrReusable(Boolean.FALSE);
    containerDetails.setCtrShippable(Boolean.FALSE);
    containerDetails.setCtrLabel(containerLabel);
    return containerDetails;
  }

  public static Instruction getInstructionWithPTAG() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("12345"));
    instruction.setMessageId("114af63a-c61a-4222-815b-ae7ae4a82g11");
    instruction.setProblemTagId("32612000000001");
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setDeliveryNumber(Long.valueOf("21809792"));
    instruction.setPurchaseReferenceNumber("056435417");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setGtin("00260434000001");
    instruction.setPoDcNumber("32612");
    instruction.setActivityName("SSTK");
    instruction.setProviderId("Witron");
    instruction.setItemDescription("MKS LAMB BRST SPLIT");
    instruction.setInstructionCode(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionCode());
    instruction.setInstructionMsg(GdcInstructionType.AUTO_GROC_BUILD_PALLET.getInstructionMsg());
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setProjectedReceiveQty(5);
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(false);
    instruction.setIsReceiveCorrection(false);

    return instruction;
  }

  public static UpdateInstructionRequest getUpdateInstructionRequestWithPTAG() {
    DocumentLine documentLine = new DocumentLine();
    List<DocumentLine> documentLines = new ArrayList<>();
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();

    updateInstructionRequest.setDeliveryNumber(Long.valueOf("21809792"));
    updateInstructionRequest.setProblemTagId("32612000000001");
    updateInstructionRequest.setDoorNumber("PA01");
    updateInstructionRequest.setContainerType("Chep Pallet");
    documentLine.setPurchaseReferenceNumber("056435417");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setQuantity(5);
    documentLine.setQuantityUOM(ReceivingConstants.Uom.VNPK);
    documentLine.setExpectedQty(Long.valueOf("500"));
    documentLine.setTotalPurchaseReferenceQty(500);
    documentLine.setMaxOverageAcceptQty(Long.valueOf("0"));
    documentLine.setMaxReceiveQty(Long.valueOf("5"));
    documentLine.setGtin("00000001234");
    documentLine.setItemNumber(Long.valueOf("557959102"));
    documentLine.setMaxOverageAcceptQty(20L);
    documentLine.setPoDCNumber("32612");
    documentLine.setVnpkQty(6);
    documentLine.setWhpkQty(6);
    documentLine.setVnpkWgtQty(13.00f);
    documentLine.setVnpkWgtUom("LB");
    documentLine.setWarehouseMinLifeRemainingToReceive(18);
    documentLine.setRotateDate(new Date());
    documentLine.setPromoBuyInd("N");
    documentLine.setDescription("MKS LAMB BRST SPLIT");
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static UpdateInstructionRequest getUpdateInstructionRequest() {
    DocumentLine documentLine = new DocumentLine();
    List<DocumentLine> documentLines = new ArrayList<>();
    UpdateInstructionRequest updateInstructionRequest = new UpdateInstructionRequest();

    updateInstructionRequest.setDeliveryNumber(Long.valueOf("21809792"));
    updateInstructionRequest.setProblemTagId(null);
    updateInstructionRequest.setDoorNumber("101");
    updateInstructionRequest.setContainerType("Chep Pallet");
    documentLine.setPurchaseReferenceNumber("056435417");
    documentLine.setPurchaseReferenceLineNumber(1);
    documentLine.setPurchaseRefType("SSTKU");
    documentLine.setQuantity(2);
    documentLine.setQuantityUOM(ReceivingConstants.Uom.VNPK);
    documentLine.setExpectedQty(Long.valueOf("1"));
    documentLine.setTotalPurchaseReferenceQty(1);
    documentLine.setMaxOverageAcceptQty(Long.valueOf("0"));
    documentLine.setMaxReceiveQty(Long.valueOf("1"));
    documentLine.setGtin("00000001234");
    documentLine.setItemNumber(Long.valueOf("557959102"));
    documentLine.setMaxOverageAcceptQty(null);
    documentLine.setPoDCNumber("32612");
    documentLine.setVnpkQty(6);
    documentLine.setWhpkQty(6);
    documentLine.setVnpkWgtQty(13.00f);
    documentLine.setVnpkWgtUom("LB");
    documentLine.setWarehouseMinLifeRemainingToReceive(30);
    documentLine.setRotateDate(new Date());
    documentLine.setPromoBuyInd("N");
    documentLine.setDescription("MKS LAMB BRST SPLIT");
    documentLines.add(documentLine);
    updateInstructionRequest.setDeliveryDocumentLines(documentLines);

    return updateInstructionRequest;
  }

  public static Instruction getCancelledInstruction() {
    // Mock cancelled instruction
    Instruction cancelledInstruction = new Instruction();
    cancelledInstruction.setId(Long.valueOf("11120"));
    cancelledInstruction.setContainer(getContainerDetails());
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

  public static ProblemLabel getMockProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setProblemTagId("5343435");
    problemLabel.setResolutionId("2323-2323-32323");
    String problemResponse =
        "{\"id\":\"0469db26-43bf-4499-9723-7a6244e7e2e0\",\"label\":\"06001969468600\",\"slot\":\"M5019\",\"status\":\"PARTIALLY_RECEIVED\",\"remainingQty\":0,\"reportedQty\":335,\"issue\":{\"id\":\"dffcc688-8550-4196-9387-9d6d3fc4409b\",\"identifier\":\"210820-46422-2559-0000\",\"type\":\"DI\",\"subType\":\"PO_ISSUE\",\"deliveryNumber\":\"95334888\",\"upc\":\"331722632317\",\"itemNumber\":563045609,\"quantity\":335,\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\"},\"resolutions\":[{\"id\":\"b0d1719e-c7f9-4f8f-adf8-0237d42ab4c4\",\"provider\":\"Manual\",\"quantity\":335,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"remainingQty\":335,\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"resolutionPoNbr\":\"8458709170\",\"resolutionPoLineNbr\":1,\"state\":\"PARTIAL\"}]}";
    problemLabel.setProblemResponse(problemResponse);
    return problemLabel;
  }

  public static ContainerDetails getContainerDetailsOldPrintForPoCon() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "TYPE");
    labelData.put("value", "POCON");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatID", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);
    printRequest.put("labelData", labelDataList);
    printRequest.put("clientId", "OF");

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
    containerDetails.setCtrLabel(printRequest);

    return containerDetails;
  }

  public static ContainerDetails getContainerDetailsforPocon() {
    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "TYPE");
    labelData.put("value", "POCON");
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

  public static ContainerDetails getContainerDetailsOldPrintForDSDC() {
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "TYPE");
    labelData.put("value", "DSDC");

    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    labelDataList.add(labelData);

    Map<String, Object> printRequest = new HashMap<>();
    printRequest.put("labelIdentifier", "a328990000000000000106509");
    printRequest.put("formatID", "pallet_lpn_format");
    printRequest.put("ttlInHours", 1);
    printRequest.put("data", labelDataList);
    printRequest.put("labelData", labelDataList);
    printRequest.put("clientId", "OF");

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
    containerDetails.setCtrLabel(printRequest);

    return containerDetails;
  }

  public static ContainerDetails getContainerDetailsforDSDC() {
    List<Map<String, Object>> labelDataList = new ArrayList<Map<String, Object>>();
    Map<String, Object> labelData = new HashMap<String, Object>();
    labelData.put("key", "TYPE");
    labelData.put("value", "DSDC");
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

  public static GLSReceiveResponse mockGlsResponse() {
    GLSReceiveResponse glsReceiveResponse = new GLSReceiveResponse();
    glsReceiveResponse.setPalletTagId("TAG-123");
    glsReceiveResponse.setSlotId("SLOT-1");
    glsReceiveResponse.setWeight(362.978);
    glsReceiveResponse.setTimestamp(LocalDateTime.now().toString());
    glsReceiveResponse.setWeightUOM("LB");
    return glsReceiveResponse;
  }

  public static Instruction getInstructionWithConvertedItemAtlas() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1901"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("300001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("AutoGrocBuildPallet");
    instruction.setInstructionMsg("Auto Groc Build Pallet");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getMoveData());
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
            + "                                      \"warehouseAreaDesc\": \"Dry Produce\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 70,"
            + "                                      \"weightUOM\": \"LB\","
            + "                                      \"whpkDimensions\": {\n"
            + "                                              \"uom\": \"IN\",\n"
            + "                                              \"depth\": 19.75,\n"
            + "                                              \"width\": 15.75,\n"
            + "                                              \"height\": 9.75\n"
            + "                                           },"
            + "                                     \"atlasConvertedItem\": \"true\""
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

  public static Instruction getWmInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("22377674"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("ManlGrocBuildPallet");
    instruction.setInstructionMsg("Manl Groc Build Pallet");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getMoveData());
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
            + "                \"purchaseRefType\": \"SSTKU\",\n"
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
            + "                                      \"warehouseAreaDesc\": \"Dry Produce\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 30,"
            + "                                      \"allowedTimeInWarehouseQty\": 10,"
            + "                                      \"weightUOM\": \"LB\","
            + "                                      \"whpkDimensions\": {\n"
            + "                                              \"uom\": \"IN\",\n"
            + "                                              \"depth\": 19.75,\n"
            + "                                              \"width\": 15.75,\n"
            + "                                              \"height\": 9.75\n"
            + "                                           }"
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

  public static Instruction getSamsInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("22377674"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("ManlGrocBuildPallet");
    instruction.setInstructionMsg("Manl Groc Build Pallet");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getMoveData());
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"SAMS\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"SSTKU\",\n"
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
            + "                                      \"warehouseAreaDesc\": \"Dry Produce\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"3\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 30,"
            + "                                      \"weightUOM\": \"LB\","
            + "                                      \"whpkDimensions\": {\n"
            + "                                              \"uom\": \"IN\",\n"
            + "                                              \"depth\": 19.75,\n"
            + "                                              \"width\": 15.75,\n"
            + "                                              \"height\": 9.75\n"
            + "                                           }"
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

  public static Instruction getSamsInstructionWithRotationTypeCode4() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("3"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("22377674"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("ManlGrocBuildPallet");
    instruction.setInstructionMsg("Manl Groc Build Pallet");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("11e1df00-ebf6-11e8-9c25-dd4bfc2b96a1");
    instruction.setPoDcNumber("32612");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("4166030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setIsReceiveCorrection(Boolean.FALSE);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId(userId);
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId(userId);
    instruction.setMove(getMoveData());
    instruction.setDeliveryDocument(
        " {\n"
            + "        \"documentNbr\": \"3515421377\",\n"
            + "        \"purchaseReferenceNumber\": \"3515421377\",\n"
            + "        \"poDCNumber\": \"6938\",\n"
            + "        \"baseDivCode\": \"SAMS\",\n"
            + "        \"purchaseReferenceLegacyType\": \"33\",\n"
            + "        \"purchaseCompanyId\": 1,\n"
            + "        \"deliveryDocumentLines\": [\n"
            + "            {\n"
            + "                \"purchaseRefType\": \"SSTKU\",\n"
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
            + "                                      \"warehouseAreaDesc\": \"Dry Produce\", "
            + "                                      \"profiledWarehouseArea\": \"CPS\","
            + "                                      \"warehouseRotationTypeCode\": \"4\","
            + "                                      \"recall\": false,"
            + "                                      \"weight\": 13.0,"
            + "                                      \"isVariableWeight\": true,"
            + "                                      \"warehouseMinLifeRemainingToReceive\": 30,"
            + "                                      \"weightUOM\": \"LB\","
            + "                                      \"whpkDimensions\": {\n"
            + "                                              \"uom\": \"IN\",\n"
            + "                                              \"depth\": 19.75,\n"
            + "                                              \"width\": 15.75,\n"
            + "                                              \"height\": 9.75\n"
            + "                                           }"
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

  public static List<DeliveryDocument> getDeliveryDocumentsForDSDC() {

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06938");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("73");
    deliveryDocument1.setVendorNumber("482497180");
    deliveryDocument1.setPurchaseReferenceNumber("4763030227");
    deliveryDocument1.setCubeQty(40.00F);
    deliveryDocument1.setCubeUOM("LF");
    deliveryDocument1.setWeight(40.00F);
    deliveryDocument1.setWeightUOM("CB");

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039630");
    deliveryDocumentLine.setItemUpc("00016017039630");
    deliveryDocumentLine.setCaseUpc("00000943037204");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129241l);
    deliveryDocumentLine.setPurchaseRefType("DSDC");
    deliveryDocumentLine.setPalletHigh(4);
    deliveryDocumentLine.setPalletTie(6);
    deliveryDocumentLine.setWeight(9.35f);
    deliveryDocumentLine.setWeightUom("lb");
    deliveryDocumentLine.setCube(0f);
    deliveryDocumentLine.setCubeUom("");
    deliveryDocumentLine.setColor("NONE");
    deliveryDocumentLine.setSize("1.0EA");
    deliveryDocumentLine.setIsHazmat(Boolean.FALSE);
    deliveryDocumentLine.setDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.FALSE);
    deliveryDocumentLine.setOpenQty(10);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);

    DeliveryDocument deliveryDocument2 = new DeliveryDocument();
    deliveryDocument2.setBaseDivisionCode("WM");
    deliveryDocument2.setDeptNumber("14");
    deliveryDocument2.setFinancialReportingGroup("US");
    deliveryDocument2.setPoDCNumber("06938");
    deliveryDocument2.setPurchaseCompanyId("1");
    deliveryDocument2.setPurchaseReferenceLegacyType("33");
    deliveryDocument2.setVendorNumber("482497180");
    deliveryDocument2.setPurchaseReferenceNumber("4763030227");
    deliveryDocumentLine.setPurchaseRefType("SSTK");
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument2.setDeliveryDocumentLines(deliveryDocumentLines);
    deliveryDocument1.setDeliveryDocumentLines(deliveryDocumentLines);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument2);
    deliveryDocuments.add(deliveryDocument1);

    return deliveryDocuments;
  }
}
