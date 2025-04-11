package com.walmart.move.nim.receiving.wfs.utils;

import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.ApplicationIdentifier;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.InstructionRequest;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.InstructionResponseImplNew;
import com.walmart.move.nim.receiving.core.model.ScannedData;
import com.walmart.move.nim.receiving.core.model.delivery.DeliveryScoreHelper;
import com.walmart.move.nim.receiving.core.model.delivery.TCLFreeDeliveryDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.wfs.constants.WFSConstants;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

public class WFSUtility {

  private WFSUtility() throws IllegalAccessException {
    throw new IllegalAccessException("Utility class");
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(WFSUtility.class);

  public static boolean isSingleDeliveryOnly(
      ConsolidatedDeliveryList listOfDeliveriesInAcceptedStatus) {
    LOGGER.info("TCL Free : Validating is single delivery only");
    return listOfDeliveriesInAcceptedStatus.getData().size() == 1;
  }

  public static TCLFreeDeliveryDetails findDeliveryForTCLFreeReceiving(
      List<ConsolidatedDelivery> listOfDeliveries) {
    TCLFreeDeliveryDetails tclFreeDeliveryDetails = TCLFreeDeliveryDetails.builder().build();
    LOGGER.info("TCL Free : Checking if Deliveries belong to Multiple Sellers");
    if (isSingleSeller(listOfDeliveries)) {
      tclFreeDeliveryDetails.setMultipleSellersAvailable(new AtomicBoolean(false));
      return tclFreeDeliveryDetails;
    } else {
      tclFreeDeliveryDetails.setMultipleSellersAvailable(new AtomicBoolean(true));
      return tclFreeDeliveryDetails;
    }
  }

  private static boolean isSingleSeller(List<ConsolidatedDelivery> listOfDeliveries) {
    String sellerId = "";
    if (listOfDeliveries != null) {
      for (ConsolidatedDelivery delivery : listOfDeliveries) {
        for (ConsolidatedPurchaseOrder po : delivery.getPurchaseOrders()) {
          if (StringUtils.isEmpty(sellerId)) {
            sellerId = po.getSellerId();
          } else if (sellerId.equals(po.getSellerId())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static TCLFreeDeliveryDetails findDeliveryByPriorityAndScore(
      List<ConsolidatedDelivery> listOfDeliveries,
      TCLFreeDeliveryDetails tclFreeDeliveryDetails,
      String gtin,
      List<String> deliveryStatusList) {
    List<ConsolidatedDelivery> sortedList = new ArrayList<>();
    if (gtin != null) {
      tclFreeDeliveryDetails.setGtin(gtin);
    }
    // Iterate through each status in the desired order
    for (String status : deliveryStatusList) {
      // Find entries with the current status and add them to the sorted list
      for (ConsolidatedDelivery delivery : listOfDeliveries) {
        if (delivery.getStatusInformation().getStatus().equals(status)) {
          sortedList.add(delivery);
        }
      }
    }
    if (!sortedList
        .get(0)
        .getStatusInformation()
        .getStatus()
        .equals(sortedList.get(1).getStatusInformation().getStatus())) {
      long deliveryNumber = sortedList.get(0).getDeliveryNumber();
      LOGGER.info("TCL Free : Based on priority Delivery : {} choosen", deliveryNumber);
      tclFreeDeliveryDetails.setDeliveryNumber(deliveryNumber);
      return tclFreeDeliveryDetails;
    }

    Iterator<ConsolidatedDelivery> iterator = sortedList.iterator();
    String currentStatus = null;
    while (iterator.hasNext()) {
      ConsolidatedDelivery delivery = iterator.next();
      // If the status changes, remove all subsequent elements
      if (currentStatus != null
          && !delivery.getStatusInformation().getStatus().equals(currentStatus)) {
        iterator.remove();
        break; // Stop iteration after removing elements with different status
      }
      // Update the current status
      currentStatus = delivery.getStatusInformation().getStatus();
    }
    tclFreeDeliveryDetails.setListOfDeliveries(sortedList);
    isSamePONumber(tclFreeDeliveryDetails);
    scoringFunction(tclFreeDeliveryDetails);
    return tclFreeDeliveryDetails;
  }

  public static void isSamePONumber(TCLFreeDeliveryDetails tclFreeDeliveryDetails) {
    boolean isSamePO =
        tclFreeDeliveryDetails
            .getListOfDeliveries()
            .stream()
            .map(
                delivery ->
                    delivery
                        .getPurchaseOrders()
                        .stream()
                        .map(ConsolidatedPurchaseOrder::getPoNumber)
                        .distinct()
                        .count())
            .allMatch(count -> count == 1);
    if (isSamePO) {
      tclFreeDeliveryDetails.setPoNumber(
          tclFreeDeliveryDetails
              .getListOfDeliveries()
              .get(0)
              .getPurchaseOrders()
              .get(0)
              .getPoNumber());
      tclFreeDeliveryDetails.setSinglePo(new AtomicBoolean(isSamePO));
    }
  }

  public static TCLFreeDeliveryDetails scoringFunction(
      TCLFreeDeliveryDetails tclFreeDeliveryDetails) {
    List<DeliveryScoreHelper> listOfDeliveryScoreHelper =
        extractCombinedLines(tclFreeDeliveryDetails);
    Collections.sort(listOfDeliveryScoreHelper, new ScoreCompartor());
    Collections.reverse(listOfDeliveryScoreHelper);
    long deliveryNumber = listOfDeliveryScoreHelper.get(0).getDeliveryNumber();
    LOGGER.info("TCL Free : Based on Scoring Function Delivery : {} choosen", deliveryNumber);
    tclFreeDeliveryDetails.setDeliveryNumber(deliveryNumber);
    return tclFreeDeliveryDetails;
  }

  private static List<DeliveryScoreHelper> extractCombinedLines(
      TCLFreeDeliveryDetails tclFreeDeliveryDetails) {
    List<DeliveryScoreHelper> deliveryScoreHelperList = new ArrayList<>();
    List<ConsolidatedDelivery> deliveryData = tclFreeDeliveryDetails.getListOfDeliveries();
    for (ConsolidatedDelivery delivery : deliveryData) {
      for (ConsolidatedPurchaseOrder po : delivery.getPurchaseOrders()) {
        processPurchaseOrderAndExtractData(deliveryScoreHelperList, delivery, po);
      }
    }
    return deliveryScoreHelperList;
  }

  private static void processPurchaseOrderAndExtractData(
      List<DeliveryScoreHelper> deliveryScoreHelperList,
      ConsolidatedDelivery delivery,
      ConsolidatedPurchaseOrder po) {
    for (ConsolidatedPurchaseOrderLine purchaseOrderLine : po.getLines()) {
      if (purchaseOrderLine.getPoLineNumber() != null) {
        deliveryScoreHelperList.add(
            new DeliveryScoreHelper(
                delivery.getDeliveryNumber(),
                po.getPoNumber(),
                purchaseOrderLine.getPoLineNumber() != null
                    ? Long.valueOf(purchaseOrderLine.getPoLineNumber())
                    : 0,
                0,
                purchaseOrderLine.getFreightBillQty() != null
                    ? purchaseOrderLine.getFreightBillQty()
                    : 0));
      }
    }
  }

  public static Map<String, ScannedData> getScannedDataMap(List<ScannedData> scannedDataList) {
    Map<String, ScannedData> scannedDataMap = new HashMap<>();
    if (!CollectionUtils.isEmpty(scannedDataList)) {
      scannedDataList.forEach(
          scannedData -> scannedDataMap.put(scannedData.getApplicationIdentifier(), scannedData));
    }
    return scannedDataMap;
  }

  public static String getPoFromScannedDataMap(Map<String, ScannedData> scannedDataMap) {
    return scannedDataMap.containsKey(ApplicationIdentifier.PO.getApplicationIdentifier())
        ? scannedDataMap.get(ApplicationIdentifier.PO.getApplicationIdentifier()).getValue()
        : null;
  }

  public static String getGtinFromScannedDataMap(Map<String, ScannedData> scannedDataMap) {
    return scannedDataMap.containsKey(ApplicationIdentifier.GTIN.getApplicationIdentifier())
        ? scannedDataMap.get(ApplicationIdentifier.GTIN.getApplicationIdentifier()).getValue()
        : null;
  }

  public static boolean isExceedingOverageThreshold(
      InstructionRequest instructionRequest, boolean isKotlin) {
    /*
     * instructionRequest will only be having one document line as this method is being called after item quantity insertion from receiving UI
     */
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    int maxTotalReceivedQty = documentLine.getTotalOrderQty() + documentLine.getOverageQtyLimit();
    int newTotalReceivedQty = documentLine.getTotalReceivedQty();

    LOGGER.info(
        "Validation overage receiving case for delivery number: {}, po number: {}, upc: {}, facility {}, correlationId: {}",
        instructionRequest.getDeliveryNumber(),
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getItemUpc(),
        TenantContext.getFacilityNum(),
        TenantContext.getCorrelationId());

    if (isKotlin) {
      // Quantity will be vnpkQty*enteredQty, both these quantities are present in
      // deliveryDocumentLine
      newTotalReceivedQty += documentLine.getEnteredQty() * documentLine.getVendorPack();
    } else {
      // Quantity will be enteredQty, present at instructionRequest level
      newTotalReceivedQty += instructionRequest.getEnteredQty();
    }

    LOGGER.info(
        "Max quantity allowed: {} and New Quantity value will be: {} for delivery number: {}, po number: {}, upc: {}, facility {}, correlationId: {}",
        maxTotalReceivedQty,
        newTotalReceivedQty,
        instructionRequest.getDeliveryNumber(),
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getItemUpc(),
        TenantContext.getFacilityNum(),
        TenantContext.getCorrelationId());

    return newTotalReceivedQty > maxTotalReceivedQty;
  }

  public static Instruction createInstructionForOverageReceiving() {
    Instruction instruction = new Instruction();
    instruction.setInstructionCode(WFSConstants.OVERAGE_RECEIVING_INSTRUCTION_CODE);
    instruction.setInstructionMsg(WFSConstants.OVERAGE_RECEIVING_INSTRUCTION_CODE);
    return instruction;
  }

  public static InstructionResponse createInstructionResponseForOverageReceiving(
      InstructionRequest instructionRequest) {
    InstructionResponse instructionResponse = new InstructionResponseImplNew();
    instructionResponse.setDeliveryStatus(instructionRequest.getDeliveryStatus());
    instructionResponse.setDeliveryDocuments(instructionRequest.getDeliveryDocuments());
    instructionResponse.setInstruction(createInstructionForOverageReceiving());
    DeliveryDocumentLine documentLine =
        instructionRequest.getDeliveryDocuments().get(0).getDeliveryDocumentLines().get(0);
    LOGGER.info(
        "OVERAGE RECEIVING: Sending instructionCode as {} for delivery number: {}, po number: {}, upc: {}, facility {}, correlationId: {}",
        WFSConstants.OVERAGE_RECEIVING_INSTRUCTION_CODE,
        instructionRequest.getDeliveryNumber(),
        documentLine.getPurchaseReferenceNumber(),
        documentLine.getItemUpc(),
        TenantContext.getFacilityNum(),
        TenantContext.getCorrelationId());
    return instructionResponse;
  }
}
