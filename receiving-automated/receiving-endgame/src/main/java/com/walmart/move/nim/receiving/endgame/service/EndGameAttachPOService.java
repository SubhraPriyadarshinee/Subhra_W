package com.walmart.move.nim.receiving.endgame.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.oms.OmsRestApiClient;
import com.walmart.move.nim.receiving.core.client.scheduler.SchedulerRestApiClient;
import com.walmart.move.nim.receiving.core.client.scheduler.model.ExternalPurchaseOrder;
import com.walmart.move.nim.receiving.core.client.scheduler.model.PoAppendRequest;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.config.AppConfig;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.model.OMSPo;
import com.walmart.move.nim.receiving.core.model.OMSPurchaseOrderResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.*;
import com.walmart.move.nim.receiving.endgame.model.AttachPurchaseOrderRequest;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

public class EndGameAttachPOService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EndGameAttachPOService.class);

  @ManagedConfiguration protected AppConfig appConfig;
  @Autowired GDMRestApiClient gdmRestApiClient;
  @Autowired OmsRestApiClient omsRestApiClient;
  @Autowired private SchedulerRestApiClient schedulerRestApiClient;
  @Autowired private EndGameDeliveryMetaDataService endGameDeliveryMetaDataService;

  public void attachPOsToDelivery(AttachPurchaseOrderRequest payload, HttpHeaders headers)
      throws ReceivingException {
    Long deliveryNumber = payload.getDeliveryNumber();
    List<String> requestPONumbers = payload.getPoNumbers();
    requestPONumbers = requestPONumbers.stream().distinct().collect(Collectors.toList());
    LOGGER.info(START_ATTACHING_PO, requestPONumbers, deliveryNumber);
    DeliveryMetaData deliveryMetaData =
        endGameDeliveryMetaDataService.getDeliveryMetaData(deliveryNumber);
    requestPONumbers = filterAttachedPurchaseOrders(requestPONumbers, deliveryMetaData);
    Delivery delivery;
    try {
      delivery = gdmRestApiClient.getDeliveryWithDeliveryResponse(deliveryNumber, headers);
      List<String> missingPONumbers = findMissingPOsInDelivery(delivery, requestPONumbers);
      if (missingPONumbers.isEmpty()) return;
      List<ExternalPurchaseOrder> missingPOs =
          populateMissingExternalPurchaseOrders(
              missingPONumbers, checkWFSType28delivery(delivery), headers);
      if (!missingPOs.isEmpty()) {
        prepareAndSendAddPoRequest(deliveryNumber.toString(), missingPOs, headers);
        String appendMissingPOs =
            missingPOs
                .stream()
                .map(ExternalPurchaseOrder::getPoNumber)
                .collect(Collectors.joining(PIPE_DELIMITER));
        if (StringUtils.hasLength(deliveryMetaData.getAttachedPoNumbers())) {
          deliveryMetaData.setAttachedPoNumbers(
              deliveryMetaData.getAttachedPoNumbers() + PIPE_DELIMITER + appendMissingPOs);
        } else {
          deliveryMetaData.setAttachedPoNumbers(appendMissingPOs);
        }
        endGameDeliveryMetaDataService.updateAttachPoInfoInDeliveryMetaData(deliveryMetaData);
      }
    } catch (Exception e) {
      LOGGER.error(FAILED_ATTACH_PO_EXCEPTION, requestPONumbers, deliveryNumber, getStackTrace(e));
      throw new ReceivingException(
          FAILED_ATTACH_PO_EXCEPTION, HttpStatus.NOT_FOUND, e.getMessage());
    }
  }

  private List<String> filterAttachedPurchaseOrders(
      List<String> poNumbers, DeliveryMetaData deliveryMetaData) {
    String existingAttachedPOs = deliveryMetaData.getAttachedPoNumbers();
    if (StringUtils.hasLength(existingAttachedPOs)) {
      HashSet<String> existingAttachedPOset =
          new HashSet<>(Arrays.asList(existingAttachedPOs.split("\\" + PIPE_DELIMITER)));
      return poNumbers
          .stream()
          .filter(poNum -> !existingAttachedPOset.contains(poNum))
          .collect(Collectors.toList());
    }
    return poNumbers;
  }

  private List<ExternalPurchaseOrder> populateMissingExternalPurchaseOrders(
      List<String> missingPONumbers, Boolean isWFSType28Delivery, HttpHeaders headers) {
    List<ExternalPurchaseOrder> missingPOs = new ArrayList<>();
    for (String missingPO : missingPONumbers) {
      PurchaseOrder gdmPoDetails = fetchPODetailsFromGDM(missingPO);
      OMSPurchaseOrderResponse omsPoDetails = null;
      Boolean isWFSPOType28 = false;

      if (Objects.nonNull(gdmPoDetails)) {
        isWFSPOType28 = isWFSPOType28(gdmPoDetails);

      } else {
        omsPoDetails = fetchPODetailsFromOMS(missingPO, headers);
        if (Objects.nonNull(omsPoDetails)) {
          isWFSPOType28 = isWFSPOType28(omsPoDetails);
        }
      }
      if (isNull(gdmPoDetails) && isNull(omsPoDetails)) continue;
      if (canAttachPO(isWFSType28Delivery, isWFSPOType28)) {
        missingPOs.add(
            ExternalPurchaseOrder.builder()
                .poNumber(missingPO)
                .poCaseQty(DEFAULT_PO_CASE_QUANTITY)
                .build());
      }
    }
    return missingPOs;
  }

  private static boolean canAttachPO(Boolean isWFSType28Delivery, Boolean isWFSPOType28) {
    return isWFSType28Delivery.equals(isWFSPOType28);
  }

  /**
   * 3P & type 28 PO can only be added to a delivery which has only 3P type 28 POs attached
   *
   * @param delivery
   * @return
   */
  private Boolean checkWFSType28delivery(Delivery delivery) {
    return isWFSPOType28(delivery.getPurchaseOrders().get(0));
  }

  private List<String> findMissingPOsInDelivery(Delivery delivery, List<String> scannedPONumbers) {
    List<String> deliveryPONumbers =
        delivery
            .getPurchaseOrders()
            .stream()
            .map(PurchaseOrder::getPoNumber)
            .collect(Collectors.toList());
    scannedPONumbers.removeAll(deliveryPONumbers);
    return scannedPONumbers;
  }

  private PurchaseOrder fetchPODetailsFromGDM(String poNumber) {
    try {
      return gdmRestApiClient.getPurchaseOrder(poNumber);
    } catch (Exception e) {
      LOGGER.error(GDM_FETCH_PO_DETAILS_ISSUE, poNumber, getStackTrace(e));
      return null;
    }
  }

  private OMSPurchaseOrderResponse fetchPODetailsFromOMS(String poNumber, HttpHeaders headers) {
    OMSPurchaseOrderResponse omsPurchaseOrderResponse =
        omsRestApiClient.getPODetailsFromOMS(poNumber);
    if (nonNull(omsPurchaseOrderResponse)
        && !omsPurchaseOrderResponse
            .getOMPSRJ()
            .getData()
            .get(0)
            .getOmspo()
            .getDcnbr()
            .equals(headers.getFirst(FACILITY_NUM))) {
      LOGGER.error(OMS_PO_DETAILS_WRONG_NODE, headers.getFirst(FACILITY_NUM), poNumber);
      return null;
    }

    return omsPurchaseOrderResponse;
  }

  private Boolean isWFSPOType28(PurchaseOrder po) {
    return po.getPoNumber().contains(GDM)
        && nonNull(po.getLegacyType())
        && po.getLegacyType().equals(TYPE_28);
  }

  private Boolean isWFSPOType28(OMSPurchaseOrderResponse po) {
    OMSPo omsPo = po.getOMPSRJ().getData().get(0).getOmspo();
    return nonNull(omsPo.getXrefponbr())
        && omsPo.getXrefponbr().contains(GDM)
        && nonNull(omsPo.getSubpoext())
        && omsPo.getSubpoext().get(0).getPotypecd().equals(TYPE_28);
  }

  private void prepareAndSendAddPoRequest(
      String deliveryId, List<ExternalPurchaseOrder> missingPOs, HttpHeaders requestHeaders) {
    PoAppendRequest requestBody = new PoAppendRequest();
    requestBody.setDeliveryId(deliveryId);
    requestBody.setExternalPurchaseOrderList(missingPOs);
    requestBody.setSource(RECEIVING);
    schedulerRestApiClient.appendPoToDelivery(requestBody, requestHeaders);
  }
}
