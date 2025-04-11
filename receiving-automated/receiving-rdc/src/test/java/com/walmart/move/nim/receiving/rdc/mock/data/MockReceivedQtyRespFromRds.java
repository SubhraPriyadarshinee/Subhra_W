package com.walmart.move.nim.receiving.rdc.mock.data;

import com.walmart.move.nim.receiving.core.client.nimrds.model.ReceivedQuantityResponseFromRDS;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MockReceivedQtyRespFromRds {

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByPoAndPoLine(Long receivedQty)
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    DeliveryDocument deliveryDocument = deliveryDocumentList.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    String key =
        deliveryDocumentLine.getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByPoAndPoLineDA(Long receivedQty)
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    DeliveryDocument deliveryDocument = deliveryDocumentList.get(0);
    DeliveryDocumentLine deliveryDocumentLine = deliveryDocument.getDeliveryDocumentLines().get(0);
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    String key =
        deliveryDocumentLine.getPurchaseReferenceNumber()
            + ReceivingConstants.DELIM_DASH
            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByPoAndPoLineMultipleDA(
      Long receivedQty) throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
      String key =
          deliveryDocumentLine.getPurchaseReferenceNumber()
              + ReceivingConstants.DELIM_DASH
              + deliveryDocumentLine.getPurchaseReferenceLineNumber();
      receivedQtyMapByPoAndPoLine.put(key, receivedQty);
      receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    }
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapByPoAndPoLineMultipleDAPartiallyFulfilled(Long receivedQty)
          throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMultipleDA();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    for (DeliveryDocument deliveryDocument : deliveryDocumentList) {
      DeliveryDocumentLine deliveryDocumentLine =
          deliveryDocument.getDeliveryDocumentLines().get(0);
      String key =
          deliveryDocumentLine.getPurchaseReferenceNumber()
              + ReceivingConstants.DELIM_DASH
              + deliveryDocumentLine.getPurchaseReferenceLineNumber();
      if (key.equals("8458708163-1")) {
        receivedQtyMapByPoAndPoLine.put(key, 10L);
      } else {
        receivedQtyMapByPoAndPoLine.put(key, receivedQty);
      }
      receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    }
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapForMoreThanOnePo()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForMoreThanOneSSTKPo();
    return getReceivedQtyMapByDeliveryDocument(deliveryDocumentList);
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapForOnePoWithTwoLines()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForOneSSTKPoWithMoreThanOneLine();
    return getReceivedQtyMapByDeliveryDocument(deliveryDocumentList);
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapForOnePoWithOneLine()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    return getReceivedQtyMapByDeliveryDocument(deliveryDocumentList);
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapForOnePoWithOneLineDA()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSingleDA();
    return getReceivedQtyMapByDeliveryDocument(deliveryDocumentList);
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapForOnePoWithOneLineAlreadyReceived() throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    return getReceivedQtyMapByDeliveryDocument(deliveryDocumentList, 3900L);
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapForOnePoWithOneLineReturnsError()
      throws IOException {
    List<DeliveryDocument> deliveryDocumentList =
        MockDeliveryDocuments.getDeliveryDocumentsForSSTK();
    return getReceivedQtyMapByDeliveryDocumentReturnsError(deliveryDocumentList);
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByDeliveryDocument(
      List<DeliveryDocument> deliveryDocumentList) {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    receivedQtyMapByPoAndPoLine.put(key, 10L);
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByDeliveryDocumentsAreFulfilled(
      List<DeliveryDocument> deliveryDocumentList) {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    Long receivedQty =
                        deliveryDocumentLine.getPurchaseReferenceLineNumber() == 1
                            ? Long.valueOf(3100)
                            : Long.valueOf(1100);
                    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByDeliveryDocument(
      List<DeliveryDocument> deliveryDocumentList, Long receivedQty) {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapByDeliveryDocumentForLine1OpenQtyIsFulfilled(
          List<DeliveryDocument> deliveryDocumentList) {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    Long receivedQty =
                        deliveryDocumentLine.getPurchaseReferenceLineNumber() == 1
                            ? Long.valueOf(3000)
                            : Long.valueOf(0);
                    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapByDeliveryDocumentHasFulfilledOpenQty(
          List<DeliveryDocument> deliveryDocumentList) {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    Long receivedQty =
                        deliveryDocumentLine.getPurchaseReferenceLineNumber() == 1
                            ? Long.valueOf(3000)
                            : Long.valueOf(1000);
                    receivedQtyMapByPoAndPoLine.put(key, receivedQty);
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS getReceivedQtyMapByDeliveryDocumentReturnsError(
      List<DeliveryDocument> deliveryDocumentList) {
    Map<String, String> errorMapByPoAndPoLine = new HashMap<>(deliveryDocumentList.size());
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    errorMapByPoAndPoLine.put(key, "PO line not found");
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(new HashMap<>());
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getEmptyReceivedAndErrorQtyResponseFromRdsForSinglePoAndPoLine() throws IOException {
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(new HashMap<>());
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(new HashMap<>());
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapForOnePoWithTwoLinesReturnsFoundAndErrorResponseForPoLine1(
          List<DeliveryDocument> deliveryDocumentList) throws IOException {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    Map<String, String> errorResponseMapByPoAndPoLine = new HashMap<>();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    if (deliveryDocumentLine.getPurchaseReferenceLineNumber() == 1) {
                      errorResponseMapByPoAndPoLine.put(key, "PoLine Not Found");
                    } else {
                      receivedQtyMapByPoAndPoLine.put(key, 10L);
                    }
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorResponseMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapForMultiPORDSReturnsFoundAndErrorResponseForOnePO(
          List<DeliveryDocument> deliveryDocumentList) throws IOException {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    Map<String, String> errorResponseMapByPoAndPoLine = new HashMap<>();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    if (deliveryDocumentLine.getPurchaseReferenceLineNumber() == 1
                        && deliveryDocumentLine
                            .getPurchaseReferenceNumber()
                            .equalsIgnoreCase("8458708162")) {
                      errorResponseMapByPoAndPoLine.put(key, "PoLine Not Found");
                    } else {
                      receivedQtyMapByPoAndPoLine.put(key, 10L);
                    }
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorResponseMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }

  public static ReceivedQuantityResponseFromRDS
      getReceivedQtyMapForOnePoWithTwoLinesReturnsFoundAndErrorResponseForPoLine2(
          List<DeliveryDocument> deliveryDocumentList) throws IOException {
    Map<String, Long> receivedQtyMapByPoAndPoLine = new HashMap<>();
    Map<String, String> errorResponseMapByPoAndPoLine = new HashMap<>();
    ReceivedQuantityResponseFromRDS receivedQuantityResponseFromRDS =
        new ReceivedQuantityResponseFromRDS();
    deliveryDocumentList.forEach(
        deliveryDocument -> {
          deliveryDocument
              .getDeliveryDocumentLines()
              .forEach(
                  deliveryDocumentLine -> {
                    String key =
                        deliveryDocumentLine.getPurchaseReferenceNumber()
                            + ReceivingConstants.DELIM_DASH
                            + deliveryDocumentLine.getPurchaseReferenceLineNumber();
                    if (deliveryDocumentLine.getPurchaseReferenceLineNumber() == 2) {
                      errorResponseMapByPoAndPoLine.put(key, "PO Not Found");
                    } else {
                      receivedQtyMapByPoAndPoLine.put(key, 10L);
                    }
                  });
        });
    receivedQuantityResponseFromRDS.setReceivedQtyMapByPoAndPoLine(receivedQtyMapByPoAndPoLine);
    receivedQuantityResponseFromRDS.setErrorResponseMapByPoAndPoLine(errorResponseMapByPoAndPoLine);
    return receivedQuantityResponseFromRDS;
  }
}
