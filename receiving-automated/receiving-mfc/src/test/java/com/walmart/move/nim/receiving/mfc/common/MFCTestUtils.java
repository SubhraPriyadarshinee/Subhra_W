package com.walmart.move.nim.receiving.mfc.common;

import static org.testng.Assert.fail;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.ContainerItem;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.gdm.GDMShipmentSearchResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.ASNDocument;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.model.osdr.v2.OSDRPayload;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class MFCTestUtils {

  private static ObjectMapper objectMapper = new ObjectMapper();

  public static Container getContainer(String path) {
    Container container = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      container = objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), Container.class);
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

  public static OSDRPayload getOSDRPayloadV2(String path) {
    OSDRPayload osdrPayload = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      osdrPayload =
          objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), OSDRPayload.class);
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return osdrPayload;
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

  public static HawkeyeAdjustment getHawkeyeAdjustment(String path) {
    HawkeyeAdjustment hawkeyeAdjustment = null;
    try {
      String dataPath = new File(path).getCanonicalPath();
      hawkeyeAdjustment =
          objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), HawkeyeAdjustment.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return hawkeyeAdjustment;
  }

  public static MFCInventoryAdjustmentDTO getInventoryAdjustmentTo(String path) {
    MFCInventoryAdjustmentDTO inventoryAdjustmentTO = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      inventoryAdjustmentTO =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), MFCInventoryAdjustmentDTO.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return inventoryAdjustmentTO;
  }

  public static OSDRPayload getOSDRPayload(String path) {
    OSDRPayload osdrPayload = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      osdrPayload =
          objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), OSDRPayload.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return osdrPayload;
  }

  public static String readInputFile(String path) {
    String jsonString = "";
    try {
      String dataPath = new File(path).getCanonicalPath();
      jsonString = new String(Files.readAllBytes(Paths.get(dataPath)));
    } catch (Exception e) {
      e.printStackTrace();
    }
    return jsonString;
  }

  public static NGRPack getNGRPack(String path) {
    NGRPack ngrPack = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      ngrPack = objectMapper.readValue(Files.readAllBytes(Paths.get(dataPath)), NGRPack.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return ngrPack;
  }

  public static GDMShipmentSearchResponse getGDMShipmentSearchResponse(String path) {
    GDMShipmentSearchResponse response = null;
    try {
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      String dataPath = new File(path).getCanonicalPath();
      response =
          objectMapper.readValue(
              Files.readAllBytes(Paths.get(dataPath)), GDMShipmentSearchResponse.class);
    } catch (IOException e) {
      fail("Unable to read file " + e.getMessage());
    }
    return response;
  }
}
