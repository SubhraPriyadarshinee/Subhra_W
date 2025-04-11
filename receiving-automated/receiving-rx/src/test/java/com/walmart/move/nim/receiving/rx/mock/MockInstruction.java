package com.walmart.move.nim.receiving.rx.mock;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Collections.singletonList;

import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.entity.PrintJob;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.GtinHierarchy;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingDivertLocations;
import com.walmart.move.nim.receiving.core.model.slotting.SlottingPalletResponse;
import com.walmart.move.nim.receiving.data.GdcHttpHeaders;
import com.walmart.move.nim.receiving.data.MockHttpHeaders;
import com.walmart.move.nim.receiving.rx.model.RxInstructionType;
import com.walmart.move.nim.receiving.rx.model.RxReceivingType;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;

public class MockInstruction {

  public static Container mockResponseForGetParentContainer(
          String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackingId);
    container.setChildContainers(mockResponseForGetContainerIncludesChildren(trackingId));
    container.setInstructionId(1L);
    container.setContainerItems(Arrays.asList(MockInstruction.getContainerItem()));
    container.setSsccNumber("test_sscc");
    HashMap<String, Object> map = new HashMap<>();
    map.put("instructionCode", "RxSerBuildUnitScan");
    container.setContainerMiscInfo(map);
    return container;
  }

  public static SlottingPalletResponse mockSlottingPalletResponse() {

    SlottingDivertLocations location = new SlottingDivertLocations();
    location.setType("success");
    location.setLocation("A1234");
    location.setItemNbr(579516308);
    location.setAsrsAlignment("SYM2");
    location.setSlotType(ReceivingConstants.PRIME_SLOT_TYPE);
    List<SlottingDivertLocations> locationList = new ArrayList();
    locationList.add(location);

    SlottingPalletResponse mockSlottingResponseBody = new SlottingPalletResponse();
    mockSlottingResponseBody.setMessageId("a1-b1-c1");
    mockSlottingResponseBody.setLocations(locationList);
    return mockSlottingResponseBody;
  }

  public static DeliveryDocumentLine selectDocumentAndDocumentLine(DeliveryDocument selectedDocument) {
    List<DeliveryDocumentLine> deliveryDocumentLines = selectedDocument.getDeliveryDocumentLines();
    return deliveryDocumentLines
            .stream()
            .sorted(
                    Comparator.comparing(DeliveryDocumentLine::getPurchaseReferenceNumber)
                            .thenComparing(DeliveryDocumentLine::getPurchaseReferenceLineNumber))
            .collect(Collectors.toList())
            .get(0);
  }

  private static Container createChildContainer(String parentTrackingId, String trackingId, int quantity) {
    Container container = new Container();
    container.setDeliveryNumber(12345l);
    container.setTrackingId(trackingId);
    container.setParentTrackingId(parentTrackingId);

    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId(trackingId);
    containerItem.setPurchaseReferenceNumber("987654321");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantity(quantity);
    containerItem.setVnpkQty(6);
    containerItem.setWhpkQty(6);

    HashMap<String, Object> map = new HashMap<String, Object>();
    map.put(KEY_SSCC, "test_sscc");
    map.put(KEY_GTIN, "test_gtin");
    map.put(KEY_SERIAL, trackingId.toString());
    map.put(KEY_LOT, "test_lot");
    container.setContainerMiscInfo(map);

    container.setContainerItems(Arrays.asList(containerItem));

    return container;
  }

  private static Set<Container> mockResponseForGetContainerIncludesChildren(String trackingId) {
    Container childContainer1 = createChildContainer("12345", "123", 6);
    Container childContainer2 = createChildContainer("12345", "456", 6);
    Set<Container> childContainers = new HashSet<>();
    childContainers.add(childContainer1);
    childContainers.add(childContainer2);
    return childContainers;
  }

  public static DeliveryDocumentLine getMockDeliveryDocumentLine() {
    DeliveryDocumentLine deliveryDocumentLine = new DeliveryDocumentLine();
    deliveryDocumentLine.setVendorPack(12);
    deliveryDocumentLine.setWarehousePack(2);
    ItemData additionalData = new ItemData();
    additionalData.setAtlasConvertedItem(false);
    additionalData.setPackTypeCode("B");
    additionalData.setHandlingCode("C");
    additionalData.setItemPackAndHandlingCode("BC");
    additionalData.setItemHandlingMethod("Breakpack Conveyable");
    deliveryDocumentLine.setAdditionalInfo(additionalData);
    deliveryDocumentLine.setItemNbr(34533232L);
    return deliveryDocumentLine;
  }
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

  public static LinkedTreeMap<String, Object> getMoveData_primeSlot() {
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");
    move.put("primeLocation", "A1234");
    move.put("primeLocationSize", 72);
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
    instruction.setInstructionMsg("Build Container");
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
    instruction.setProjectedReceiveQtyUOM(ReceivingConstants.Uom.VNPK);
    instruction.setReceivedQuantityUOM(ReceivingConstants.Uom.VNPK);

    return instruction;
  }

  public static Instruction getOpenInstruction_smartPrimeSlot() {
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
    instruction.setInstructionMsg("Build Container");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setActivityName("DA");
    instruction.setMove(getMoveData_primeSlot());
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

  public static Instruction getRxCompleteInstruction() throws Exception {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("1"));

    File resource = new ClassPathResource("completeInstructions_container.json").getFile();
    String containerData = new String(Files.readAllBytes(resource.toPath()));

    instruction.setContainer(new Gson().fromJson(containerData, ContainerDetails.class));
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
    container.setOrgUnitId("1");
    container.setPublishTs(new Date());
    container.setCreateTs(new Date());
    container.setCreateUser("sysadmin");
    container.setLastChangedTs(new Date());
    container.setLastChangedUser("sysadmin");
    container.setContainerItems(null);

    ContainerItem containerItem = getContainerItem();
    ContainerItem containerItem_2 = getContainerItem_2();
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
    // childContainer1.setOrgUnitId(1);
    childContainer1.setCompleteTs(new Date());
    childContainer1.setPublishTs(new Date());
    childContainer1.setCreateTs(new Date());
    childContainer1.setCreateUser("sysadmin");
    childContainer1.setLastChangedTs(new Date());
    childContainer1.setLastChangedUser("sysadmin");
    childContainer1.setContainerItems(Arrays.asList(containerItem_2));
    childContainer1.setContainerMiscInfo(Collections.emptyMap());
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
    // childContainer2.setOrgUnitId("");
    childContainer2.setCompleteTs(new Date());
    childContainer2.setPublishTs(new Date());
    childContainer2.setCreateTs(new Date());
    childContainer2.setCreateUser("sysadmin");
    childContainer2.setLastChangedTs(new Date());
    childContainer2.setLastChangedUser("sysadmin");
    childContainer2.setContainerItems(Arrays.asList(containerItem));
    childContainer2.setContainerMiscInfo(Collections.emptyMap());
    childContainer2.setSsccNumber("123456789");
    childContainer2.setGtin("34567890000O");
    childContainer2.setSerial("WERTYU4333");


    Container childContainer3 = new Container();
    childContainer3.setTrackingId("123_abc124");
    childContainer3.setParentTrackingId("a328990000000000000106509");
    childContainer3.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    childContainer3.setCompleteTs(new Date());
    childContainer3.setLocation("123L");
    childContainer3.setDeliveryNumber(Long.valueOf(12342));
    childContainer3.setFacility(getDestination());
    childContainer3.setDestination(getDestination());
    childContainer3.setContainerType("Vendor Pack");
    childContainer3.setContainerStatus("");
    childContainer3.setWeight(5F);
    childContainer3.setWeightUOM("EA");
    childContainer3.setCube(2F);
    childContainer3.setCubeUOM("EA");
    childContainer3.setCtrShippable(true);
    childContainer3.setCtrShippable(false);
    childContainer3.setInventoryStatus("Picked");
    childContainer3.setOrgUnitId("");
    childContainer3.setCompleteTs(new Date());
    childContainer3.setPublishTs(new Date());
    childContainer3.setCreateTs(new Date());
    childContainer3.setCreateUser("sysadmin");
    childContainer3.setLastChangedTs(new Date());
    childContainer3.setLastChangedUser("sysadmin");
    childContainer3.setContainerItems(Arrays.asList(containerItem));
    childContainer3.setContainerMiscInfo(Collections.emptyMap());
    childContainer3.setSsccNumber("123456789");
    childContainer3.setGtin("34567890000O");
    childContainer3.setSerial("WERTYU4333");


    childContainer2.setContainerMiscInfo(Collections.emptyMap());
    childContainer2.setContainerMiscInfo(Collections.emptyMap());

    container.setChildContainers(
        Stream.of(childContainer1, childContainer2, childContainer3).collect(Collectors.toCollection(HashSet::new)));

    return container;
  }

  public static ContainerItem getContainerItem() {
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
    containerItem.setVnpkQty(10);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setQuantityUOM("EA");
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(null);
    containerItem.setDistributions(getDistributions());
    containerItem.setLotNumber("ADC8908A");
    containerItem.setRotateDate(new Date());
    containerItem.setExpiryDate(new Date());
    return containerItem;
  }

  public static ContainerItem getContainerItem_2() {
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
    containerItem.setVnpkQty(10);
    containerItem.setWhpkQty(1);
    containerItem.setQuantity(1);
    containerItem.setQuantityUOM("EA");
    containerItem.setVendorPackCost(25.0);
    containerItem.setWhpkSell(25.0);
    containerItem.setBaseDivisionCode("VM");
    containerItem.setFinancialReportingGroupCode("US");
    containerItem.setRotateDate(null);
    containerItem.setDistributions(getDistributions());
    containerItem.setLotNumber("ADC8908AA");
    containerItem.setRotateDate(new Date());
    containerItem.setExpiryDate(new Date());
    return containerItem;
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
    instruction.setReceivedQuantity(2);
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

  public static Instruction getInstructionWithManufactureDetails() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2076"));
    instruction.setContainer(getContainerData());

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setWarehousePack(6);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setParentTrackingId(instruction.getContainer().getTrackingId());
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setVendorPack(1);
    content2.setRotateDate("2020-12-12");
    contents2.add(content2);
    content2.setQtyUom(ReceivingConstants.Uom.WHPK);
    content2.setWarehousePack(6);
    content2.setQty(1);

    containerDetails2.setParentTrackingId(instruction.getContainer().getTrackingId());
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);
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
            + "      \"shipmentDetailsList\": [{\n"
            + "        \"inboundShipmentDocId\": \"546191213_20191106_719468_VENDOR_US\",\n"
            + "        \"shipmentNumber\": \"546191213\",\n"
            + "        \"loadNumber\": \"88528711\",\n"
            + "        \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "        \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "      }],\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"gtin\": \"00029695410987\",\n"
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
            + "      \"lotNumber\": \"ABCDEF1234\",\n"
            + "        \"shipmentNumber\": \"546191213\",\n"
            + "      \"shipmentDetailsList\": [{\n"
            + "        \"inboundShipmentDocId\": \"546191213_20191106_719468_VENDOR_US\",\n"
            + "        \"shipmentNumber\": \"546191213\",\n"
            + "        \"loadNumber\": \"88528711\",\n"
            + "        \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "        \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "      }],\n"
            + "          \"gtinHierarchy\": [\n"
            + "                                {\n"
            + "                                    \"gtin\": \"10368645556540\",\n"
            + "                                    \"type\": \"consumableGTIN\"\n"
            + "                                }],\n"
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

  public static Instruction getInstructionV2(String instructionCode) {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2076"));
    instruction.setContainer(getContainerData());
    instruction.setReceivingMethod("FULL-PALLET");
    instruction.setInstructionCode(instructionCode);
    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setWarehousePack(6);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setParentTrackingId(instruction.getContainer().getTrackingId());
    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setVendorPack(1);
    content2.setRotateDate("2020-12-12");
    contents2.add(content2);
    content2.setQtyUom(ReceivingConstants.Uom.WHPK);
    content2.setWarehousePack(6);
    content2.setQty(1);

    containerDetails2.setParentTrackingId(instruction.getContainer().getTrackingId());
    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);
    instruction.setDeliveryNumber(Long.parseLong("798001"));
    instruction.setGtin("00028000114603");
    instruction.setInstructionMsg(instructionCode);
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
           "{\n" +
                   "  \"purchaseReferenceNumber\": \"3132876704\",\n" +
                   "  \"financialGroupCode\": \"US\",\n" +
                   "  \"baseDivCode\": \"WM\",\n" +
                   "  \"vendorNumber\": \"988246\",\n" +
                   "  \"deptNumber\": \"38\",\n" +
                   "  \"purchaseCompanyId\": \"1\",\n" +
                   "  \"purchaseReferenceLegacyType\": \"20\",\n" +
                   "  \"poDCNumber\": \"6032\",\n" +
                   "  \"purchaseReferenceStatus\": \"ACTV\",\n" +
                   "  \"deliveryDocumentLines\": [\n" +
                   "    {\n" +
                   "      \"gtin\": \"30369543123500\",\n" +
                   "      \"itemUPC\": \"30369543123500\",\n" +
                   "      \"caseUPC\": \"30369543123500\",\n" +
                   "      \"purchaseReferenceNumber\": \"3132876704\",\n" +
                   "      \"purchaseReferenceLineNumber\": 1,\n" +
                   "      \"event\": \"POS REPLEN\",\n" +
                   "      \"purchaseReferenceLineStatus\": \"ACTIVE\",\n" +
                   "      \"whpkSell\": 4.59,\n" +
                   "      \"vendorPackCost\": 1.84,\n" +
                   "      \"vnpkQty\": 10000,\n" +
                   "      \"whpkQty\": 500,\n" +
                   "      \"orderableQuantity\": 10000,\n" +
                   "      \"warehousePackQuantity\": 500,\n" +
                   "      \"expectedQtyUOM\": \"EA\",\n" +
                   "      \"expectedQty\": 40000,\n" +
                   "      \"overageQtyLimit\": 0,\n" +
                   "      \"itemNbr\": 595620918,\n" +
                   "      \"purchaseRefType\": \"SSTKU\",\n" +
                   "      \"vnpkWgtQty\": 16.8,\n" +
                   "      \"vnpkWgtUom\": \"LB\",\n" +
                   "      \"vnpkcbqty\": 2.5,\n" +
                   "      \"vnpkcbuomcd\": \"CF\",\n" +
                   "      \"color\": \"TABLET\",\n" +
                   "      \"size\": \"500\",\n" +
                   "      \"itemDescription1\": \"GLIMEPIRIDE 1MG\",\n" +
                   "      \"promoBuyInd\": \"\",\n" +
                   "      \"additionalInfo\": {\n" +
                   "        \"warehouseGroupCode\": \"\",\n" +
                   "        \"isNewItem\": false,\n" +
                   "        \"weight\": 0.2,\n" +
                   "        \"weightFormatTypeCode\": \"F\",\n" +
                   "        \"weightUOM\": \"LB\",\n" +
                   "        \"warehouseMinLifeRemainingToReceive\": 0,\n" +
                   "        \"isDscsaExemptionInd\": false,\n" +
                   "        \"isTemperatureSensitive\": false,\n" +
                   "        \"isControlledSubstance\": false,\n" +
                   "        \"isHACCP\": false,\n" +
                   "        \"primeSlotSize\": 0,\n" +
                   "        \"handlingCode\": \"C\",\n" +
                   "        \"isHazardous\": 0,\n" +
                   "        \"atlasConvertedItem\": false,\n" +
                   "        \"isWholesaler\": false,\n" +
                   "        \"isDefaultTiHiUsed\": false,\n" +
                   "        \"qtyValidationDone\": true,\n" +
                   "        \"isEpcisEnabledVendor\": true,\n" +
                   "        \"auditQty\": 2,\n" +
                   "        \"attpQtyInEaches\": 20000,\n" +
                   "        \"auditCompletedQty\": 0,\n" +
                   "        \"scannedCaseAttpQty\": 0,\n" +
                   "        \"skipEvents\": false,\n" +
                   "        \"partialPallet\": false,\n" +
                   "        \"multiPOPallet\": false,\n" +
                   "        \"isCompliancePack\": false,\n" +
                   "        \"skipUnitEvents\": false,\n" +
                   "        \"partOfMultiSkuPallet\": false,\n" +
                   "        \"packCountInEaches\": 0,\n" +
                   "        \"autoSwitchEpcisToAsn\": false,\n" +
                   "        \"palletFlowInMultiSku\": false,\n" +
                   "        \"isEpcisLongTermEnabled\": true\n" +
                   "      },\n" +
                   "      \"department\": \"38\",\n" +
                   "      \"ndc\": \"69543-0123-50\",\n" +
                   "      \"shipmentDetailsList\": [\n" +
                   "        {\n" +
                   "          \"inboundShipmentDocId\": \"3071999242454947_20191106_719468_VENDOR_US\",\n" +
                   "          \"shipmentNumber\": \"3071999242454947\",\n" +
                   "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n" +
                   "          \"shippedQty\": 40000,\n" +
                   "          \"shippedQtyUom\": \"EA\"\n" +
                   "        }\n" +
                   "      ],\n" +
                   "      \"deptNumber\": \"38\",\n" +
                   "      \"vendorNbrDeptSeq\": 988246,\n" +
                   "      \"vendorStockNumber\": \"69543-0123-50\",\n" +
                   "      \"shippedQty\": 40000,\n" +
                   "      \"shippedQtyUom\": \"EA\",\n" +
                   "      \"warehousePackGtin\": \"10369543123506\",\n" +
                   "      \"consumableGTIN\": \"00369543123509\",\n" +
                   "      \"orderableGTIN\": \"30369543123500\",\n" +
                   "      \"maxAllowedOverageQtyIncluded\": false,\n" +
                   "      \"isTemperatureSensitive\": false,\n" +
                   "      \"isControlledSubstance\": false,\n" +
                   "      \"lithiumIonVerificationRequired\": false,\n" +
                   "      \"limitedQtyVerificationRequired\": false,\n" +
                   "      \"gtinHierarchy\": [\n" +
                   "        { \"gtin\": \"00369543123509\", \"type\": \"consumableGTIN\" },\n" +
                   "        { \"gtin\": \"30369543123500\", \"type\": \"orderableGTIN\" },\n" +
                   "        { \"gtin\": \"10369543123506\", \"type\": \"warehousePackGtin\" },\n" +
                   "        { \"type\": \"catalogGTIN\" },\n" +
                   "        { \"gtin\": \"30369543123500\", \"type\": \"gtin\" }\n" +
                   "      ],\n" +
                   "      \"isNewItem\": false,\n" +
                   "      \"autoPopulateReceivingQty\": false,\n" +
                   "      \"complianceItem\": false\n" +
                   "    }\n" +
                   "  ],\n" +
                   "  \"weight\": 0.0,\n" +
                   "  \"cubeQty\": 0.0,\n" +
                   "  \"deliveryStatus\": \"WRK\",\n" +
                   "  \"poTypeCode\": 20,\n" +
                   "  \"totalBolFbq\": 4,\n" +
                   "  \"deliveryLegacyStatus\": \"WRK\",\n" +
                   "  \"purchaseReferenceMustArriveByDate\": \"Mar 26, 2019 12:00:00 AM\",\n" +
                   "  \"deliveryNumber\": 16860944,\n" +
                   "  \"trailerId\": \"T16860944\",\n" +
                   "  \"gdmCurrentNodeDetail\": {\n" +
                   "    \"containers\": [\n" +
                   "      {\n" +
                   "        \"id\": \"19899833_001215477788384931\",\n" +
                   "        \"shipmentDocumentId\": \"12243245_0012154777384931\",\n" +
                   "        \"shipmentNumber\": \"3071999242454947\",\n" +
                   "        \"sscc\": \"001215477788384931\",\n" +
                   "        \"unitCount\": 40.0,\n" +
                   "        \"childCount\": 2.0,\n" +
                   "        \"receivingStatus\": \"Open\",\n" +
                   "        \"trackingStatus\": \"ValidationSuccessful\",\n" +
                   "        \"postingStatus\": \"Open\",\n" +
                   "        \"isQueried\": false,\n" +
                   "        \"hints\": [\"CASE-PACK-ITEM\", \"SINGLE-SKU-PACKAGE\"]\n" +
                   "      }\n" +
                   "    ],\n" +
                   "    \"additionalInfo\": {\n" +
                   "      \"containers\": [\n" +
                   "        {\n" +
                   "          \"id\": \"19899833_001215477788384931\",\n" +
                   "          \"shipmentDocumentId\": \"12243245_0012154777384931\",\n" +
                   "          \"shipmentNumber\": \"3071999242454947\",\n" +
                   "          \"sscc\": \"001215477788384931\",\n" +
                   "          \"unitCount\": 40.0,\n" +
                   "          \"childCount\": 2.0,\n" +
                   "          \"receivingStatus\": \"Open\",\n" +
                   "          \"trackingStatus\": \"ValidationSuccessful\",\n" +
                   "          \"postingStatus\": \"Open\",\n" +
                   "          \"isQueried\": false,\n" +
                   "          \"hints\": [\"CASE-PACK-ITEM\", \"SINGLE-SKU-PACKAGE\"]\n" +
                   "        }\n" +
                   "      ]\n" +
                   "    }\n" +
                   "  }\n" +
                   "}\n");

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
    printRequest.put("formatName", "pallet_lpn_witron");
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

  public static InstructionRequest clientRequestForDSDCPrintJob() {
    InstructionRequest instructionRequest = new InstructionRequest();
    instructionRequest.setMessageId("d73hw3g-ebf6-11e8-9g75-dd4bf6dfa4u");
    instructionRequest.setDeliveryStatus("WRK");
    instructionRequest.setIsDSDC(true);
    instructionRequest.setDoorNumber("786");
    instructionRequest.setDeliveryNumber("89768798");

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

  public static Instruction getMockNewInstruction() {

    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("lastChangedBy", "OF-SYS");
    move.put("lastChangedOn", new Date());
    move.put("sequenceNbr", 543397582);
    move.put("containerTag", "b328990000000000000048571");
    move.put("correlationID", "98e22370-f2f0-11e8-b725-95f2a20d59c0");
    move.put("toLocation", "302");

    Map<String, Object> item = new HashMap<String, Object>();
    item.put("value", "550129241");
    item.put("key", "ITEM");

    Map<String, Object> destination = new HashMap<String, Object>();
    item.put("value", "07026 US");
    item.put("key", "DESTINATION");

    Map<String, Object> upcbar = new HashMap<String, Object>();
    item.put("value", "00016017039630");
    item.put("key", "UPCBAR");

    Map<String, Object> lpn = new HashMap<String, Object>();
    item.put("value", "2803897407964380");
    item.put("key", "LPN");

    Map<String, Object> fullUserID = new HashMap<String, Object>();
    item.put("value", "rlp004v");
    item.put("key", "FULLUSERID");

    Map<String, Object> type = new HashMap<String, Object>();
    item.put("value", "DA");
    item.put("key", "TYPE");

    Map<String, Object> desc1 = new HashMap<String, Object>();
    item.put("value", "TR 12QT STCKPT SS   ");
    item.put("key", "DESC1");

    List<Map<String, Object>> dataArrayList =
        Arrays.asList(item, destination, upcbar, lpn, fullUserID, type, desc1);

    Map<String, Object> mapCtrLabel = new HashMap<>();
    mapCtrLabel.put("ttlInHours", 72);
    mapCtrLabel.put("labelIdentifier", "a328990000000000000106509");
    mapCtrLabel.put("clientId", "OF");
    mapCtrLabel.put("clientID", "OF");
    mapCtrLabel.put("formatId", "pallet_lpn_format");
    mapCtrLabel.put("formatID", "pallet_lpn_format");
    /*
     * Both Data and label data are same value returned by OF.
     */
    mapCtrLabel.put("data", dataArrayList);
    mapCtrLabel.put("labelData", dataArrayList);

    Map<String, Object> mapCtrLabelForDACase = new HashMap<>();
    mapCtrLabelForDACase.put("ttlInHours", 72);
    mapCtrLabelForDACase.put("labelIdentifier", "2803897406677828");
    mapCtrLabelForDACase.put("clientId", "OF");
    mapCtrLabelForDACase.put("clientID", "OF");
    mapCtrLabelForDACase.put("formatId", "case_lpn_format");
    mapCtrLabelForDACase.put("formatID", "case_lpn_format");
    /*
     * Both Data and label data are same value returned by OF.
     */
    mapCtrLabelForDACase.put("data", dataArrayList);
    mapCtrLabelForDACase.put("labelData", dataArrayList);

    Map<String, String> mapCtrDestination = new HashMap<>();
    mapCtrDestination.put("countryCode", "US");
    mapCtrDestination.put("buNumber", "6012");

    Map<String, String> itemMap = new HashMap<>();
    itemMap.put("financialReportingGroup", "US");
    itemMap.put("baseDivisionCode", "WM");
    itemMap.put("itemNbr", "1084445");

    Distribution distribution1 = new Distribution();
    distribution1.setAllocQty(5);
    distribution1.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c3");
    distribution1.setItem(itemMap);

    Distribution distribution2 = new Distribution();
    distribution2.setAllocQty(5);
    distribution2.setOrderId("0bb3080c-5e62-4337-b373-9e874cc7d2c4");
    distribution2.setItem(itemMap);

    List<Distribution> distributions = new ArrayList<Distribution>();
    distributions.add(distribution1);
    distributions.add(distribution2);

    ContainerDetails containerDetails = new ContainerDetails();
    containerDetails.setOrgUnitId(1);
    containerDetails.setProjectedWeightUom(ReceivingConstants.Uom.VNPK);
    containerDetails.setProjectedWeight(20F);
    containerDetails.setTrackingId("a328990000000000000106509");
    containerDetails.setCtrType("PALLET");
    containerDetails.setInventoryStatus("PICKED");
    containerDetails.setCtrShippable(true);
    containerDetails.setCtrReusable(false);
    containerDetails.setQuantity(10);
    containerDetails.setCtrDestination(mapCtrDestination);
    containerDetails.setDistributions(distributions);
    containerDetails.setCtrLabel(mapCtrLabel);

    Instruction instruction = new Instruction();
    instruction.setId(12345l);
    instruction.setContainer(containerDetails);
    instruction.setChildContainers(null);
    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("rxTestUser");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("rxTestUser");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("Build Container");
    instruction.setInstructionMsg("Build the Container");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(move);
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(true);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(20);
    instruction.setProviderId("DA");
    instruction.setSsccNumber("0012345678890");
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
            + "                \"deptNumber\": \"D38\",\n"
            + "                \"itemDescription1\": \"LG SH BATCYCLE BATTL\",\n"
            + "                \"itemDescription2\": \"NEW F20 WK 28\",\n"
            + "                \"shipmentNumber\": \"546191213\",\n"
            + "      \"manufactureDetails\": [\n"
            + "        {\n"
            + "          \"lot\": \"ABCDEF1234\",\n"
            + "          \"qty\": \"20\",\n"
            + "          \"expiry\": \"12/30/2020\"\n"
            + "        }\n"
            + "      ]\n"
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

  public static InstructionRequest getInstructionRequestFor2dBarcodeScan() {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("00028000114603");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(
        ApplicationIdentifier.LOT.getApplicationIdentifier());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");

    scannedDataList.add(gtinScannedData);
    scannedDataList.add(lotNumberScannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    documentLine.setGtin("00028000114603");
    documentLine.setLotNumber("ABCDEF1234");
    documentLine.setDeptNumber("38");
    documentLine.setShippedQty(20);
    documentLine.setShippedQtyUom("PH");

    ShipmentDetails shipment = new ShipmentDetails();
    shipment.setLoadNumber("88528711");
    shipment.setShipmentNumber("546191213");
    shipment.setInboundShipmentDocId("546191213_20191106_719468_VENDOR_US");
    shipment.setSourceGlobalLocationNumber("0069382035222");
    shipment.setDestinationGlobalLocationNumber("0078742035222");

    documentLine.setShipmentDetailsList(Arrays.asList(shipment));


    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setLot("ABCDEF1234");
    manufactureDetail.setQty(20);
    manufactureDetail.setReportedUom("PH");
    documentLine.setManufactureDetails(Arrays.asList(manufactureDetail));

    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(documentLine));
    instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    return instructionRequest;
  }

  public static InstructionRequest getInstructionRequestFor2dBarcodeScan_NewInstruction() {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("1111111111111111");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(
        ApplicationIdentifier.LOT.getApplicationIdentifier());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("ABCDEF1234");

    scannedDataList.add(gtinScannedData);
    scannedDataList.add(lotNumberScannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    documentLine.setGtin("1111111111111111");
    documentLine.setCaseUpc("1111111111111111");
    documentLine.setItemUpc("1111111111111111");
    documentLine.setLotNumber("ABCDEF1234");
    documentLine.setDeptNumber("38");
    documentLine.setShippedQty(20);
    documentLine.setShippedQtyUom("PH");

    ShipmentDetails shipment = new ShipmentDetails();
    shipment.setLoadNumber("88528711");
    shipment.setShipmentNumber("546191213");
    shipment.setInboundShipmentDocId("546191213_20191106_719468_VENDOR_US");
    shipment.setSourceGlobalLocationNumber("0069382035222");
    shipment.setDestinationGlobalLocationNumber("0078742035222");
    documentLine.setShipmentDetailsList(new ArrayList(Arrays.asList(shipment)));

    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setLot("ABCDEF1234");
    manufactureDetail.setQty(20);
    manufactureDetail.setReportedUom("PH");
    documentLine.setManufactureDetails(new ArrayList(Arrays.asList(manufactureDetail)));

    deliveryDocument.setDeliveryDocumentLines(new ArrayList(Arrays.asList(documentLine)));
    instructionRequest.setDeliveryDocuments(new ArrayList(Arrays.asList(deliveryDocument)));
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    return instructionRequest;
  }

  public static Instruction getPatchedRxInstruction() {
    Instruction instruction = getInstructionWithManufactureDetails();
    instruction.setGtin("mockUpc");
    DeliveryDocument deliveryDocument =
        new Gson().fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    deliveryDocument.getDeliveryDocumentLines().get(0).setCatalogGTIN("mockUpc");
    GtinHierarchy catalogGtin = new GtinHierarchy("mockUpc", ReceivingConstants.ITEM_CATALOG_GTIN);
    List<GtinHierarchy> gtinHierarchies = Arrays.asList(catalogGtin);
    deliveryDocument.getDeliveryDocumentLines().get(0).setGtinHierarchy(gtinHierarchies);
    instruction.setDeliveryDocument(new Gson().toJson(deliveryDocument));
    return instruction;
  }

  public static InstructionRequest getInstructionRequestForD40Receiving() {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("00028000114603");

    scannedDataList.add(gtinScannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    documentLine.setGtin("00028000114603");
    documentLine.setLotNumber("ABCDEF1234");
    documentLine.setDeptNumber("40");
    documentLine.setShippedQty(20);
    documentLine.setShippedQtyUom("PH");

    //    ShipmentDetails shipment = new ShipmentDetails();
    //    shipment.setLoadNumber("88528711");
    //    shipment.setShipmentNumber("546191213");
    //    shipment.setInboundShipmentDocId("546191213_20191106_719468_VENDOR_US");
    //    shipment.setSourceGlobalLocationNumber("0069382035222");
    //    shipment.setDestinationGlobalLocationNumber("0078742035222");
    // FIXME
    // documentLine.setShipmentNumber("546191213");

    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setLot("ABCDEF1234");
    manufactureDetail.setQty(20);
    manufactureDetail.setReportedUom("PH");
    documentLine.setManufactureDetails(Arrays.asList(manufactureDetail));

    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(documentLine));
    instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));

    return instructionRequest;
  }

  public static SlottingPalletResponse mockSmartSlotting() {
    SlottingPalletResponse slottingRxPalletResponse = new SlottingPalletResponse();
    SlottingDivertLocations slottingRxDivertLocations = new SlottingDivertLocations();
    slottingRxPalletResponse.setMessageId("Test-Smart-Slotting");
    slottingRxDivertLocations.setLocation("A1234");
    slottingRxDivertLocations.setType("success");
    slottingRxPalletResponse.setLocations(Arrays.asList(slottingRxDivertLocations));
    return slottingRxPalletResponse;
  }

  public static Instruction getInstructionWithManufactureDetailsAndPrimeSlot() {
    Instruction instruction = new Instruction();
    instruction.setId(Long.valueOf("2076"));
    instruction.setContainer(getContainerData());
    instruction.setMove(getMoveData_primeSlot());

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setWarehousePack(6);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setVendorPack(1);
    content2.setRotateDate("2020-12-12");
    contents2.add(content2);
    content2.setQtyUom(ReceivingConstants.Uom.WHPK);
    content2.setWarehousePack(6);
    content2.setQty(1);

    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);
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
            + "      \"gtin\": \"00029695410987\",\n"
            + "      \"itemUPC\": \"00028000114603\",\n"
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
            + "      \"shipmentDetailsList\": [{\n"
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

  public static InstructionRequest getInstructionRequestFor2dBarcodeScan_CloseDatedItem() {
    InstructionRequest instructionRequest = MockInstruction.getInstructionRequest();
    List<ScannedData> scannedDataList = new ArrayList<>();
    ScannedData gtinScannedData = new ScannedData();
    gtinScannedData.setApplicationIdentifier(ApplicationIdentifier.GTIN.getApplicationIdentifier());
    gtinScannedData.setKey(ApplicationIdentifier.GTIN.getKey());
    gtinScannedData.setValue("00028000114603");
    ScannedData lotNumberScannedData = new ScannedData();
    lotNumberScannedData.setApplicationIdentifier(
        ApplicationIdentifier.LOT.getApplicationIdentifier());
    lotNumberScannedData.setKey(ReceivingConstants.KEY_LOT);
    lotNumberScannedData.setValue("00L032C09A");

    ScannedData expDateScannedData = new ScannedData();
    expDateScannedData.setApplicationIdentifier(
        ApplicationIdentifier.EXP.getApplicationIdentifier());
    expDateScannedData.setKey(ReceivingConstants.KEY_EXPIRY_DATE);
    LocalDate now = LocalDate.now();
    LocalDate futureDate = now.plus(100, ChronoUnit.DAYS);
    expDateScannedData.setValue(futureDate.format(DateTimeFormatter.ofPattern("yyMMdd")));

    ScannedData serialScannedData = new ScannedData();
    serialScannedData.setApplicationIdentifier(
        ApplicationIdentifier.SERIAL.getApplicationIdentifier());
    serialScannedData.setKey(ReceivingConstants.KEY_SERIAL);
    serialScannedData.setValue("12345678");


    ItemData additionalInfo = new ItemData();
    additionalInfo.setPalletFlowInMultiSku(true);
    additionalInfo.setIsEpcisEnabledVendor(true);
    additionalInfo.setAutoSwitchEpcisToAsn(false);
    ManufactureDetail serializedInfo = new ManufactureDetail();
    serializedInfo.setSerial("1234567890");
    additionalInfo.setSerializedInfo(singletonList(serializedInfo));

    scannedDataList.add(gtinScannedData);
    scannedDataList.add(lotNumberScannedData);
    scannedDataList.add(expDateScannedData);
    scannedDataList.add(serialScannedData);
    instructionRequest.setScannedDataList(scannedDataList);

    DeliveryDocument deliveryDocument = instructionRequest.getDeliveryDocuments().get(0);
    DeliveryDocumentLine documentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    documentLine.setGtin("00028000114603");
    documentLine.setLotNumber("00L032C09A");
    documentLine.setDeptNumber("38");
    documentLine.setShippedQty(20);
    documentLine.setShippedQtyUom("PH");
    documentLine.setAdditionalInfo(additionalInfo);

    ShipmentDetails shipment = new ShipmentDetails();
    shipment.setLoadNumber("88528711");
    shipment.setShipmentNumber("546191213");
    shipment.setInboundShipmentDocId("546191213_20191106_719468_VENDOR_US");
    shipment.setSourceGlobalLocationNumber("0069382035222");
    shipment.setDestinationGlobalLocationNumber("0078742035222");
    documentLine.setShipmentDetailsList(Arrays.asList(shipment));

    ManufactureDetail manufactureDetail = new ManufactureDetail();
    manufactureDetail.setLot("00L032C09A");
    manufactureDetail.setQty(20);
    manufactureDetail.setReportedUom("PH");
    documentLine.setManufactureDetails(Arrays.asList(manufactureDetail));

    deliveryDocument.setDeliveryDocumentLines(Arrays.asList(documentLine));
    instructionRequest.setDeliveryDocuments(Arrays.asList(deliveryDocument));
    instructionRequest.setReceivingType(RxReceivingType.TWOD_BARCODE.getReceivingType());

    return instructionRequest;
  }

  public static Instruction getMockInstructionEpcis() {
    // Move data
    LinkedTreeMap<String, Object> move = new LinkedTreeMap<>();
    move.put("fromLocation", "608");
    move.put("correlationID", "a1-b2-c3-d4-e6");
    move.put("lastChangedOn", new Date());
    move.put("lastChangedBy", "rxTestUser");

    Instruction instruction = new Instruction();
    instruction.setId(12345l);
    instruction.setActivityName("RxSSTK");
    instruction.setContainer(null);

    List<ContainerDetails> containerDetailsList = new ArrayList<>();

    ContainerDetails containerDetails1 = new ContainerDetails();
    List<Content> contents1 = new ArrayList<>();
    Content content1 = new Content();
    content1.setLot("11111");
    content1.setVendorPack(1);
    content1.setRotateDate("2020-12-12");
    content1.setQtyUom(ReceivingConstants.Uom.EACHES);
    content1.setWarehousePack(6);
    content1.setQty(1);
    contents1.add(content1);

    containerDetails1.setContents(contents1);
    containerDetailsList.add(containerDetails1);

    ContainerDetails containerDetails2 = new ContainerDetails();
    List<Content> contents2 = new ArrayList<>();
    Content content2 = new Content();
    content2.setLot("11111");
    content2.setVendorPack(1);
    content2.setRotateDate("2020-12-12");
    contents2.add(content2);
    content2.setQtyUom(ReceivingConstants.Uom.WHPK);
    content2.setWarehousePack(6);
    content2.setQty(1);

    containerDetails2.setContents(contents2);
    containerDetailsList.add(containerDetails2);

    instruction.setChildContainers(containerDetailsList);

    instruction.setCreateTs(new Date());
    instruction.setCreateUserId("rxTestUser");
    instruction.setLastChangeTs(new Date());
    instruction.setLastChangeUserId("rxTestUser");
    instruction.setDeliveryNumber(Long.valueOf("21119003"));
    instruction.setGtin("00000943037194");
    instruction.setInstructionCode("RxBuildPallet");
    instruction.setInstructionMsg("RxBuildPallet");
    instruction.setItemDescription("HEM VALUE PACK (4)");
    instruction.setMessageId("58e1df00-ebf6-11e8-9c25-dd4bfc2a06f8");
    instruction.setMove(move);
    instruction.setPoDcNumber("32898");
    instruction.setPrintChildContainerLabels(false);
    instruction.setPurchaseReferenceNumber("9763140004");
    instruction.setPurchaseReferenceLineNumber(1);
    instruction.setProjectedReceiveQty(60);
    instruction.setProjectedReceiveQtyUOM("EA");
    instruction.setReceivedQuantity(0);
    instruction.setReceivedQuantityUOM("EA");
    instruction.setProviderId("RxSSTK");
    instruction.setSsccNumber("00100700302232310010");
    instruction.setDeliveryDocument(
        "{\n"
            + "  \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "  \"financialGroupCode\": \"US\",\n"
            + "  \"baseDivCode\": \"WM\",\n"
            + "  \"deptNumber\": \"38\",\n"
            + "  \"purchaseCompanyId\": \"1\",\n"
            + "  \"purchaseReferenceLegacyType\": \"33\",\n"
            + "  \"poDCNumber\": \"32898\",\n"
            + "  \"purchaseReferenceStatus\": \"ACTV\",\n"
            + "  \"deliveryDocumentLines\": [\n"
            + "    {\n"
            + "      \"gtin\": \"00029695410987\",\n"
            + "      \"itemUPC\": \"00029695410987\",\n"
            + "      \"caseUPC\": \"20029695410987\",\n"
            + "      \"shippedQty\": \"10\",\n"
            + "      \"shippedQtyUom\": \"ZA\",\n"
            + "      \"purchaseReferenceNumber\": \"8458709164\",\n"
            + "      \"purchaseReferenceLineNumber\": 1,\n"
            + "      \"event\": \"POS REPLEN\",\n"
            + "      \"purchaseReferenceLineStatus\": \"RECEIVED\",\n"
            + "      \"whpkSell\": 8.22,\n"
            + "      \"vendorPackCost\": 6.6,\n"
            + "      \"vnpkQty\": 6,\n"
            + "      \"whpkQty\": 6,\n"
            + "      \"expectedQtyUOM\": \"ZA\",\n"
            + "      \"openQty\": 10,\n"
            + "      \"expectedQty\": 10,\n"
            + "      \"overageQtyLimit\": 0,\n"
            + "      \"itemNbr\": 561291081,\n"
            + "      \"purchaseRefType\": \"33\",\n"
            + "      \"palletTi\": 0,\n"
            + "      \"palletHi\": 0,\n"
            + "      \"vnpkWgtQty\": 14.84,\n"
            + "      \"vnpkWgtUom\": \"LB\",\n"
            + "      \"vnpkcbqty\": 0.432,\n"
            + "      \"vnpkcbuomcd\": \"CF\",\n"
            + "      \"isHazmat\": false,\n"
            + "      \"itemDescription1\": \"TOYS\",\n"
            + "      \"palletSSCC\": \"00100700302232310010\",\n"
            + "      \"packSSCC\": \"909899000020014377\",\n"
            + "      \"ndc\": \"43547-282-11\",\n"
            + "      \"shipmentNumber\": \"90989110\",\n"
            + "      \"shipmentDetailsList\": [\n"
            + "        {\n"
            + "          \"inboundShipmentDocId\": \"90989110_20191106_719468_VENDOR_US\",\n"
            + "          \"shipmentNumber\": \"90989110\",\n"
            + "          \"sourceGlobalLocationNumber\": \"0069382035222\",\n"
            + "          \"destinationGlobalLocationNumber\": \"0078742035222\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"manufactureDetails\": [\n"
            + "        {\n"
            + "          \"lot\": \"00L032C09A\",\n"
            + "          \"expiryDate\": \"2025-01-08\",\n"
            + "          \"qty\": 10,\n"
            + "          \"reportedUom\": \"ZA\"\n"
            + "        }\n"
            + "      ],\n"
            + "      \"additionalInfo\": {\n"
            + "        \"warehouseGroupCode\": \"\",\n"
            + "        \"isNewItem\": false,\n"
            + "        \"weight\": 0,\n"
            + "        \"warehouseMinLifeRemainingToReceive\": 0,\n"
            + "        \"isDscsaExemptionInd\": false,\n"
            + "        \"isHACCP\": false,\n"
            + "        \"primeSlotSize\": 0,\n"
            + "        \"isHazardous\": 0,\n"
            + "        \"atlasConvertedItem\": false,\n"
            + "        \"isWholesaler\": false,\n"
            + "        \"isDefaultTiHiUsed\": false,\n"
            + "        \"qtyValidationDone\": true,\n"
            + "        \"isEpcisEnabledVendor\": true,\n"
            + "        \"palletFlowInMultiSku\": false,\n"
            + "        \"auditQty\": 1,\n"
            + "        \"lotList\": [\n"
            + "          \"00L032C09E\",\n"
            + "          \"00L032C09B\",\n"
            + "          \"00L032C09A\",\n"
            + "          \"00L032C09D\",\n"
            + "          \"00L032C09C\"\n"
            + "        ],\n"
            + "        \"gtinList\": [\n"
            + "          \"00368180121015\"\n"
            + "        ],\n"
            + "        \"attpQtyInEaches\": 15,\n"
            + "        \"serializedInfo\": [\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          },\n"
            + "          {\n"
            + "            \"lot\": \"00L032C09C\",\n"
            + "            \"serial\": \"abc124\",\n"
            + "            \"expiryDate\": \"2025-01-08\",\n"
            + "            \"reportedUom\": \"CA\",\n"
            + "            \"gtin\": \"00368180121015\"\n"
            + "          }\n"
            + "        ]\n"
            + "      },\n"
            + "      \"deptNumber\": \"38\"\n"
            + "    }\n"
            + "  ],\n"
            + "  \"weight\": 0,\n"
            + "  \"cubeQty\": 0,\n"
            + "  \"deliveryStatus\": \"ARV\",\n"
            + "  \"totalBolFbq\": 106\n"
            + "}");

    return instruction;
  }

  public static Instruction getMockInstructionEpcisScannedCase() {
    Gson gson = new Gson();
    ManufactureDetail scannedCase = new ManufactureDetail();
    scannedCase.setSscc("1234567890");
    Instruction instruction = getMockInstructionEpcis();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    deliveryDocumentLine.getAdditionalInfo().setScannedCase(scannedCase);
    deliveryDocument.setDeliveryDocumentLines(Collections.singletonList(deliveryDocumentLine));
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  public static Instruction getInstructionWithEpcis() {
    Gson gson = new Gson();
    Instruction instruction = getInstructionWithManufactureDetails();
    DeliveryDocument deliveryDocument =
        gson.fromJson(instruction.getDeliveryDocument(), DeliveryDocument.class);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    ItemData additionalInfo = new ItemData();
    ManufactureDetail serializedInfo = new ManufactureDetail();
    serializedInfo.setSerial("1234567890");
    additionalInfo.setSerializedInfo(singletonList(serializedInfo));
    deliveryDocumentLine.setAdditionalInfo(additionalInfo);
    deliveryDocument.setDeliveryDocumentLines(singletonList(deliveryDocumentLine));
    instruction.setDeliveryDocument(gson.toJson(deliveryDocument));
    return instruction;
  }

  public static FitProblemTagResponse createFitProblemMockResponse() {
    FitProblemTagResponse mockFitProblemTagResponse = new FitProblemTagResponse();
    Resolution mockResolution = new Resolution();
    mockResolution.setRemainingQty(10);
    Issue issue = new Issue();
    issue.setQuantity(1);
    issue.setUom("ZA");
    mockFitProblemTagResponse.setIssue(issue);
    mockFitProblemTagResponse.setResolutions(Arrays.asList(mockResolution));

    return mockFitProblemTagResponse;
  }
}
