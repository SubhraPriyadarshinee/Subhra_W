package com.walmart.move.nim.receiving.endgame.common;

import static java.util.Objects.isNull;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.EventStore;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryQtyByPoAndPoLineResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmAsnDeliveryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails;
import com.walmart.move.nim.receiving.endgame.constants.DivertStatus;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.entity.SlottingDestination;
import com.walmart.move.nim.receiving.endgame.model.*;
import com.walmart.move.nim.receiving.endgame.model.SlotMoveType;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.EventStoreType;
import com.walmart.move.nim.receiving.utils.constants.EventTargetStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class EndGameUtilsTestCase {

  private static Gson gson = new Gson();
  @Mock private TenantSpecificConfigReader configUtils;

  @BeforeClass
  private void initMocks() {
    MockitoAnnotations.initMocks(this);
  }

  public static Container getContainer() {
    Container container = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/EndgameContainer.json")
              .getCanonicalPath();
      container =
          gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Container.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return container;
  }

  public static List<ContainerItem> getContainerItems() {
    ContainerItem containerItem = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/EndgameContainerItem.json")
              .getCanonicalPath();
      containerItem =
          gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), ContainerItem.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    List<ContainerItem> containerItems = new ArrayList<>();
    containerItems.add(containerItem);
    return containerItems;
  }

  public static String getUPCOnOnePOLine() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameScanUPCOnePOLineV3.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getUPCOnOnePOLineSams() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameScanUPCOnePOLineV3SamsRcvd.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getUPCOnOnePOLineSamsRcvd() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameScanUPCOnePOLineV3SamsRcvd.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getUPCOnMultiPOLine() {

    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameScanUPCMultiPOLineV3.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getAuditCaseUPCOnMultiPOLine(String path) {

    try {
      String dataPath = new File(path).getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getUPCOnMultiPOLineMultipleSeller() {

    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/wfs/EndgameScanUPCMultiPOLineV3.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getCancelledPOLineData() {

    String deliveryJson = null;

    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameScanUPCCancelledPO.json")
              .getCanonicalPath();
      deliveryJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }

    assertNotNull(deliveryJson);

    Delivery delivery = gson.fromJson(deliveryJson, Delivery.class);

    return updatePoMabdDates(gson.toJson(delivery.getPurchaseOrders()));
  }

  public static PurchaseOrder getPurchaseOrder() {
    String poJson = null;
    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/" + "json/EndgamePurchaseOrder.json")
              .getCanonicalPath();
      poJson = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    assertNotNull(poJson);
    return gson.fromJson(poJson, PurchaseOrder.class);
  }

  public static Delivery getDeliveryData(String path) {
    try {
      String dataPath = new File(path).getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Delivery.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static Delivery getDelivery() {
    String dataPath = "../../receiving-test/src/main/resources/json/GDMDeliveryDocumentV3.json";
    return getDeliveryData(dataPath);
  }

  public static Delivery getDeliveryForWhpk() {
    String dataPath = "../../receiving-test/src/main/resources/json/GDMDeliveryDocumentWhpkV3.json";
    return getDeliveryData(dataPath);
  }

  public static Delivery getDeliveryForEaches() {
    String dataPath = "../../receiving-test/src/main/resources/json/GDMDeliveryDocumentEAV3.json";
    return getDeliveryData(dataPath);
  }

  public static Delivery getAssortmentDelivery() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/json/GDMDeliveryDocumentV3_assortment_item.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(dataPath))), Delivery.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static Map<String, Object> getItemDetails() {
    try {
      String itemDetails =
          new File("../../receiving-test/src/main/resources/json/item_details_9213971.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static Map<String, Object> getSSOTItemDetails(String path) {
    try {
      String itemDetails = new File(path).getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static Map<String, Object> getAssortmentItemDetails() {
    try {
      String itemDetails =
          new File("../../receiving-test/src/main/resources/json/item_details_577186241.json")
              .getCanonicalPath();
      return gson.fromJson(new String(Files.readAllBytes(Paths.get(itemDetails))), Map.class);
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
      return null;
    }
  }

  public static List<SlottingDestination> getSingleSlottingDestination() {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC("12333334");
    slottingDestination.setDestination(DivertStatus.DECANT.getStatus());
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@");
    return Collections.singletonList(slottingDestination);
  }

  public static SlottingDestination getSlottingDestination() {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC("12333334");
    slottingDestination.setDestination(DivertStatus.DECANT.getStatus());
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@");
    return slottingDestination;
  }

  public static SlottingDestination getSlottingDestination(DivertStatus divertStatus) {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC("12333334");
    slottingDestination.setDestination(divertStatus.getStatus());
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@");
    return slottingDestination;
  }

  public static List<SlottingDestination> getSingleSlottingDestination(
      String caseUPC, DivertStatus divertStatus) {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC(caseUPC);
    slottingDestination.setDestination(divertStatus.getStatus());
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@");
    return Collections.singletonList(slottingDestination);
  }

  public static List<SlottingDestination> getSingleSlottingDestinationWithMultipleSellerId(
      String caseUPC, DivertStatus divertStatus) {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC(caseUPC);
    slottingDestination.setDestination(divertStatus.getStatus());
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@");

    SlottingDestination slottingDestination_1 = new SlottingDestination();
    slottingDestination_1.setCaseUPC(caseUPC);
    slottingDestination_1.setDestination(divertStatus.getStatus());
    slottingDestination_1.setSellerId("F55CDC31AB754BB68FE0B39041159D64");
    slottingDestination_1.setPossibleUPCs("@12333331@,@12333332@,@12333333@");
    return Arrays.asList(slottingDestination, slottingDestination_1);
  }

  public static List<SlottingDestination> getPossibleUPCResponse() {
    SlottingDestination slottingDestination = new SlottingDestination();
    slottingDestination.setCaseUPC("12333334");
    slottingDestination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination.setDestination(DivertStatus.DECANT.getStatus());
    slottingDestination.setPossibleUPCs("@12333331@,@12333332@,@12333333@,@12333333@,@12333333@");

    SlottingDestination slottingDestination1 = new SlottingDestination();
    slottingDestination1.setCaseUPC("12333335");
    slottingDestination1.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination1.setDestination(DivertStatus.DECANT.getStatus());
    slottingDestination1.setPossibleUPCs("@12333331@,@12333332@,@12333333@,@12333333@,@12333333@");

    SlottingDestination slottingDestination2 = new SlottingDestination();
    slottingDestination2.setCaseUPC("12333336");
    slottingDestination2.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    slottingDestination2.setDestination(DivertStatus.DECANT.getStatus());
    slottingDestination2.setPossibleUPCs("@12333331@,@12333332@,@12333333@,@12333333@,@12333333@");
    return Arrays.asList(slottingDestination, slottingDestination1, slottingDestination2);
  }

  public static List<ReceiptSummaryEachesResponse> getReceiptSummary_0664420451() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 2l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse> getReceiptSummary_0664420452() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420452", 1, null, 2l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse> getReceiptSummary__0664420451_2() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 5l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse> getReceiptSummary_0664420451_0664420452() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420452", 1, null, 2l));
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 2l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse>
      getReceiptSummary_0664420451_0664420452_OverageFor_0664420451() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420452", 1, null, 4l));
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 2l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse> getReceiptSummaryWithOverage_0664420451() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 4l));
    return ReceiptSummaryEachesResponses;
  }

  public static List<ReceiptSummaryEachesResponse>
      getReceiptSummaryWithOverage_0664420451_0664420452() {
    List<ReceiptSummaryEachesResponse> ReceiptSummaryEachesResponses = new ArrayList<>();
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420452", 1, null, 4l));
    ReceiptSummaryEachesResponses.add(new ReceiptSummaryEachesResponse("0664420451", 1, null, 4l));
    return ReceiptSummaryEachesResponses;
  }

  public static SlotLocation getSlotWithContainerId(String containerid) {
    SlotLocation slotLocation = new SlotLocation();
    slotLocation.setLocation("testLocation");
    slotLocation.setContainerTrackingId(containerid);
    slotLocation.setType("testType");
    slotLocation.setMoveRequired(true);
    slotLocation.setMoveType(SlotMoveType.HAUL);
    return slotLocation;
  }

  public static SlottingDivertResponse getSlottingDivertResponse() {
    DivertDestinationFromSlotting destination = new DivertDestinationFromSlotting();
    destination.setCaseUPC("20078742229434");
    destination.setDivertLocation("DECANT");
    destination.setItemNbr(561298341L);
    destination.setSellerId("F55CDC31AB754BB68FE0B39041159D63");
    SlottingDivertResponse slottingDivertResponse = new SlottingDivertResponse();
    slottingDivertResponse.setMessageId(UUID.randomUUID().toString());
    slottingDivertResponse.setDivertLocations(Collections.singletonList(destination));
    return slottingDivertResponse;
  }

  public static SlottingDivertRequest getSlottingDivertRequest() {
    SlottingDivertRequest slottingDivertRequest = new SlottingDivertRequest();
    slottingDivertRequest.setMessageId(UUID.randomUUID().toString());
    slottingDivertRequest.setDivertRequestItems(
        Collections.singletonList(
            DivertRequestItem.builder()
                .caseUPC("20078742229434")
                .itemNbr(561298341L)
                .sellerId("F55CDC31AB754BB68FE0B39041159D63")
                .build()));
    return slottingDivertRequest;
  }

  public static DivertDestinationToHawkeye getDivertDestinationToHawkeye() {
    return DivertDestinationToHawkeye.builder()
        .caseUPC("20078742229434")
        .destination("PALLET_BUILD")
        .possibleUPCs(
            Arrays.asList("@12333331@", "@12333332@", "@12333333@", "@12333333@", "@12333333@"))
        .build();
  }

  public static List<ReceiptSummaryQtyByPoAndPoLineResponse>
      getReceiptSummaryQtyByPoAndPoLineResponse() {
    List<ReceiptSummaryQtyByPoAndPoLineResponse> receiptSummaryQtyByPoAndPoLineResponses =
        new ArrayList<>();
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse1 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0664420451", 1, 0L);
    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse2 =
        new ReceiptSummaryQtyByPoAndPoLineResponse("0664420452", 1, 0L);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse1);
    receiptSummaryQtyByPoAndPoLineResponses.add(receiptSummaryQtyByPoAndPoLineResponse2);
    return receiptSummaryQtyByPoAndPoLineResponses;
  }

  public static DeliveryUpdateMessage getDeliveryUpdateMessage() {
    DeliveryUpdateMessage deliveryUpdateMessage = new DeliveryUpdateMessage();
    deliveryUpdateMessage.setCountryCode("US");
    deliveryUpdateMessage.setSiteNumber("4321");
    deliveryUpdateMessage.setDeliveryNumber("30008889");
    deliveryUpdateMessage.setEventType("DOOR_ASSIGNED");
    deliveryUpdateMessage.setDeliveryStatus(DeliveryStatus.ARV.name());
    deliveryUpdateMessage.setUrl(
        "https://dev.gdm.prod.us.walmart.net/document/v2/deliveries/56003401?docNbr=9888888843&docLineNbr=1");
    return deliveryUpdateMessage;
  }

  private static String updatePoMabdDates(String deliveryDocument) {
    List<PurchaseOrder> purchaseOrderList =
        Arrays.asList(gson.fromJson(deliveryDocument, PurchaseOrder[].class));
    purchaseOrderList.forEach(
        po -> {
          Dates poDates = po.getDates();
          po.getDates().setMabd(updateYear(poDates.getMabd()));
          po.getDates().setOrdered(updateYear(poDates.getOrdered()));
          po.getDates().setCancel(updateYear(poDates.getCancel()));
          po.getDates().setShip(updateYear(poDates.getShip()));
        });
    return gson.toJson(purchaseOrderList);
  }

  private static String updateYear(String date) {
    if (isNull(date)) return null;
    DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern(EndgameConstants.SIMPLE_DATE);
    LocalDate dt = LocalDate.parse(date).withYear(LocalDate.now().getYear());
    return dateFormat.format(dt);
  }

  public static EventStore getEventStoreData() {
    EventStore eventStore = new EventStore();
    eventStore.setId(10L);
    eventStore.setEventStoreKey("200");
    eventStore.setFacilityCountryCode("US");
    eventStore.setFacilityNum(7552);
    eventStore.setDeliveryNumber(1234L);
    eventStore.setContainerId("");
    eventStore.setStatus(EventTargetStatus.PENDING);
    eventStore.setEventStoreType(EventStoreType.DOOR_ASSIGNMENT);
    eventStore.setPayload("");
    eventStore.setRetryCount(0);
    eventStore.setCreatedDate(new Date());
    eventStore.setLastUpdatedDate(new Date());
    return eventStore;
  }

  public static String getValidLocationResponseForAutoFc() {

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/ValidLocationResponseForEG.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getReplenLocationResponseForAutoFc() {

    try {
      String dataPath =
          new File("../../receiving-test/src/main/resources/json/ReplenLocationResponseForEG.json")
              .getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static JsonArray getLocationResponseAsJsonArray() {
    String locationResponseString = getValidLocationResponseForAutoFc();
    return getJsonArray(locationResponseString);
  }

  public static JsonArray getReplenLocationResponseAsJsonArray() {
    String locationResponseString = getReplenLocationResponseForAutoFc();
    return getJsonArray(locationResponseString);
  }

  private static JsonArray getJsonArray(String locationResponseString) {
    JsonObject locationResponseJsonObject =
        new JsonParser().parse(locationResponseString).getAsJsonObject();
    JsonArray locationsArray =
        locationResponseJsonObject
            .get(ReceivingConstants.SUCCESS_TUPLES)
            .getAsJsonArray()
            .get(0)
            .getAsJsonObject()
            .get(ReceivingConstants.RESPONSE)
            .getAsJsonObject()
            .get(ReceivingConstants.LOCATIONS)
            .getAsJsonArray();
    return locationsArray;
  }

  public static GdmAsnDeliveryResponse getASNData(String filePath) {
    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return gson.fromJson(
          new String(Files.readAllBytes(Paths.get(dataPath))), GdmAsnDeliveryResponse.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getWfsUPCOnOnePOLine() {
    try {
      String dataPath =
          new File(
                  "../../receiving-test/src/main/resources/"
                      + "json/EndgameWFSScanUPCOnePOLineV3.json")
              .getCanonicalPath();
      return updatePoMabdDates(new String(Files.readAllBytes(Paths.get(dataPath))));
    } catch (IOException e) {
      assertTrue(false, "Unable to read file " + e.getMessage());
    }
    return null;
  }

  @Test
  public void testIsVnpkPalletItem_VnpkPalletQtyEnabled() {
    PurchaseOrderLine purchaseOrderLine = getPurchaseOrderLine(1, 1, 100);
    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(true);
    boolean result = EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS");
    Assert.assertTrue(result);
  }

  @Test
  public void testIsVnpkPalletItem_VnpkPalletQtyDisabled() {
    PurchaseOrderLine purchaseOrderLine = getPurchaseOrderLine(1, 1, 100);
    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(false);
    boolean result = EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS");
    Assert.assertFalse(result);
    boolean resultWm = EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "WM");
    Assert.assertFalse(resultWm);
  }

  @Test
  public void testIsVnpkPalletItem_QuantitiesDoNotMatch() {
    PurchaseOrderLine purchaseOrderLine = getPurchaseOrderLine(2, 2, 100);
    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(true);
    boolean result = EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS");
    Assert.assertFalse(result);
  }

  @Test(expectedExceptions = Exception.class)
  public void testIsVnpkPalletItem_ThrowsReceivingException() {
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    Vnpk vnpk = new Vnpk();
    vnpk.setQuantity(10);
    purchaseOrderLine.setVnpk(vnpk);
    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(true);
    when(purchaseOrderLine.getItemDetails()).thenThrow(Exception.class);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));
  }

  @Test(expectedExceptions = Exception.class)
  public void testIsVnpkPalletItem_NullPointerException() {
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(true);
    when(purchaseOrderLine.getVnpk()).thenReturn(null);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));

    Vnpk vnpk = new Vnpk();
    vnpk.setQuantity(10);
    purchaseOrderLine.setVnpk(vnpk);
    when(purchaseOrderLine.getVnpk()).thenReturn(vnpk);
    when(purchaseOrderLine.getItemDetails().getPalletHi()).thenThrow(Exception.class);
    when(purchaseOrderLine.getItemDetails().getPalletTi()).thenReturn(1);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));

    when(purchaseOrderLine.getItemDetails().getPalletTi()).thenThrow(Exception.class);
    when(purchaseOrderLine.getItemDetails().getPalletHi()).thenReturn(1);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));

    vnpk = new Vnpk();
    when(vnpk.getQuantity()).thenThrow(Exception.class);
    purchaseOrderLine.setVnpk(vnpk);
    when(purchaseOrderLine.getVnpk()).thenReturn(vnpk);
    when(purchaseOrderLine.getItemDetails().getPalletTi()).thenReturn(1);
    when(purchaseOrderLine.getItemDetails().getPalletHi()).thenReturn(1);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));

    when(vnpk.getQuantity()).thenReturn(null);
    purchaseOrderLine.setVnpk(vnpk);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));

    when(configUtils.getConfiguredFeatureFlag(ReceivingConstants.IS_VNPK_PALLET_QTY_ENABLED))
        .thenReturn(null);
    Assert.assertFalse(EndGameUtils.isVnpkPalletItem(configUtils, purchaseOrderLine, "SAMS"));
  }

  PurchaseOrderLine getPurchaseOrderLine(Integer palletTi, Integer palletHi, Integer quantity) {
    PurchaseOrderLine purchaseOrderLine = new PurchaseOrderLine();
    com.walmart.move.nim.receiving.core.model.gdm.v3.ItemDetails itemDetails = new ItemDetails();
    itemDetails.setPalletTi(palletTi);
    itemDetails.setPalletHi(palletHi);
    purchaseOrderLine.setItemDetails(itemDetails);
    Vnpk vnpk = new Vnpk();
    vnpk.setQuantity(quantity);
    purchaseOrderLine.setVnpk(vnpk);
    return purchaseOrderLine;
  }
}
