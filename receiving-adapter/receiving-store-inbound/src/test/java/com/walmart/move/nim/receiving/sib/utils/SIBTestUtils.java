package com.walmart.move.nim.receiving.sib.utils;

import static org.testng.Assert.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.message.common.DeliveryUpdateMessage;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.inventory.ContainerDTO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class SIBTestUtils {

  private static ObjectMapper objectMapper = new ObjectMapper();

  public static ContainerDTO getContainer(String path) {
    ContainerDTO container = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      container =
          objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), ContainerDTO.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return container;
  }

  public static List<Container> getContainers(String path) {
    List<Container> containers = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      containers =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), new TypeReference<List<Container>>() {});
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return containers;
  }

  public static List<ContainerItem> getContainerItems(String path) {
    List<ContainerItem> containerItems = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      containerItems =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), new TypeReference<List<ContainerItem>>() {});
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return containerItems;
  }

  public static ASNDocument getASNDocument(String path) {
    ASNDocument asnDocument = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      asnDocument =
          objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), ASNDocument.class);
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return asnDocument;
  }

  public static Delivery getDeliveryDocument(String path) {
    String deliveryDocs = null;
    Delivery delivery = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      deliveryDocs = new String(Files.readAllBytes(Paths.get(dataPath)));

      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      delivery =
          objectMapper.readValue(deliveryDocs.getBytes(StandardCharsets.UTF_8), Delivery.class);

      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      return delivery;
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return delivery;
  }

  public static List<Receipt> getReceipts(String path) {
    List<Receipt> receipts = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      receipts =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), new TypeReference<List<Receipt>>() {});
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return receipts;
  }

  public static DeliveryUpdateMessage getDeliveryUpdateMessage(String path) {
    String data = null;
    DeliveryUpdateMessage deliveryUpdateMessage = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      data = new String(Files.readAllBytes(Paths.get(dataPath)));

      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      deliveryUpdateMessage =
          objectMapper.readValue(
              data.getBytes(StandardCharsets.UTF_8), DeliveryUpdateMessage.class);

      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      return deliveryUpdateMessage;
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return deliveryUpdateMessage;
  }
}
