package com.walmart.move.nim.receiving.fixture.mock.data;

import static org.testng.Assert.fail;

import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.model.Distribution;
import com.walmart.move.nim.receiving.utils.constants.ContainerType;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FixtureMockData {
  private static String getFileAsString(String filePath) {

    try {
      String dataPath = new File(filePath).getCanonicalPath();
      return new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return null;
  }

  public static String getCTPutawayInventoryPayload() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/fixtureCTPutawayInventoryPayload.json");
  }

  public static String getShipmentAddedEventPayload() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/fixtureShipmentAddedEvent.json");
  }

  public static String getInvalidShipmentEventTypePayload() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/fixtureInvalidShipmentEventType.json");
  }

  public static String getShipmentUpdateEventPayload() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/fixtureShipmentUpdateEvent.json");
  }

  public static String getGlobalPackScanResponse() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/" + "json/fixtureGlobalPackScanResponse.json");
  }

  public static String getMultipleGlobalPackScanResponse() {
    return getFileAsString(
        "../../receiving-test/src/main/resources/"
            + "json/fixtureMultipleGlobalPackScanResponse.json");
  }

  public static Container getPendingContainer() {
    Container container = new Container();
    container.setCreateTs(new Date());
    container.setTrackingId("B32899000020011086");
    container.setParentTrackingId("B32899000020011086");
    container.setMessageId("B32899000020011086");
    container.setCreateUser(ReceivingConstants.DEFAULT_USER);
    container.setContainerStatus(ReceivingConstants.STATUS_PENDING_COMPLETE);
    container.setDeliveryNumber(1234567L);
    Map<String, String> lpnMap = new HashMap<>();
    lpnMap.put("countryCode", "US");
    lpnMap.put("buNumber", "6001");
    container.setDestination(lpnMap);

    Map<String, String> facility = new HashMap<>();
    facility.put("buNumber", "32987");
    facility.put("countryCode", "US");
    container.setFacility(facility);

    container.setCtrReusable(false);
    container.setCtrShippable(true);
    container.setOnConveyor(false);
    container.setIsConveyable(false);
    container.setContainerType(ContainerType.VENDORPACK.getText());
    List<ContainerItem> containerItemList = new ArrayList<>();
    containerItemList.add(getContainerItem(561301081L));
    container.setContainerItems(containerItemList);
    return container;
  }

  public static ContainerItem getContainerItem(Long itemNumber) {
    ContainerItem containerItem = new ContainerItem();
    containerItem.setTrackingId("B32899000020011086");
    containerItem.setItemNumber(itemNumber);
    containerItem.setDescription("Part");
    containerItem.setPurchaseReferenceNumber("2356789123");
    containerItem.setPurchaseReferenceLineNumber(1);
    containerItem.setQuantityUOM("EA");
    containerItem.setQuantity(5);
    containerItem.setOrderableQuantity(5);

    Distribution distribution = new Distribution();
    distribution.setOrderId("aewd-123");
    containerItem.setDistributions(Collections.singletonList(distribution));

    return containerItem;
  }

  public static Container getActiveContainerLPNNotMapped() {
    Container pendingContainer = getPendingContainer();
    pendingContainer.setContainerStatus(ReceivingConstants.STATUS_ACTIVE);
    return pendingContainer;
  }

  public static Container getActiveContainer() {
    Container pendingContainer = getPendingContainer();
    pendingContainer.setContainerStatus(ReceivingConstants.STATUS_ACTIVE);
    pendingContainer.setTrackingId("LPN 10656 INV 4784");
    pendingContainer
        .getContainerItems()
        .forEach(containerItem -> containerItem.setTrackingId("LPN 10656 INV 4784"));
    return pendingContainer;
  }

  public static Container getCompletedContainer() {
    Container activeContainer = getActiveContainer();
    activeContainer.setContainerStatus(ReceivingConstants.STATUS_COMPLETE);
    activeContainer.setLocation("F-012");
    activeContainer.setCompleteTs(new Date());
    activeContainer.setPublishTs(new Date());
    return activeContainer;
  }
}
