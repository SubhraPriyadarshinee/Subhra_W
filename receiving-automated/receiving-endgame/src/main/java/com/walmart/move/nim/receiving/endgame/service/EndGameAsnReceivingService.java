package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.endgame.constants.EndgameConstants.ASN_PO_PO_LINE_EXHAUSTED_ERROR_MSG;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.AUDIT_CHECK_REQUIRED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.INVALID_GDM_ASN_RESPONSE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.MULTIPLE_SELLER_ASSOCIATED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ASNFailureReasonCode.POLINE_EXHAUSTED;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.DECANT_API;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.WFS_AUDIT_CHECK_ENABLED;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingConflictException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.message.common.PackItemData;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.Pair;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryEachesResponse;
import com.walmart.move.nim.receiving.core.model.gdm.GdmAsnDeliveryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.SsccScanResponse;
import com.walmart.move.nim.receiving.endgame.common.EndGameUtils;
import com.walmart.move.nim.receiving.endgame.constants.EndgameConstants;
import com.walmart.move.nim.receiving.endgame.message.common.ScanEventData;
import com.walmart.move.nim.receiving.endgame.model.ReceiveVendorPack;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class EndGameAsnReceivingService extends EndGameReceivingService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameAsnReceivingService.class);

  /**
   * Method to receive "1 VendorPack" from ASN
   *
   * @param scanEventData
   * @return receiveVendorPack
   */
  @Override
  public ReceiveVendorPack receiveVendorPack(ScanEventData scanEventData) {
    GdmAsnDeliveryResponse asnDeliveryResponse = null;
    for (String boxId : scanEventData.getBoxIds()) {
      try {
        asnDeliveryResponse =
            endGameDeliveryService.getASNDataFromGDM(scanEventData.getDeliveryNumber(), boxId);
        if (isNull(asnDeliveryResponse)) {
          asnDeliveryResponse = linkAndGetASNData(scanEventData.getDeliveryNumber(), boxId);
        }
      } catch (Exception exception) {
        LOGGER.error(
            "Got error to get ASn data for [BoxId={}] [errorMessage={}]",
            boxId,
            exception.getMessage());
      }
      if (nonNull(asnDeliveryResponse)) break;
    }
    if (StringUtils.hasLength(scanEventData.getCaseUPC()) && isNull(asnDeliveryResponse)) {
      return super.receiveVendorPack(scanEventData);
    } else if (!validAsnResponse(asnDeliveryResponse)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), INVALID_GDM_ASN_RESPONSE);
      LOGGER.warn("Invalid response as pack or item is not present in ASN response");
      return ReceiveVendorPack.builder().build();
    }
    PackItemData packItemData = asnDeliveryResponse.getPacks().get(0).getItems().get(0);
    scanEventData.setCaseUPC(packItemData.getGtin());
    List<PurchaseOrder> purchaseOrderList = getPurchaseOrders(scanEventData);
    if (Boolean.TRUE.equals(EndGameUtils.isMultipleSellerIdInPurchaseOrders(purchaseOrderList))) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), MULTIPLE_SELLER_ASSOCIATED);
      LOGGER.debug(
          "Got multiple seller id associated for [upc={}] [deliveryNumber={}]",
          scanEventData.getCaseUPC(),
          scanEventData.getDeliveryNumber());
      return ReceiveVendorPack.builder().purchaseOrderList(purchaseOrderList).build();
    }

    List<Pair<PurchaseOrder, PurchaseOrderLine>> availablePosAndLines =
        getPurchaseOrdersAndLinesToReceive(
            purchaseOrderList, Optional.empty(), scanEventData.getDeliveryNumber());
    if (availablePosAndLines.isEmpty()) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), POLINE_EXHAUSTED);
      endGameLabelingService.saveOrUpdateLabel(scanEventData.getPreLabelData());
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(
              EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
    Integer quantity = packItemData.getInventoryDetail().getReportedQuantity().intValue();
    availablePosAndLines =
        getPurchaseOrdersAndLinesToReceive(
            purchaseOrderList, Optional.of(quantity), scanEventData.getDeliveryNumber());
    Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine =
        getSelectedPoAndLineFromSuitablePosAndLines(availablePosAndLines, packItemData)
            .orElse(null);
    if (isNull(selectedPoAndLine)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), POLINE_EXHAUSTED);
      endGameLabelingService.saveOrUpdateLabel(scanEventData.getPreLabelData());
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(
              ASN_PO_PO_LINE_EXHAUSTED_ERROR_MSG,
              scanEventData.getCaseUPC(),
              scanEventData.getDeliveryNumber()));
    }
    LOGGER.info(
        "Received asn quantity for [BoxId={}] is [quantity={}]",
        scanEventData.getBoxIds().get(0),
        quantity);
    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
            ReceivingConstants.ENABLE_ENTER_QUANTITY_1P)
        && DECANT_API.equalsIgnoreCase(scanEventData.getRequestOriginator())) {
      return getReceiveVendorPackWithPO(selectedPoAndLine);
    }
    if (auditCheckEnabledForWFS() && isAuditRequired(selectedPoAndLine, scanEventData)) {
      updatePreLabelDataReason(scanEventData.getPreLabelData(), AUDIT_CHECK_REQUIRED);
      return getReceiveVendorPackWithPO(selectedPoAndLine);
    }
    scanEventData.setShipmentId(asnDeliveryResponse.getShipments().get(0).getShipmentNumber());
    scanEventData.setBoxIds(
        Collections.singletonList(asnDeliveryResponse.getPacks().get(0).getPackNumber()));
    String quantityUOM = packItemData.getInventoryDetail().getReportedUom();
    return getReceiveVendorPack(scanEventData, selectedPoAndLine, quantity, quantityUOM);
  }

  private SsccScanResponse getShipmentPacks(String packNumber) {
    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    httpHeaders.add(
        ReceivingConstants.ACCEPT, ReceivingConstants.GDM_SHIPMENT_GET_BY_PACK_NUM_ACCEPT_TYPE_V2);
    SsccScanResponse response = endGameDeliveryService.globalPackSearch(packNumber, httpHeaders);
    if (Objects.isNull(response)
        || Objects.isNull(response.getShipments())
        || Objects.isNull(response.getPacks())
        || (response.getShipments().isEmpty() && response.getPacks().isEmpty())
        || Objects.isNull(response.getShipments().get(0))
        || Objects.isNull(response.getPacks().get(0))) {
      LOGGER.error("Pack {} not found in GDM", packNumber);

      throw new ReceivingDataNotFoundException(
          ExceptionCodes.GDM_PACK_NOT_FOUND,
          String.format(ReceivingConstants.GDM_SEARCH_PACK_NOT_FOUND, packNumber),
          packNumber);
    }
    return response;
  }

  public GdmAsnDeliveryResponse linkAndGetASNData(Long deliveryNumber, String boxId)
      throws ReceivingException {
    GdmAsnDeliveryResponse asnDeliveryResponse = null;
    SsccScanResponse response = getShipmentPacks(boxId);
    LOGGER.info("Got shipment details for [BoxId={}]", boxId);
    if (!response.getShipments().isEmpty() && !response.getPacks().isEmpty()) {
      boolean shipmentLinkSuccess =
          linkDeliveryWithShipment(String.valueOf(deliveryNumber), response);
      LOGGER.info(
          "Linking delivery with shipment is successful [shipmentLinkSuccess={}]",
          shipmentLinkSuccess);
      if (shipmentLinkSuccess) {
        asnDeliveryResponse = endGameDeliveryService.getASNDataFromGDM(deliveryNumber, boxId);
      }
    }
    return asnDeliveryResponse;
  }

  private Boolean linkDeliveryWithShipment(String deliveryNumber, SsccScanResponse response) {
    String shipmentNumber = response.getPacks().get(0).getShipmentNumber();
    String shipmentDocumentId = response.getPacks().get(0).getDocumentId();
    HttpHeaders requestHeaders = ReceivingUtils.getHeaders();
    requestHeaders.add(
        ReceivingConstants.CONTENT_TYPE,
        ReceivingConstants.GDM_LINK_DELIVERY_WITH_SHIPMENT_ACCEPT_TYPE);
    String linkResponse =
        endGameDeliveryService.linkDeliveryWithShipment(
            deliveryNumber, shipmentNumber, shipmentDocumentId, requestHeaders);
    return !StringUtils.hasText(linkResponse);
  }

  private boolean auditCheckEnabledForWFS() {
    return tenantSpecificConfigReader.isFeatureFlagEnabled(WFS_AUDIT_CHECK_ENABLED);
  }

  private boolean validAsnResponse(GdmAsnDeliveryResponse asnDeliveryResponse) {
    return nonNull(asnDeliveryResponse)
        && !CollectionUtils.isEmpty(asnDeliveryResponse.getPacks())
        && !CollectionUtils.isEmpty(asnDeliveryResponse.getPacks().get(0).getItems());
  }

  /**
   * This method is responsible for returning suitable POs and POLines.
   *
   * @param purchaseOrders
   * @return
   */
  @TimeTracing(
      component = AppComponent.ENDGAME,
      type = Type.INTERNAL,
      executionFlow = "MABD-Calc-Line-Select")
  protected List<Pair<PurchaseOrder, PurchaseOrderLine>> getPurchaseOrdersAndLinesToReceive(
      List<PurchaseOrder> purchaseOrders, Optional<Integer> eachQuantity, long deliveryNumber) {
    Map<String, Long> receivedQtyByPoAndPoLineMap = getReceivedQTYByPoAndPoLine(purchaseOrders);
    /*
     * First receive against totalOrderQty for all PO/POLine.
     * If receivedQty is equal to all the PO/POLine, then
     * start receiving against overageQtyLimit.
     */

    // Check suitable PO/POLine considering totalOrderQty
    List<Pair<PurchaseOrder, PurchaseOrderLine>> suitablePurchaseOrdersAndLines =
        findAvailablePurchaseOrdersAndLines(
            purchaseOrders,
            receivedQtyByPoAndPoLineMap,
            eachQuantity,
            Boolean.FALSE,
            deliveryNumber);
    if (!suitablePurchaseOrdersAndLines.isEmpty()) {
      return suitablePurchaseOrdersAndLines;
    }
    /*
     Check suitable PO/POLine considering totalOrderQty and overageQtyLimit
    */
    suitablePurchaseOrdersAndLines =
        findAvailablePurchaseOrdersAndLines(
            purchaseOrders,
            receivedQtyByPoAndPoLineMap,
            eachQuantity,
            Boolean.TRUE,
            deliveryNumber);
    return suitablePurchaseOrdersAndLines;
  }

  /**
   * This method is responsible to find all suitable POs/POLines
   *
   * @param purchaseOrders
   * @param qtyByPoAndPoLineMap
   * @param shouldAddOverageQtyLimit
   * @return {@link DeliveryDocumentLine}
   */
  private List<Pair<PurchaseOrder, PurchaseOrderLine>> findAvailablePurchaseOrdersAndLines(
      List<PurchaseOrder> purchaseOrders,
      Map<String, Long> qtyByPoAndPoLineMap,
      Optional<Integer> eachQty,
      boolean shouldAddOverageQtyLimit,
      long deliveryNumber) {
    List<Pair<PurchaseOrder, PurchaseOrderLine>> availablePOsAndLines = new ArrayList<>();
    for (PurchaseOrder purchaseOrder : purchaseOrders) {
      List<PurchaseOrderLine> purchaseOrderLines = purchaseOrder.getLines();
      for (PurchaseOrderLine purchaseOrderLine : purchaseOrderLines) {
        Integer maxReceiveEachQty =
            getPurchaseOrderLineMaxReceiveEachQty(purchaseOrderLine, shouldAddOverageQtyLimit);
        String key =
            purchaseOrder.getPoNumber()
                + ReceivingConstants.DELIM_DASH
                + purchaseOrderLine.getPoLineNumber();
        Long totalReceivedQty = qtyByPoAndPoLineMap.get(key);
        totalReceivedQty = Objects.isNull(totalReceivedQty) ? 0 : totalReceivedQty;

        int eachQtyToReceive =
            eachQty.isPresent()
                ? eachQty.get()
                : calculateEachQtyToReceive(
                    purchaseOrderLine, deliveryNumber, purchaseOrder.getBaseDivisionCode());

        if (totalReceivedQty + eachQtyToReceive <= maxReceiveEachQty) {
          availablePOsAndLines.add(new Pair<>(purchaseOrder, purchaseOrderLine));
        }
      }
    }
    return availablePOsAndLines;
  }

  private boolean validAsnAndDeliveryDetails(
      PackItemData packItemData, Pair<PurchaseOrder, PurchaseOrderLine> selectedPoAndLine) {
    return packItemData
            .getPurchaseOrder()
            .getPoNumber()
            .equals(selectedPoAndLine.getKey().getPoNumber())
        && selectedPoAndLine
            .getValue()
            .getItemDetails()
            .getNumber()
            .equals(packItemData.getItemNumber())
        && selectedPoAndLine
            .getValue()
            .getItemDetails()
            .getConsumableGTIN()
            .equals(packItemData.getGtin());
  }

  private Optional<Pair<PurchaseOrder, PurchaseOrderLine>>
      getSelectedPoAndLineFromSuitablePosAndLines(
          List<Pair<PurchaseOrder, PurchaseOrderLine>> suitablePosAndLines,
          PackItemData packItemData) {
    return suitablePosAndLines
        .stream()
        .filter(poAndLine -> validAsnAndDeliveryDetails(packItemData, poAndLine))
        .findFirst();
  }

  protected static void checkValidityOfSuitablePosAndLines(
      List<Pair<PurchaseOrder, PurchaseOrderLine>> suitablePosAndLines,
      String caseUpc,
      long deliverNumber) {
    if (suitablePosAndLines.isEmpty()) {
      LOGGER.error(ReceivingException.PO_LINE_EXHAUSTED);
      throw new ReceivingConflictException(
          ExceptionCodes.PO_LINE_EXHAUSTED,
          String.format(EndgameConstants.PO_PO_LINE_EXHAUSTED_ERROR_MSG, caseUpc, deliverNumber));
    }
  }

  private Map<String, Long> getReceivedQTYByPoAndPoLine(List<PurchaseOrder> purchaseOrders) {
    List<String> poNumberList = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    for (PurchaseOrder purchaseOrder : purchaseOrders) {
      poNumberList.add(purchaseOrder.getPoNumber());
      for (PurchaseOrderLine purchaseOrderLine : ListUtils.emptyIfNull(purchaseOrder.getLines())) {
        poLineNumberSet.add(purchaseOrderLine.getPoLineNumber());
      }
    }

    List<ReceiptSummaryEachesResponse> qtyByPoAndPoLineList =
        receiptService.receivedQtyByPoAndPoLineList(poNumberList, poLineNumberSet);
    Map<String, Long> receivedQtyByPoAndPoLineMap = new HashMap<>();
    for (ReceiptSummaryEachesResponse qtyByPoAndPoLine : qtyByPoAndPoLineList) {
      String key =
          qtyByPoAndPoLine.getPurchaseReferenceNumber()
              + ReceivingConstants.DELIM_DASH
              + qtyByPoAndPoLine.getPurchaseReferenceLineNumber();
      receivedQtyByPoAndPoLineMap.put(key, qtyByPoAndPoLine.getReceivedQty());
    }
    return receivedQtyByPoAndPoLineMap;
  }
}
