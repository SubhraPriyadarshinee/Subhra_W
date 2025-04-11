package com.walmart.move.nim.receiving.core.mock.data;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.utils.constants.ContainerException;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.POLineStatus;
import com.walmart.move.nim.receiving.utils.constants.POStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

public class MockInstruction {

  private static final Gson gson = new Gson();

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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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

  public static Instruction getOpenInstructionForLessThan14DigitUPC() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("1234"));
    instruction.setGtin("765843037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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

  public static Instruction getPendingInstruction() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2"));
    instruction.setContainer(getContainerDetails());
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037204");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
            + "                \"totalReceivedQty\": 10,\n"
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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setCompleteTs(new Date());
    instruction.setCompleteUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("0087876804154");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
    container.setOrgUnitId("abc");
    container.setPublishTs(new Date());
    container.setCreateTs(new Date());
    container.setCreateUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setLastChangedUser("sysadmin");
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
    containerItem.setRotateDate(new Date());
    containerItem.setDistributions(getDistributions());
    containerItem.setLotNumber("ADC8908A");

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
    childContainer1.setOrgUnitId("");
    childContainer1.setCompleteTs(new Date());
    childContainer1.setPublishTs(new Date());
    childContainer1.setCreateTs(new Date());
    childContainer1.setCreateUser("sysadmin");
    childContainer1.setLastChangedTs(new Date());
    childContainer1.setLastChangedUser("sysadmin");
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
    childContainer2.setOrgUnitId("");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setPublishTs(new Date());
    childContainer2.setCreateTs(new Date());
    childContainer2.setCreateUser("sysadmin");
    childContainer2.setLastChangedTs(new Date());
    childContainer2.setLastChangedUser("sysadmin");
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
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setPurchaseReferenceMustArriveByDate(new Date(1599471477636L));
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));
    deliveryDocument.setPurchaseReferenceStatus(POStatus.ACTV.name());

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
    deliveryDocumentLine.setSecondaryDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.TRUE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setWarehousePack(4);
    deliveryDocumentLine.setVendorPack(4);
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.ACTIVE.name());
    deliveryDocumentLine.setFreightBillQty(15);

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

  public static List<DeliveryDocument> getDeliveryDocumentsWithCountryCode() {

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06938");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setPurchaseReferenceMustArriveByDate(new Date(1599471477636L));
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));
    deliveryDocument.setPurchaseReferenceStatus(POStatus.ACTV.name());

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
    deliveryDocumentLine.setSecondaryDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.TRUE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setWarehousePack(4);
    deliveryDocumentLine.setVendorPack(4);
    deliveryDocumentLine.setPurchaseReferenceLineStatus(POLineStatus.ACTIVE.name());
    deliveryDocumentLine.setOriginCountryCode(Collections.singletonList("US"));

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

  public static List<DeliveryDocument> getMultiPoDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getDeliveryDocuments();

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06561");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497181");
    deliveryDocument.setPurchaseReferenceNumber("4763030228");
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setPurchaseReferenceMustArriveByDate(new Date(1599471477636L));
    deliveryDocument.setDeliveryStatus(DeliveryStatus.OPN);
    deliveryDocument.setStateReasonCodes(Collections.singletonList("WORKING"));

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("4763030228");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(1);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039631");
    deliveryDocumentLine.setItemUpc("00016017039631");
    deliveryDocumentLine.setCaseUpc("00000943037205");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129242L);
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
    deliveryDocumentLine.setSecondaryDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.TRUE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setWarehousePack(4);
    deliveryDocumentLine.setVendorPack(4);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    deliveryDocuments.add(deliveryDocument);
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getMultiPoLineDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getDeliveryDocuments();

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseReferenceNumber("4763030227");
    deliveryDocumentLine.setPurchaseReferenceLineNumber(2);
    deliveryDocumentLine.setQtyUOM("EA");
    deliveryDocumentLine.setGtin("00016017039631");
    deliveryDocumentLine.setItemUpc("00016017039631");
    deliveryDocumentLine.setCaseUpc("00000943037205");
    deliveryDocumentLine.setEvent("POS REPLEN");
    deliveryDocumentLine.setWarehousePackSell(28.18f);
    deliveryDocumentLine.setVendorPackCost(26.98f);
    deliveryDocumentLine.setTotalOrderQty(10);
    deliveryDocumentLine.setOverageQtyLimit(5);
    deliveryDocumentLine.setItemNbr(550129242L);
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
    deliveryDocumentLine.setSecondaryDescription("TR 12QT STCKPT SS");
    deliveryDocumentLine.setIsConveyable(Boolean.TRUE);
    deliveryDocumentLine.setOpenQty(10);
    deliveryDocumentLine.setWarehousePack(4);
    deliveryDocumentLine.setVendorPack(4);

    ItemData itemData = new ItemData();
    itemData.setWarehouseAreaCode("1");
    itemData.setWarehouseGroupCode("DD");
    itemData.setWarehouseRotationTypeCode("1");
    itemData.setWarehouseMinLifeRemainingToReceive(9);
    deliveryDocumentLine.setAdditionalInfo(itemData);

    deliveryDocuments.get(0).getDeliveryDocumentLines().add(deliveryDocumentLine);
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getCancelledSinglePoDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceStatus(POStatus.CNCL.name());
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getCancelledSinglePoLineDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getPartialCancelledMultiPoDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getMultiPoDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceStatus(POStatus.CNCL.name());
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getPartialCancelledMultiPoLineDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getMultiPoLineDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getAllCancelledMultiPoDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getMultiPoDeliveryDocuments();
    deliveryDocuments.get(0).setPurchaseReferenceStatus(POStatus.CNCL.name());
    deliveryDocuments.get(1).setPurchaseReferenceStatus(POStatus.CNCL.name());
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getAllCancelledMultiPoLineDeliveryDocuments() {
    List<DeliveryDocument> deliveryDocuments = getMultiPoLineDeliveryDocuments();
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(1)
        .setPurchaseReferenceLineStatus(POLineStatus.CANCELLED.name());
    return deliveryDocuments;
  }

  public static PrintJob getPrintJob() {
    PrintJob printJob = new PrintJob();

    Set<String> printJobLpnSet = new HashSet<>();
    printJobLpnSet.add("a328990000000000000106509");

    printJob = new PrintJob();
    printJob.setDeliveryNumber(Long.valueOf("21119003"));
    printJob.setCreateUserId("sysadmin");
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
    instructionRequest.setDeliveryDocuments(getDeliveryDocuments());
    instructionRequest.setReceivingType(ReceivingConstants.UPC);

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

  public static List<DeliveryDocument> getDeliveryDocumentsForMultiPO_for_manualPO()
      throws IOException {
    File resource = new ClassPathResource("Gdm_MultiPo.json").getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static InstructionRequest
      getInstructionRequestWithoutDeliveryDocumentWhenManualSelectionEnabled() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    instructionRequest.setPoManualSelectionEnabled(Boolean.TRUE);
    return instructionRequest;
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
    instruction.setCreateUserId("sysadmin");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("sysadmin");
    instruction.setDeliveryNumber(50001001L);
    instruction.setGtin("00028000114602");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
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
            + "                \"totalReceivedQty\": 0,\n"
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

  public static Instruction getInstructionForPOCON() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1901"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("300001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("POCON");
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
            + "                \"purchaseRefType\": \"POCON\",\n"
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

  public static Instruction getInstructionWithManufactureDetails() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2076"));
    instruction.setContainer(getContainerData());
    instruction.setChildContainers(null);
    instruction.setDeliveryNumber(Long.parseLong("798001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("TEST ITEM DESCR");
    instruction.setActivityName("SSTK");
    instruction.setMessageId("56y56det-ebf6-11e8-9c25-dd4b09hu96a1");
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(Boolean.FALSE);
    instruction.setPurchaseReferenceNumber("9876030001");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("DA-SSTK");
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);
    instruction.setFirstExpiryFirstOut(Boolean.FALSE);
    instruction.setDeliveryDocument(
        " {\n"
            + "  \"purchaseReferenceNumber\": \"2791747859\",\n"
            + "  \"financialGroupCode\": \"US\",\n"
            + "  \"baseDivCode\": \"WM\",\n"
            + "  \"purchaseCompanyId\": \"1\",\n"
            + "  \"purchaseReferenceLegacyType\": \"33\",\n"
            + "  \"poDCNumber\": \"32898\",\n"
            + "  \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"gtin\": \"00028000114603\",\n"
            + "      \"lotNumber\": \"ABCDEF1234\",\n"
            + "      \"itemUPC\": \"00029695410987\",\n"
            + "      \"caseUPC\": \"20029695410987\",\n"
            + "      \"purchaseReferenceNumber\": \"2791747859\",\n"
            + "      \"purchaseReferenceLineNumber\": 1,\n"
            + "      \"event\": \"POS REPLEN\",\n"
            + "      \"purchaseReferenceLineStatus\": \"RECEIVED\",\n"
            + "      \"whpkSell\": 8.22,\n"
            + "      \"vendorPackCost\": 6.6,\n"
            + "      \"vnpkQty\": 6,\n"
            + "      \"whpkQty\": 6,\n"
            + "      \"openQty\": 10,\n"
            + "      \"expectedQty\": 10,\n"
            + "      \"overageQtyLimit\": 0,\n"
            + "      \"itemNbr\": 561298341,\n"
            + "      \"purchaseRefType\": \"33\",\n"
            + "      \"palletTi\": 0,\n"
            + "      \"palletHi\": 0,\n"
            + "      \"vnpkWgtQty\": 14.84,\n"
            + "      \"vnpkWgtUom\": \"LB\",\n"
            + "      \"vnpkcbqty\": 0.432,\n"
            + "      \"vnpkcbuomcd\": \"CF\",\n"
            + "      \"color\": \"\",\n"
            + "      \"size\": \"\",\n"
            + "      \"isHazmat\": false,\n"
            + "      \"itemDescription1\": \"Tylenol\",\n"
            + "      \"palletSSCC\": \"00100700302232310224\",\n"
            + "      \"packSSCC\": \"B32899000020014243\",\n"
            + "      \"ndc\": \"123456\",\n"
            + "      \"shipmentDetails\": [{\n"
            + "        \"inboundShipmentDocId\": \"546191213_20191106_719468_VENDOR_US\",\n"
            + "        \"shipmentNumber\": \"546191213\",\n"
            + "        \"loadNumber\": \"88528711\",\n"
            + "        \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "        \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "      }],\n"
            + "      \"manufactureDetails\": [\n"
            + "        {\n"
            + "          \"lot\": \"ABCDEF1234\",\n"
            + "          \"qty\": \"2\",\n"
            + "          \"expiry\": \"12/30/2020\"\n"
            + "        }\n"
            + "      ]\n"
            + "    }\n"
            + "  ],\n"
            + "  \"weight\": 0,\n"
            + "  \"cubeQty\": 0,\n"
            + "  \"deliveryStatus\": \"SCH\",\n"
            + "  \"totalBolFbq\": 106\n"
            + "}");

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
    instructionRequest.setIsPOCON(Boolean.TRUE);

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPoDcCountry("US");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(5);
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setPalletQty(2);
    deliveryDocument.setReceivedPalletCount(1);

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

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForDSDC() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");
    instructionRequest.setIsDSDC(Boolean.TRUE);
    instructionRequest.setNonNationPo("DSDC");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("72");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(8);
    deliveryDocument.setEnteredPalletQty(1);
    deliveryDocument.setTotalPurchaseReferenceQty(1);
    deliveryDocument.setCubeQty(1.0f);
    deliveryDocument.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");
    deliveryDocument.setDeliveryDocumentLines(null);

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06999");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("72");
    deliveryDocument1.setVendorNumber("482497182");
    deliveryDocument1.setPurchaseReferenceNumber("4763030228");
    deliveryDocument1.setQuantity(8);
    deliveryDocument1.setTotalPurchaseReferenceQty(1);
    deliveryDocument1.setCubeQty(1.0f);
    deliveryDocument1.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");

    deliveryDocument1.setDeliveryDocumentLines(null);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    deliveryDocuments.add(deliveryDocument1);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForPocon() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");
    instructionRequest.setIsDSDC(Boolean.TRUE);
    instructionRequest.setNonNationPo("DSDC");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("72");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(8);
    deliveryDocument.setTotalPurchaseReferenceQty(1);
    deliveryDocument.setCubeQty(1.0f);
    deliveryDocument.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");
    deliveryDocument.setDeliveryDocumentLines(null);

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06999");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("72");
    deliveryDocument1.setVendorNumber("482497182");
    deliveryDocument1.setPurchaseReferenceNumber("4763030228");
    deliveryDocument1.setQuantity(8);
    deliveryDocument1.setTotalPurchaseReferenceQty(1);
    deliveryDocument1.setCubeQty(1.0f);
    deliveryDocument1.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");

    deliveryDocument1.setDeliveryDocumentLines(null);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    deliveryDocuments.add(deliveryDocument1);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForMultiPO() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");
    instructionRequest.setIsDSDC(Boolean.TRUE);
    instructionRequest.setNonNationPo("POCON");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("72");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(8);
    deliveryDocument.setTotalPurchaseReferenceQty(1);
    deliveryDocument.setCubeQty(1.0f);
    deliveryDocument.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");
    deliveryDocument.setDeliveryDocumentLines(null);

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06999");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("72");
    deliveryDocument1.setVendorNumber("482497182");
    deliveryDocument1.setPurchaseReferenceNumber("4763030228");
    deliveryDocument1.setQuantity(8);
    deliveryDocument1.setTotalPurchaseReferenceQty(1);
    deliveryDocument1.setCubeQty(1.0f);
    deliveryDocument1.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");

    deliveryDocument1.setDeliveryDocumentLines(null);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    deliveryDocuments.add(deliveryDocument1);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithDeliveryDocumentsForMultiPOPOCON() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");
    instructionRequest.setIsDSDC(Boolean.TRUE);
    instructionRequest.setNonNationPo("POCON");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("72");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(8);
    deliveryDocument.setTotalPurchaseReferenceQty(1);
    deliveryDocument.setCubeQty(1.0f);
    deliveryDocument.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");
    deliveryDocument.setDeliveryDocumentLines(null);

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06999");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("72");
    deliveryDocument1.setVendorNumber("482497182");
    deliveryDocument1.setPurchaseReferenceNumber("4763030228");
    deliveryDocument1.setQuantity(8);
    deliveryDocument1.setTotalPurchaseReferenceQty(1);
    deliveryDocument1.setCubeQty(1.0f);
    deliveryDocument1.setWeight(10.0f);
    deliveryDocument.setCubeUOM("CF");
    deliveryDocument.setWeightUOM("LB");

    deliveryDocument1.setDeliveryDocumentLines(null);
    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    deliveryDocuments.add(deliveryDocument1);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static FdeCreateContainerResponse getFdeCreateContainerResponseForDSDC() {
    FdeCreateContainerResponse fdeCreateContainerResponse = new FdeCreateContainerResponse();
    fdeCreateContainerResponse.setChildContainers(null);
    fdeCreateContainerResponse.setContainer(null);
    fdeCreateContainerResponse.setInstructionMsg("Build the Container");
    fdeCreateContainerResponse.setInstructionCode("Build Container");
    fdeCreateContainerResponse.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    fdeCreateContainerResponse.setPrintChildContainerLabels(false);
    fdeCreateContainerResponse.setProjectedQty(20);
    fdeCreateContainerResponse.setProjectedQtyUom("EA");
    fdeCreateContainerResponse.setProviderId("DSDC");
    fdeCreateContainerResponse.setActivityName("DSDC");
    fdeCreateContainerResponse.setMove(getMoveData());

    return fdeCreateContainerResponse;
  }

  public static FdeCreateContainerResponse getFdeCreateContainerResponseForPOCON() {
    FdeCreateContainerResponse fdeCreateContainerResponse = new FdeCreateContainerResponse();
    fdeCreateContainerResponse.setChildContainers(null);
    fdeCreateContainerResponse.setContainer(null);
    fdeCreateContainerResponse.setInstructionMsg("Build the Container");
    fdeCreateContainerResponse.setInstructionCode("Build Container");
    fdeCreateContainerResponse.setMessageId("8e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    fdeCreateContainerResponse.setPrintChildContainerLabels(false);
    fdeCreateContainerResponse.setProjectedQty(20);
    fdeCreateContainerResponse.setProjectedQtyUom("EA");
    fdeCreateContainerResponse.setProviderId("POCON");
    fdeCreateContainerResponse.setActivityName("POCON");
    fdeCreateContainerResponse.setMove(getMoveData());

    return fdeCreateContainerResponse;
  }

  public static InstructionRequest clientRequestForDSDCPOs() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d73hw3g-ebf6-11e8-9g75-dd4bf6dfa4u");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setIsDSDC(true);
    instructionRequest.setDoorNumber("333");
    instructionRequest.setDeliveryNumber("765432190");
    instructionRequest.setDeliveryDocuments(new ArrayList<>());
    return instructionRequest;
  }

  public static InstructionRequest clientRequestForPOCONPOs() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d73hw3g-ebf6-11e8-9g75-dd4bf6dfa4u");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setIsPOCON(true);
    instructionRequest.setDoorNumber("333");
    instructionRequest.setDeliveryNumber("765432190");
    instructionRequest.setDeliveryDocuments(new ArrayList<>());
    return instructionRequest;
  }

  public static InstructionRequest clientRequestForDSDCPrintJob() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d73hw3g-ebf6-11e8-9g75-dd4bf6dfa4u");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setIsDSDC(true);
    instructionRequest.setDoorNumber("786");
    instructionRequest.setDeliveryNumber("89768798");
    instructionRequest.setNonNationPo("DSDC");

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setQuantity(9);
    deliveryDocument.setPoDCNumber("32988");
    deliveryDocument.setPurchaseReferenceNumber("7320250027");
    deliveryDocument.setDeliveryDocumentLines(new ArrayList<>());
    deliveryDocuments.add(deliveryDocument);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    return instructionRequest;
  }

  public static InstructionRequest clientRequestForPOCONPrintJob() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d73hw3g-ebf6-11e8-9g75-dd4bf6dfa4u");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setIsPOCON(true);
    instructionRequest.setDoorNumber("786");
    instructionRequest.setDeliveryNumber("89768798");
    instructionRequest.setNonNationPo("POCON");

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setQuantity(9);
    deliveryDocument.setPoDCNumber("32988");
    deliveryDocument.setPurchaseReferenceNumber("7320250027");
    deliveryDocument.setDeliveryDocumentLines(new ArrayList<>());
    deliveryDocuments.add(deliveryDocument);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);

    return instructionRequest;
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

  public static List<DeliveryDocument> getDeliveryDocumentsForPOCON() {

    DeliveryDocument deliveryDocument1 = new DeliveryDocument();
    deliveryDocument1.setBaseDivisionCode("WM");
    deliveryDocument1.setDeptNumber("14");
    deliveryDocument1.setFinancialReportingGroup("US");
    deliveryDocument1.setPoDCNumber("06938");
    deliveryDocument1.setPurchaseCompanyId("1");
    deliveryDocument1.setPurchaseReferenceLegacyType("33");
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

  public static List<DeliveryDocument> getDeliveryDocumentsForPOCONisDAFreight() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();

    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setPurchaseRefType("CROSSU");
    deliveryDocumentLine.setIsConveyable(Boolean.TRUE);
    deliveryDocumentLine.setActiveChannelMethods(Collections.singletonList("CROSSU"));

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    return deliveryDocuments;
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForPOCONFreight() {
    DeliveryDocument deliveryDocument = new DeliveryDocument();

    DeliveryDocumentLine deliveryDocumentLine1 = new DeliveryDocumentLine();
    deliveryDocumentLine1.setPurchaseRefType("POCON");

    DeliveryDocumentLine deliveryDocumentLine2 = new DeliveryDocumentLine();
    deliveryDocumentLine2.setPurchaseRefType("POCON");

    List<DeliveryDocumentLine> deliveryDocumentLines = new ArrayList<>();
    deliveryDocumentLines.add(deliveryDocumentLine1);
    deliveryDocumentLines.add(deliveryDocumentLine2);
    deliveryDocument.setDeliveryDocumentLines(deliveryDocumentLines);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    return deliveryDocuments;
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

  public static ContainerDetails getContainerDetailsOldPrintForPOCON() {
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

  public static ContainerDetails getContainerDetailsforPOCON() {
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

  public static InstructionRequest getMultiPoInstructionRequestWithDeliveryDocumentsForPoCon() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("8e1df00-ebf6-11e8-9c25-dd4dfgc2a06f9");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("21231314");
    instructionRequest.setDoorNumber("123");
    instructionRequest.setAsnBarcode(null);
    instructionRequest.setProblemTagId(null);
    instructionRequest.setUpcNumber("00016017039635");
    instructionRequest.setIsPOCON(Boolean.TRUE);
    instructionRequest.setNonNationPo("POCON");

    DeliveryDocument deliveryDocument = new DeliveryDocument();
    deliveryDocument.setBaseDivisionCode("WM");
    deliveryDocument.setDeptNumber("14");
    deliveryDocument.setFinancialReportingGroup("US");
    deliveryDocument.setPoDCNumber("06999");
    deliveryDocument.setPurchaseCompanyId("1");
    deliveryDocument.setPurchaseReferenceLegacyType("33");
    deliveryDocument.setVendorNumber("482497180");
    deliveryDocument.setPurchaseReferenceNumber("4763030227");
    deliveryDocument.setQuantity(5);
    deliveryDocument.setTotalPurchaseReferenceQty(10);
    deliveryDocument.setPalletQty(2);
    deliveryDocument.setReceivedPalletCount(1);

    DeliveryDocument deliveryDocument2 = new DeliveryDocument();
    deliveryDocument2.setBaseDivisionCode("WM");
    deliveryDocument2.setDeptNumber("14");
    deliveryDocument2.setFinancialReportingGroup("US");
    deliveryDocument2.setPoDCNumber("06999");
    deliveryDocument2.setPurchaseCompanyId("1");
    deliveryDocument2.setPurchaseReferenceLegacyType("33");
    deliveryDocument2.setVendorNumber("482497180");
    deliveryDocument2.setPurchaseReferenceNumber("4763030337");
    deliveryDocument2.setQuantity(15);
    deliveryDocument2.setTotalPurchaseReferenceQty(60);
    deliveryDocument2.setPalletQty(5);
    deliveryDocument2.setReceivedPalletCount(1);

    List<DeliveryDocument> deliveryDocuments = new ArrayList<>();
    deliveryDocuments.add(deliveryDocument);
    deliveryDocuments.add(deliveryDocument2);
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    return instructionRequest;
  }

  public static List<ContainerItem> populateDataInContainerItemTable() {
    List<ContainerItem> containerItemList = new ArrayList<ContainerItem>();
    ContainerItem containerItem;
    containerItem = new ContainerItem();

    containerItem.setItemNumber(33333388l);

    containerItemList.add(containerItem);
    return containerItemList;
  }

  public static List<DcFinReconciledDate> populateDcFinReconciledDateData() {
    DcFinReconciledDate dcFinReconciledDate = new DcFinReconciledDate();
    List<DcFinReconciledDate> dcFinReconciledDates = new ArrayList<>();
    dcFinReconciledDate.setContainerId("a32L8990000000000000106519");
    dcFinReconciledDate.setDeliveryNum(1l);
    dcFinReconciledDates.add(dcFinReconciledDate);

    return dcFinReconciledDates;
  }

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
    httpHeaders.add(ReceivingConstants.WMT_PRODUCT_NAME, ReceivingConstants.APP_NAME_VALUE);
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

  public static Instruction getInstructionResponse() throws IOException {
    File resource = new ClassPathResource("GetInstructionResponse.json").getFile();
    String mockInstructionResponse = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    return instruction;
  }

  public static Instruction getInstructionResponse_QtyReceiving() throws IOException {
    File resource =
        new ClassPathResource("InstructionResponseForQtyReceivingLabels.json").getFile();
    String mockInstructionResponse = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    return instruction;
  }

  public static Instruction getInstructionResponseWithMultipleLabels() throws IOException {
    File resource = new ClassPathResource("GetInstructionResponseList.json").getFile();
    String mockInstructionResponse = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    return instruction;
  }

  public static List<Instruction> getMultipleInstructionResponseWithMultipleLabels()
      throws IOException {
    List<Instruction> instructions = new ArrayList<>();
    File resource = new ClassPathResource("GetMultipleInstructionResponse.json").getFile();
    String mockInstructionResponse = new String(Files.readAllBytes(resource.toPath()));
    Instruction instruction1 = new Gson().fromJson(mockInstructionResponse, Instruction.class);
    File resource1 = new ClassPathResource("GetInstructionResponseList.json").getFile();
    String mockInstructionResponse1 = new String(Files.readAllBytes(resource1.toPath()));
    Instruction instruction2 = new Gson().fromJson(mockInstructionResponse1, Instruction.class);
    instructions.add(instruction1);
    instructions.add(instruction2);
    return instructions;
  }

  public static String getMockDeliveryDocument() {
    String mockDeliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"6638945111\",\n"
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
            + "            \"purchaseReferenceNumber\": \"6638945111\",\n"
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
            + "            \"openQty\": 499,\n"
            + "            \"expectedQty\": 500,\n"
            + "            \"overageQtyLimit\": 20,\n"
            + "            \"itemNbr\": 9745387,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 5,\n"
            + "            \"palletHi\": 5,\n"
            + "            \"vnpkWgtQty\": 10.0,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.852,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"8DAYS\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"Ice Cream\",\n"
            + "            \"itemDescription2\": \"\\u003cT\\u0026S\\u003e\",\n"
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
            + "                \"warehouseRotationTypeCode\": \"3\",\n"
            + "                \"recall\": false,\n"
            + "                \"weight\": 3.325,\n"
            + "                \"weightFormatTypeCode\": \"F\",\n"
            + "                \"omsWeightFormatTypeCode\": \"F\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "                \"isHACCP\": true,\n"
            + "                \"primeSlotSize\": 0,\n"
            + "                \"isHazardous\": 0,\n"
            + "                \"atlasConvertedItem\": false,\n"
            + "                \"isWholesaler\": false\n"
            + "            },\n"
            + "            \"department\": \"98\",\n"
            + "            \"vendorStockNumber\": \"11357\",\n"
            + "            \"maxAllowedOverageQtyIncluded\": false,\n"
            + "            \"lithiumIonVerificationRequired\": false,\n"
            + "            \"limitedQtyVerificationRequired\": false,\n"
            + "            \"newItem\": false\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 243,\n"
            + "    \"weight\": 0.0,\n"
            + "    \"cubeQty\": 0.0,\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"WRK\",\n"
            + "    \"poTypeCode\": 28,\n"
            + "    \"totalBolFbq\": 0,\n"
            + "    \"deliveryLegacyStatus\": \"WRK\",\n"
            + "    \"purchaseReferenceMustArriveByDate\": \"Nov 27, 2019 6:00:00 PM\",\n"
            + "    \"stateReasonCodes\": [\n"
            + "        \"WORKING\"\n"
            + "    ],\n"
            + "    \"deliveryNumber\": 12345678,\n"
            + "    \"importInd\": false\n"
            + "}";

    return mockDeliveryDocument;
  }

  public static String getMockDeliveryDocumentConvertedItemGDC() {
    String mockDeliveryDocument =
        "{\n"
            + "    \"purchaseReferenceNumber\": \"6638945111\",\n"
            + "    \"financialGroupCode\": \"US\",\n"
            + "    \"baseDivCode\": \"WM\",\n"
            + "    \"vendorNumber\": \"480889\",\n"
            + "    \"vendorNbrDeptSeq\": 480889940,\n"
            + "    \"deptNumber\": \"94\",\n"
            + "    \"purchaseCompanyId\": \"1\",\n"
            + "    \"purchaseReferenceLegacyType\": \"28\",\n"
            + "    \"poDCNumber\": \"32612\",\n"
            + "    \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "    \"fromSubCenterId\": \"2\",\n"
            + "    \"deliveryDocumentLines\": [\n"
            + "        {\n"
            + "            \"gtin\": \"01123840356119\",\n"
            + "            \"itemUPC\": \"01123840356119\",\n"
            + "            \"caseUPC\": \"11188122713797\",\n"
            + "            \"purchaseReferenceNumber\": \"6638945111\",\n"
            + "            \"purchaseReferenceLineNumber\": 1,\n"
            + "            \"event\": \"POS REPLEN\",\n"
            + "            \"purchaseReferenceLineStatus\": \"ACTIVE\",\n"
            + "            \"whpkSell\": 23.89,\n"
            + "            \"vendorPackCost\": 23.89,\n"
            + "            \"fromOrgUnitId\": 2,\n"
            + "            \"vnpkQty\": 1,\n"
            + "            \"whpkQty\": 1,\n"
            + "            \"orderableQuantity\": 1,\n"
            + "            \"warehousePackQuantity\": 1,\n"
            + "            \"expectedQtyUOM\": \"ZA\",\n"
            + "            \"openQty\": 499,\n"
            + "            \"expectedQty\": 500,\n"
            + "            \"overageQtyLimit\": 20,\n"
            + "            \"itemNbr\": 9745387,\n"
            + "            \"purchaseRefType\": \"SSTKU\",\n"
            + "            \"palletTi\": 5,\n"
            + "            \"palletHi\": 5,\n"
            + "            \"vnpkWgtQty\": 10.0,\n"
            + "            \"vnpkWgtUom\": \"LB\",\n"
            + "            \"vnpkcbqty\": 0.852,\n"
            + "            \"vnpkcbuomcd\": \"CF\",\n"
            + "            \"color\": \"8DAYS\",\n"
            + "            \"size\": \"EA\",\n"
            + "            \"isHazmat\": false,\n"
            + "            \"itemDescription1\": \"Ice Cream\",\n"
            + "            \"itemDescription2\": \"\\u003cT\\u0026S\\u003e\",\n"
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
            + "                \"warehouseRotationTypeCode\": \"3\",\n"
            + "                \"recall\": false,\n"
            + "                \"weight\": 3.325,\n"
            + "                \"weightFormatTypeCode\": \"F\",\n"
            + "                \"omsWeightFormatTypeCode\": \"F\",\n"
            + "                \"weightUOM\": \"LB\",\n"
            + "                \"warehouseMinLifeRemainingToReceive\": 30,\n"
            + "                \"isHACCP\": true,\n"
            + "                \"primeSlotSize\": 0,\n"
            + "                \"isHazardous\": 0,\n"
            + "                \"atlasConvertedItem\": true,\n"
            + "                \"isWholesaler\": false\n"
            + "            },\n"
            + "            \"department\": \"98\",\n"
            + "            \"vendorStockNumber\": \"11357\",\n"
            + "            \"maxAllowedOverageQtyIncluded\": false,\n"
            + "            \"lithiumIonVerificationRequired\": false,\n"
            + "            \"limitedQtyVerificationRequired\": false,\n"
            + "            \"fromPoLineDCNumber\": 32987,\n"
            + "            \"newItem\": false\n"
            + "        }\n"
            + "    ],\n"
            + "    \"totalPurchaseReferenceQty\": 243,\n"
            + "    \"weight\": 0.0,\n"
            + "    \"cubeQty\": 0.0,\n"
            + "    \"freightTermCode\": \"PRP\",\n"
            + "    \"deliveryStatus\": \"WRK\",\n"
            + "    \"poTypeCode\": 28,\n"
            + "    \"totalBolFbq\": 0,\n"
            + "    \"deliveryLegacyStatus\": \"WRK\",\n"
            + "    \"purchaseReferenceMustArriveByDate\": \"Nov 27, 2019 6:00:00 PM\",\n"
            + "    \"stateReasonCodes\": [\n"
            + "        \"WORKING\"\n"
            + "    ],\n"
            + "    \"deliveryNumber\": 12345678,\n"
            + "    \"importInd\": false\n"
            + "}";

    return mockDeliveryDocument;
  }

  public static ProblemLabel getMockProblemLabel() {
    ProblemLabel problemLabel = new ProblemLabel();
    problemLabel.setProblemTagId("32612760672009");
    problemLabel.setResolutionId("00a97ea8-dbb9-461e-9a95-1b6407150ba5");
    String problemResponse =
        "{\"id\":\"37d95a49-9701-464e-baba-eec28fc19e87\",\"label\":\"32612760672009\",\"slot\":\"PA02\",\"status\":\"OPEN\",\"remainingQty\":3,\"reportedQty\":3,\"issue\":{\"id\":\"fdcf2473-1134-4cf9-a5ac-214ec27cef08\",\"identifier\":\"211117-67194-1346-0000\",\"type\":\"NIS\",\"deliveryNumber\":\"22223334\",\"upc\":\"618842355167\",\"itemNumber\":564040492,\"quantity\":3,\"status\":\"ANSWERED\",\"businessStatus\":\"READY_TO_RECEIVE\",\"resolutionStatus\":\"COMPLETE_RESOLUTON\"},\"resolutions\":[{\"id\":\"00a97ea8-dbb9-461e-9a95-1b6407150ba5\",\"provider\":\"Manual\",\"quantity\":3,\"acceptedQuantity\":0,\"rejectedQuantity\":0,\"remainingQty\":3,\"type\":\"RECEIVE_AGAINST_ORIGINAL_LINE\",\"resolutionPoNbr\":\"1146844937\",\"resolutionPoLineNbr\":2,\"state\":\"OPEN\"}]}";
    problemLabel.setProblemResponse(problemResponse);

    return problemLabel;
  }

  public static Instruction getCompleteInstructionWithProblemTagId() {
    Instruction instruction = getInstruction();
    instruction.setProblemTagId("43232342");
    instruction.setCompleteTs(new Date());
    return instruction;
  }

  // Regulated Item Validation
  public static InstructionRequest getInstructionRequestForVendorComplianceReqCheck() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("f1fbf698-5d5c-4cf1-bb58-9810d44d28d7");
    instructionRequest.setDeliveryStatus(DeliveryStatus.OPN.toString());
    instructionRequest.setDeliveryNumber("56212116");
    instructionRequest.setDoorNumber("956");
    instructionRequest.setUpcNumber("00833562002454");
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setReceivingType(ReceivingConstants.UPC);

    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLimitedQtyComplianceWithDeliveryDocumentForSinglePO()
          throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_SinglePO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLimitedQty());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLimitedQtyComplianceWithDeliveryDocumentForMultiPO()
          throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_MultiPO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLimitedQty());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLimitedQtyComplianceWithoutDeliveryDocument() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    instructionRequest.setRegulatedItemType(VendorCompliance.LIMITED_QTY);
    return instructionRequest;
  }

  public static List<TransportationModes> getMockTransportationModeForLimitedQty() {
    TransportationModes transportationModes = new TransportationModes();
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("ORM-D");
    Mode mode = new Mode();
    mode.setCode(1);
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);

    return Arrays.asList(transportationModes);
  }

  public static InstructionRequest
      getInstructionRequestWithLithiumIonComplianceWithDocumentForSinglePO() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_SinglePO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIon());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLithiumIonComplianceWithDocumentForMultiPO() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_MultiPO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIon());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);
    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestWithLithiumIonComplianceWithoutDocument()
      throws IOException {
    InstructionRequest instructionRequest = getInstructionRequestForVendorComplianceReqCheck();
    instructionRequest.setDeliveryDocuments(null);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLithiumIonAndLimitedQtyComplianceForSinglePO() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_SinglePO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIonAndLimitedQtyItem());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY);
    return instructionRequest;
  }

  public static InstructionRequest
      getInstructionRequestWithLithiumIonAndLimitedQtyComplianceForMultiPO() throws IOException {
    InstructionRequest instructionRequest = getInstructionRequest();
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_MultiPO.json")
            .getFile();
    String mockDeliveryDocumentsResponse = new String(Files.readAllBytes(resource.toPath()));
    List<DeliveryDocument> deliveryDocuments =
        gson.fromJson(
            mockDeliveryDocumentsResponse, new TypeToken<List<DeliveryDocument>>() {}.getType());
    deliveryDocuments
        .get(0)
        .getDeliveryDocumentLines()
        .get(0)
        .setTransportationModes(getMockTransportationModeForLithiumIonAndLimitedQtyItem());
    instructionRequest.setDeliveryDocuments(deliveryDocuments);
    instructionRequest.setRegulatedItemType(VendorCompliance.LITHIUM_ION_AND_LIMITED_QUANTITY);
    return instructionRequest;
  }

  public static List<TransportationModes> getMockTransportationModeForLithiumIon() {
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("N/A");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("965"));
    transportationModes.setMode(mode);

    return Arrays.asList(transportationModes);
  }

  public static List<TransportationModes>
      getMockTransportationModeForLithiumIonAndLimitedQtyItem() {
    TransportationModes transportationModes = new TransportationModes();
    Mode mode = new Mode();
    mode.setCode(1);
    DotHazardousClass dotHazardousClass = new DotHazardousClass();
    dotHazardousClass.setCode("LTD-Q");
    transportationModes.setDotHazardousClass(dotHazardousClass);
    transportationModes.setMode(mode);
    transportationModes.setDotRegionCode(null);
    transportationModes.setDotIdNbr(null);
    transportationModes.setLimitedQty(null);
    transportationModes.setProperShipping("Lithium Ion Battery Packed with Equipment");
    transportationModes.setPkgInstruction(Arrays.asList("970"));
    transportationModes.setMode(mode);

    return Arrays.asList(transportationModes);
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForMultiPO() throws IOException {
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_MultiPO.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static List<DeliveryDocument> getDeliveryDocumentsForSinglePO() throws IOException {
    File resource =
        new ClassPathResource("Gdm_LithiumIon_LimitedQty_Vendor_Verification_Req_SinglePO.json")
            .getFile();
    String mockResponse = new String(Files.readAllBytes(resource.toPath()));
    return new ArrayList<>(Arrays.asList(gson.fromJson(mockResponse, DeliveryDocument[].class)));
  }

  public static DocumentLine getMockDocumentLine() {
    DocumentLine documentLine = new DocumentLine();
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
    return documentLine;
  }
}
