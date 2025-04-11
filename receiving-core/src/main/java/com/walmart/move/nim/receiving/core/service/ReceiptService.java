/** */
package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.populateValidVnpkAndWnpk;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.walmart.atlas.argus.metrics.annotations.CaptureMethodMetric;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.common.EventType;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionDescriptionConstants;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.*;
import com.walmart.move.nim.receiving.core.event.processor.summary.ReceiptSummaryProcessor;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrder;
import com.walmart.move.nim.receiving.core.model.gdm.v3.PurchaseOrderLine;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.Timed;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

/** @author a0b02ft */
@Service(ReceivingConstants.RECEIPT_SERVICE)
public class ReceiptService implements Purge {
  private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptService.class);
  @Autowired private ReceiptRepository receiptRepository;
  @Autowired private ReceiptCustomRepository receiptCustomRepository;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  /**
   * Fetches list of received quantity summary by purchase Order for delivery.
   *
   * @param deliveryNumber
   * @param uom
   * @return String
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  @CaptureMethodMetric
  public List<ReceiptSummaryResponse> getReceivedQtySummaryByPOForDelivery(
      Long deliveryNumber, String uom) {

    List<ReceiptSummaryResponse> receiptSummaryResponseList = null;

    if (uom.equals(ReceivingConstants.Uom.VNPK)) {

      ReceiptSummaryProcessor receiptSummaryProcessor =
          tenantSpecificConfigReader.getConfiguredInstance(
              TenantContext.getFacilityNum().toString(),
              ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
              ReceiptSummaryProcessor.class);
      LOGGER.info(
          "Receipt Summary Processor for facilityNum = {} is {}",
          TenantContext.getFacilityNum(),
          receiptSummaryProcessor);
      receiptSummaryResponseList =
          receiptSummaryProcessor.receivedQtySummaryInVnpkByDelivery(deliveryNumber);
    } else {
      receiptSummaryResponseList =
          receiptCustomRepository.receivedQtySummaryInEachesByDelivery(deliveryNumber);
    }

    return receiptSummaryResponseList;
  }

  /**
   * Fetches list of received quantity summary by purchase Order for delivery.
   *
   * @param deliveryNumber
   * @param headers
   * @return String
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  public ReceiptSummaryQtyByPoResponse getReceiptsSummaryByPo(
      Long deliveryNumber, HttpHeaders headers) throws ReceivingException {

    ReceiptSummaryQtyByPoResponse receiptSummaryQtyByPoResponse = null;
    ReceiptSummaryProcessor receiptSummaryProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
            ReceiptSummaryProcessor.class);
    LOGGER.info(
        "Receipt Summary Processor for facilityNum = {} is {}",
        TenantContext.getFacilityNum(),
        receiptSummaryProcessor);
    receiptSummaryQtyByPoResponse =
        receiptSummaryProcessor.getReceiptsSummaryByPo(deliveryNumber, headers);
    return receiptSummaryQtyByPoResponse;
  }

  /**
   * This method fetches total received qty by delivery numbers
   *
   * @param receiptSummaryQtyByDeliveries
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public List<ReceiptQtySummaryByDeliveryNumberResponse> getReceiptQtySummaryByDeliveries(
      ReceiptSummaryQtyByDeliveries receiptSummaryQtyByDeliveries, HttpHeaders headers)
      throws ReceivingException {

    ReceiptSummaryProcessor receiptSummaryProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
            ReceiptSummaryProcessor.class);
    return receiptSummaryProcessor.getReceiptQtySummaryByDeliveries(
        receiptSummaryQtyByDeliveries, headers);
  }

  /**
   * This method fetches total received qty by po numbers
   *
   * @param receiptSummaryQtyByPoNumbers
   * @param headers
   * @return List<ReceiptQtySummaryByPoNumbersResponse>
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public List<ReceiptQtySummaryByPoNumbersResponse> getReceiptQtySummaryByPoNumbers(
      ReceiptSummaryQtyByPos receiptSummaryQtyByPoNumbers, HttpHeaders headers)
      throws ReceivingException {

    ReceiptSummaryProcessor receiptSummaryProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
            ReceiptSummaryProcessor.class);
    return receiptSummaryProcessor.getReceiptQtySummaryByPoNumbers(
        receiptSummaryQtyByPoNumbers, headers);
  }

  /**
   * Fetches list of received quantity summary by poLine for given delivery, po number
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param headers
   * @return ReceiptSummaryQtyByPoLineResponse
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  public ReceiptSummaryQtyByPoLineResponse getReceiptsSummaryByPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, HttpHeaders headers)
      throws ReceivingException {

    ReceiptSummaryQtyByPoLineResponse receiptSummaryQtyByPoLineResponse = null;
    ReceiptSummaryProcessor receiptSummaryProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
            ReceiptSummaryProcessor.class);
    LOGGER.info(
        "Receipt Summary Processor for facilityNum = {} is {}",
        TenantContext.getFacilityNum(),
        receiptSummaryProcessor);
    receiptSummaryQtyByPoLineResponse =
        receiptSummaryProcessor.getReceiptsSummaryByPoLine(
            deliveryNumber, purchaseReferenceNumber, headers);
    return receiptSummaryQtyByPoLineResponse;
  }

  public List<Receipt> buildReceiptsFromInstruction(
      UpdateInstructionRequest instruction,
      String problemTagId,
      String userId,
      int receivedQtyInVnpk) {
    String orgUnitId = tenantSpecificConfigReader.getOrgUnitId();
    final List<Receipt> receipts = new ArrayList<>(instruction.getDeliveryDocumentLines().size());
    instruction
        .getDeliveryDocumentLines()
        .forEach(
            documentLine -> {
              Receipt receipt = new Receipt();
              receipt.setDeliveryNumber(instruction.getDeliveryNumber());
              receipt.setDoorNumber(instruction.getDoorNumber());
              receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
              receipt.setPurchaseReferenceLineNumber(documentLine.getPurchaseReferenceLineNumber());
              receipt.setQuantity(receivedQtyInVnpk);
              receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
              receipt.setVnpkQty(documentLine.getVnpkQty());
              receipt.setProblemId(problemTagId);
              receipt.setWhpkQty(documentLine.getWhpkQty());
              receipt.setEachQty(
                  ReceivingUtils.conversionToEaches(
                      receivedQtyInVnpk,
                      ReceivingConstants.Uom.VNPK,
                      documentLine.getVnpkQty(),
                      documentLine.getWhpkQty()));
              receipt.setCreateUserId(userId);
              if (nonNull(orgUnitId)) receipt.setOrgUnitId(Integer.valueOf(orgUnitId));
              receipts.add(receipt);
            });
    return receipts;
  }

  /**
   * This method prepares receipts for the given Po/PoLine and ReceivedQty. For Less Than a Case, we
   * will use UoM as PH to differentiate VendorPack (ZA) vs WarehousePack (PH) receiving. Quantity
   * will be set to ZERO to track the received Qty as 0 Vendor Pack. GDM will show shortage for Less
   * Than a case receiving.
   *
   * @param deliveryDocument
   * @param doorNumber
   * @param problemTagId
   * @param userId
   * @param receivedQty
   * @param isLessThanACase
   * @return
   */
  public List<Receipt> buildReceiptsFromInstructionWithOsdrMasterUpdate(
      DeliveryDocument deliveryDocument,
      String doorNumber,
      String problemTagId,
      String userId,
      int receivedQty,
      Boolean isLessThanACase) {
    final List<Receipt> receipts = new ArrayList<>();
    Receipt masterReceipt =
        findFirstMasterReceiptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryDocument.getDeliveryNumber(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceNumber(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber());

    if (EventType.OFFLINE_RECEIVING.equals(deliveryDocument.getEventType())) {
      offlineReceiptGeneration(
          deliveryDocument, doorNumber, problemTagId, userId, receivedQty, receipts);
    } else {
      deliveryDocument
          .getDeliveryDocumentLines()
          .forEach(
              documentLine -> {
                Receipt receipt = new Receipt();
                receipt.setDeliveryNumber(deliveryDocument.getDeliveryNumber());
                receipt.setDoorNumber(doorNumber);
                receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
                receipt.setPurchaseReferenceLineNumber(
                    documentLine.getPurchaseReferenceLineNumber());
                receipt.setInboundShipmentDocId(deliveryDocument.getAsnNumber());
                if (Boolean.TRUE.equals(isLessThanACase)) {
                  receipt.setQuantity(0);
                  receipt.setQuantityUom(ReceivingConstants.Uom.WHPK);
                  receipt.setEachQty(
                      ReceivingUtils.conversionToEaches(
                          receivedQty,
                          ReceivingConstants.Uom.WHPK,
                          documentLine.getVendorPack(),
                          documentLine.getWarehousePack()));
                } else {
                  receipt.setQuantity(receivedQty);
                  receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
                  receipt.setEachQty(
                      ReceivingUtils.conversionToEaches(
                          receivedQty,
                          ReceivingConstants.Uom.VNPK,
                          documentLine.getVendorPack(),
                          documentLine.getWarehousePack()));
                }
                receipt.setVnpkQty(documentLine.getVendorPack());
                receipt.setProblemId(problemTagId);
                receipt.setWhpkQty(documentLine.getWarehousePack());
                receipt.setCreateUserId(userId);
                if (isNull(masterReceipt)) {
                  LOGGER.info(
                      "No Master Receipt found for delivery number {}, "
                          + "purchase reference number {} and purchase reference line number {},so updating master_receipt value",
                      deliveryDocument.getDeliveryNumber(),
                      documentLine.getPurchaseReferenceNumber(),
                      documentLine.getPurchaseReferenceLineNumber());
                  receipt.setOsdrMaster(ReceivingConstants.OSDR_MASTER_RECORD_VALUE);
                  receipt.setOrderFilledQuantity(ReceivingConstants.ZERO_QTY);
                }

                receipts.add(receipt);
              });
    }
    return receipts;
  }

  public List<Receipt> buildReceiptsFromInstructionWithOsdrMasterUpdate(
      DeliveryDocument deliveryDocument,
      String doorNumber,
      String problemTagId,
      String userId,
      int receivedQtyInVnpk) {
    return buildReceiptsFromInstructionWithOsdrMasterUpdate(
        deliveryDocument, doorNumber, problemTagId, userId, receivedQtyInVnpk, false);
  }

  /**
   * For repack and break pack Offline Rcv has multiple document lines, Hence, making a separate
   * function for the flow.
   *
   * @param deliveryDocument
   * @param doorNumber
   * @param problemTagId
   * @param userId
   * @param receivedQtyInEaches
   * @param receipts
   */
  private void offlineReceiptGeneration(
      DeliveryDocument deliveryDocument,
      String doorNumber,
      String problemTagId,
      String userId,
      int receivedQtyInEaches,
      List<Receipt> receipts) {
    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryDocument.getDeliveryNumber());
    receipt.setDoorNumber(doorNumber);
    receipt.setPurchaseReferenceNumber(deliveryDocument.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(
        deliveryDocument.getDeliveryDocumentLines().get(0).getPurchaseReferenceLineNumber());
    receipt.setInboundShipmentDocId(deliveryDocument.getAsnNumber());
    receipt.setQuantity(
        ReceivingUtils.conversionToVendorPack(
            receivedQtyInEaches,
            Uom.EACHES,
            deliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack(),
            deliveryDocument.getDeliveryDocumentLines().get(0).getWarehousePack()));
    receipt.setQuantityUom(Uom.VNPK);
    receipt.setVnpkQty(deliveryDocument.getDeliveryDocumentLines().get(0).getVendorPack());
    receipt.setProblemId(problemTagId);
    receipt.setWhpkQty(deliveryDocument.getDeliveryDocumentLines().get(0).getWarehousePack());
    receipt.setEachQty(receivedQtyInEaches);
    receipt.setCreateUserId(userId);
    receipt.setOsdrMaster(ReceivingConstants.OSDR_MASTER_RECORD_VALUE);
    receipt.setOrderFilledQuantity(ReceivingConstants.ZERO_QTY);
    receipts.add(receipt);
  }

  /**
   * This method builds Receipts data for the given delivery, PO & PO Line number. If
   * Delivery/PO/POLine/ already exists, received qty will be aggregated else new receipt entry will
   * be added for the Delivery/PO/POLine combinations.
   *
   * @param autoReceiveRequest
   * @param deliveryDocumentLines
   * @param userId
   * @return
   */
  public List<Receipt> buildReceiptsWithOsdrMasterUpdate(
      AutoReceiveRequest autoReceiveRequest,
      List<DeliveryDocumentLine> deliveryDocumentLines,
      String userId) {
    final List<Receipt> receipts = new ArrayList<>();
    Receipt masterReceipt =
        findFirstMasterReceiptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            autoReceiveRequest.getDeliveryNumber(),
            autoReceiveRequest.getPurchaseReferenceNumber(),
            autoReceiveRequest.getPurchaseReferenceLineNumber());

    if (Objects.nonNull(masterReceipt)) {
      int previousReceivedQty = masterReceipt.getQuantity();
      masterReceipt.setQuantity(previousReceivedQty + autoReceiveRequest.getQuantity());
      receipts.add(masterReceipt);
      return receipts;
    }

    deliveryDocumentLines.forEach(
        deliveryDocumentLine -> {
          Receipt receipt = new Receipt();
          receipt.setDeliveryNumber(autoReceiveRequest.getDeliveryNumber());
          receipt.setDoorNumber(autoReceiveRequest.getDoorNumber());
          receipt.setPurchaseReferenceNumber(autoReceiveRequest.getPurchaseReferenceNumber());
          receipt.setPurchaseReferenceLineNumber(
              autoReceiveRequest.getPurchaseReferenceLineNumber());
          receipt.setQuantity(autoReceiveRequest.getQuantity());
          receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
          receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
          receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
          receipt.setEachQty(
              ReceivingUtils.conversionToEaches(
                  autoReceiveRequest.getQuantity(),
                  ReceivingConstants.Uom.VNPK,
                  deliveryDocumentLine.getVendorPack(),
                  deliveryDocumentLine.getWarehousePack()));
          receipt.setCreateUserId(userId);
          receipt.setOsdrMaster(ReceivingConstants.OSDR_MASTER_RECORD_VALUE);
          receipt.setOrderFilledQuantity(ReceivingConstants.ZERO_QTY);
          receipts.add(receipt);
        });
    return receipts;
  }

  public List<Receipt> buildReceiptsFromInstruction(
      UpdateInstructionRequest instruction, String problemTagId, String userId) {
    return buildReceiptsFromInstruction(
        instruction,
        problemTagId,
        userId,
        instruction.getDeliveryDocumentLines().get(0).getQuantity());
  }

  /**
   * Creates all receipts from an updateInstructionRequest object.
   *
   * @param instruction
   * @param problemTagId
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public List<Receipt> createReceiptsFromInstruction(
      UpdateInstructionRequest instruction, String problemTagId, String userId) {
    return receiptRepository.saveAll(
        buildReceiptsFromInstruction(instruction, problemTagId, userId));
  }

  /**
   * Creates all receipts from an updateInstructionRequest object and updates osdr master
   * properties.
   *
   * @param deliveryDocument
   * @param doorNumber
   * @param problemTagId
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public List<Receipt> createReceiptsFromInstructionWithOsdrMasterUpdate(
      DeliveryDocument deliveryDocument,
      String doorNumber,
      Integer receivedQty,
      String problemTagId,
      String userId) {
    return receiptRepository.saveAll(
        buildReceiptsFromInstructionWithOsdrMasterUpdate(
            deliveryDocument, doorNumber, problemTagId, userId, receivedQty));
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> createReceiptsFromUpdateInstructionRequestWithOsdrMaster(
      UpdateInstructionRequest instruction, HttpHeaders httpHeaders) {
    final List<DocumentLine> deliveryDocumentLines = instruction.getDeliveryDocumentLines();
    DocumentLine documentLine = deliveryDocumentLines.get(0);
    int receivedQtyInVnpk = documentLine.getQuantity();
    final List<Receipt> newReceipts = new ArrayList<>(deliveryDocumentLines.size());
    final Long deliveryNumber = instruction.getDeliveryNumber();
    final String purchaseReferenceNumber = documentLine.getPurchaseReferenceNumber();
    final Integer lineNumber = documentLine.getPurchaseReferenceLineNumber();
    Receipt newReceipt = new Receipt();
    newReceipt.setDeliveryNumber(deliveryNumber);
    newReceipt.setDoorNumber(instruction.getDoorNumber());
    newReceipt.setPurchaseReferenceNumber(purchaseReferenceNumber);
    newReceipt.setPurchaseReferenceLineNumber(lineNumber);
    newReceipt.setQuantity(receivedQtyInVnpk);
    newReceipt.setQuantityUom(Uom.VNPK);
    newReceipt.setVnpkQty(documentLine.getVnpkQty());
    newReceipt.setWhpkQty(documentLine.getWhpkQty());
    newReceipt.setEachQty(
        ReceivingUtils.conversionToEaches(
            receivedQtyInVnpk, Uom.VNPK, documentLine.getVnpkQty(), documentLine.getWhpkQty()));
    newReceipt.setCreateUserId(httpHeaders.getFirst(USER_ID_HEADER_KEY));
    if (isNotBlank(httpHeaders.getFirst(ORG_UNIT_ID_HEADER))) {
      newReceipt.setOrgUnitId(Integer.valueOf(httpHeaders.getFirst(ORG_UNIT_ID_HEADER)));
    }
    newReceipts.add(newReceipt);
    return newReceipts;
  }

  public void updateRejects(
      Integer rejectQty,
      String rejectQtyUOM,
      String rejectReasonCode,
      String rejectComments,
      Receipt receipt) {
    receipt.setFbRejectedQty(rejectQty);
    receipt.setFbRejectedQtyUOM(rejectQtyUOM);
    receipt.setFbRejectedReasonCode(
        !isEmpty(rejectReasonCode) ? OSDRCode.valueOf(OSDRCode.class, rejectReasonCode) : null);
    receipt.setFbRejectionComment(rejectComments);
  }

  public void updateDamages(
      Integer damageQty,
      String damageQtyUOM,
      String damageReasonCode,
      String damageClaimType,
      Receipt receipt) {
    receipt.setFbDamagedQty(damageQty);
    receipt.setFbDamagedQtyUOM(damageQtyUOM);
    receipt.setFbDamagedReasonCode(
        !isEmpty(damageReasonCode) ? OSDRCode.valueOf(OSDRCode.class, damageReasonCode) : null);
    receipt.setFbDamagedClaimType(damageClaimType);
  }

  /**
   * Creates all receipts from an autoReceiveRequest object and updates osdr master properties.
   *
   * @param autoReceiveRequest
   * @param deliveryDocumentLines
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public List<Receipt> createReceiptsWithOsdrMasterUpdate(
      AutoReceiveRequest autoReceiveRequest,
      List<DeliveryDocumentLine> deliveryDocumentLines,
      String userId) {
    return receiptRepository.saveAll(
        buildReceiptsWithOsdrMasterUpdate(autoReceiveRequest, deliveryDocumentLines, userId));
  }

  /**
   * Update Keyed Quantity for Master Record in Receipts when container is putAway complete
   *
   * @param container
   * @return receipt
   */
  @Transactional(readOnly = true)
  public Receipt updateOrderFilledQuantityInReceipts(Container container) {
    ContainerItem containerItem = container.getContainerItems().get(0);
    Receipt osdrMasterReceipt =
        findFirstMasterReceiptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            container.getDeliveryNumber(),
            containerItem.getPurchaseReferenceNumber(),
            containerItem.getPurchaseReferenceLineNumber());
    if (Objects.nonNull(osdrMasterReceipt)) {
      LOGGER.info(
          "Found master receipt, updating order filled quantity for deliveryNumber:{}, PO: {} and POL: {}",
          osdrMasterReceipt.getDeliveryNumber(),
          osdrMasterReceipt.getPurchaseReferenceNumber(),
          osdrMasterReceipt.getPurchaseReferenceLineNumber());
      Integer containerItemQtyInVnpk =
          ReceivingUtils.conversionToVendorPack(
              containerItem.getQuantity(),
              containerItem.getQuantityUOM(),
              containerItem.getVnpkQty(),
              containerItem.getWhpkQty());
      LOGGER.info(
          "PutAway is completed for container with trackingId:{} so updating order filled quantity with {} in receipt",
          container.getTrackingId(),
          containerItemQtyInVnpk);
      osdrMasterReceipt.setOrderFilledQuantity(
          osdrMasterReceipt.getOrderFilledQuantity() + containerItemQtyInVnpk);
    } else {
      LOGGER.info(
          "No master receipt found for deliveryNumber:{}, PO: {} and POL: {}",
          container.getDeliveryNumber(),
          containerItem.getPurchaseReferenceNumber(),
          containerItem.getPurchaseReferenceLineNumber());
    }
    return osdrMasterReceipt;
  }

  /**
   * Fetches quantity received by purchase reference number and purchase reference number.
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return Long received quantity in VNPK
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "GetByPOPOLine")
  @Transactional
  @InjectTenantFilter
  public Long getReceivedQtyByPoAndPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    ReceiptSummaryQtyByPoAndPoLineResponse currentReceiveQuantitySummary =
        receiptCustomRepository.receivedQtyByPoAndPoLine(
            purchaseReferenceNumber, purchaseReferenceLineNumber);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  /**
   * Fetches quantity received by purchase reference number for given delivery number
   *
   * @param deliveryNumber
   * @return List<ReceiptSummaryResponse>
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "getReceivedQtySummaryByPoInVnpk")
  @Transactional
  @InjectTenantFilter
  public List<ReceiptSummaryResponse> getReceivedQtySummaryByPoInVnpk(Long deliveryNumber) {
    return receiptCustomRepository.receivedQtySummaryByPoInVnpkByDelivery(deliveryNumber);
  }

  /**
   * Fetches quantity received by poLine for given delivery number, po number
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return List<ReceiptSummaryResponse>
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "getReceivedQtySummaryByPoLineInVnpk")
  @Transactional
  @InjectTenantFilter
  public List<ReceiptSummaryResponse> getReceivedQtySummaryByPoLineInVnpk(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return receiptCustomRepository.receivedQtySummaryByPoLineInVnpkByDelivery(
        deliveryNumber, purchaseReferenceNumber);
  }

  /**
   * Fetches quantity received by poLine for given delivery number, po number
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return List<ReceiptSummaryVnpkResponse>
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "getReceivedQtySummaryByPoLineInEaches")
  @Transactional
  @InjectTenantFilter
  public List<ReceiptSummaryVnpkResponse> getReceivedQtySummaryByPoLineInEaches(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return receiptCustomRepository.receivedQtySummaryInEachesByDeliveryAndPo(
        deliveryNumber, purchaseReferenceNumber);
  }

  /**
   * Fetches quantity received by purchase reference number and purchase reference number.
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return Long received quantity in EA
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "GetByPOPOLineInEach")
  @Transactional
  @InjectTenantFilter
  public Long getReceivedQtyByPoAndPoLineInEach(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    ReceiptSummaryQtyByPoAndPoLineResponse currentReceiveQuantitySummary =
        receiptCustomRepository.receivedQtyByPoAndPoLineInEach(
            purchaseReferenceNumber, purchaseReferenceLineNumber);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  /**
   * Fetches quantity received by problem tag id. Marking as @Deprecated and suggesting to use
   * getReceivedQtyByPoAndPoLineInEach as method name mentions units in EA though below description
   * says as VNPK the code is fetching EA and this is a dupe method.
   *
   * @param problemId
   * @return Long received quantity in VNPK
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  public Long getReceivedQtyByProblemId(String problemId) {

    ReceiptSummaryQtyByProblemIdResponse currentReceiveQuantitySummary =
        receiptCustomRepository.receivedQtyByProblemIdInVnpk(problemId);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  /**
   * Fetches quantity received by problem tag id.
   *
   * @param problemId
   * @return Long received quantity in VNPK
   * @throws Exception
   */
  @Timed(
      name = "ReceiptQueryTime",
      level1 = "uwms-receiving",
      level2 = "ReceiptService",
      level3 = "GetReceivedQtyInVnpk")
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Long getReceivedQtyByProblemIdInVnpk(String problemId) {

    ReceiptSummaryQtyByProblemIdResponse currentReceiveQuantitySummary =
        receiptCustomRepository.receivedQtyByProblemIdInVnpk(problemId);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  /**
   * Fetches quantity received by problem tag id.
   *
   * @param problemId
   * @return Long received quantity in Ea
   * @throws Exception
   */
  @Transactional
  @InjectTenantFilter
  public Long getReceivedQtyByProblemIdInEach(String problemId) {

    ReceiptSummaryQtyByProblemIdResponse currentReceiveQuantitySummary =
        receiptCustomRepository.getReceivedQtyByProblemIdInEa(problemId);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  /**
   * Creates all receipts from containerItems object.
   *
   * @param containerItems container items from GDM container response
   * @param instructionRequest instruction request from client
   * @param userId user id
   */
  @Transactional
  @InjectTenantFilter
  public void createReceiptsFromContainerItems(
      List<ContainerItemResponseData> containerItems,
      InstructionRequest instructionRequest,
      String userId) {
    final List<Receipt> receipts = new ArrayList<>(containerItems.size());
    containerItems.forEach(
        containerItem -> {
          Receipt receipt = new Receipt();
          receipt.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
          receipt.setDoorNumber(instructionRequest.getDoorNumber());
          receipt.setPurchaseReferenceNumber(
              containerItem.getPurchaseOrder().getPurchaseReferenceNumber());
          receipt.setPurchaseReferenceLineNumber(
              containerItem.getPurchaseOrder().getPurchaseReferenceLineNumber());
          receipt.setQuantity(containerItem.getItemQuantity());
          receipt.setQuantityUom(containerItem.getQuantityUOM());
          receipt.setVnpkQty(
              containerItem.getPurchaseOrder().getVendorPackQuantity() != null
                      && containerItem.getPurchaseOrder().getVendorPackQuantity() > 0
                  ? containerItem.getPurchaseOrder().getVendorPackQuantity()
                  : 1);
          receipt.setWhpkQty(
              containerItem.getPurchaseOrder().getWarehousePackQuantity() != null
                      && containerItem.getPurchaseOrder().getWarehousePackQuantity() > 0
                  ? containerItem.getPurchaseOrder().getWarehousePackQuantity()
                  : 1);
          receipt.setCreateUserId(userId);
          if (containerItem.getItemQuantity() != null
              && containerItem.getQuantityUOM() != null
              && containerItem.getPurchaseOrder().getVendorPackQuantity() != null
              && containerItem.getPurchaseOrder().getWarehousePackQuantity() != null) {
            receipt.setEachQty(
                ReceivingUtils.conversionToEaches(
                    containerItem.getItemQuantity(),
                    containerItem.getQuantityUOM(),
                    containerItem.getPurchaseOrder().getVendorPackQuantity(),
                    containerItem.getPurchaseOrder().getWarehousePackQuantity()));
          }
          receipts.add(receipt);
        });
    receiptRepository.saveAll(receipts);
  }

  /**
   * This method will delete all the receipt records which are created for integration test
   *
   * @param deliveryNumber
   * @throws ReceivingException
   */
  @Transactional
  @InjectTenantFilter
  public void deleteReceptList(Long deliveryNumber) throws ReceivingException {
    List<Receipt> receiptData = receiptRepository.findByDeliveryNumber(deliveryNumber);
    if (receiptData == null || receiptData.isEmpty()) {
      throw new ReceivingException(
          ReceivingException.RECEIPT_NOT_FOUND, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    receiptRepository.deleteAll(receiptData);
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> findByDeliveryNumber(long deliveryNumber) {
    return receiptRepository.findByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public Receipt findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPalletQtyIsNull(
      Long deliveryNumber, String purchaseReferenceNumber) {
    return receiptRepository.findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPalletQtyIsNull(
        deliveryNumber, purchaseReferenceNumber);
  }

  /**
   * Prepare receipts using container request
   *
   * @param deliveryNumber
   * @param containerRequest
   * @param userId
   * @return List<Receipt>
   */
  public List<Receipt> prepareReceipts(
      Long deliveryNumber, ContainerRequest containerRequest, String userId) {
    LOGGER.debug(
        "Enter prepareReceipts() with deliveryNumber :{} containerRequest {}",
        deliveryNumber,
        containerRequest);
    final List<Receipt> receipts = new ArrayList<>(containerRequest.getContents().size());
    containerRequest
        .getContents()
        .forEach(
            containerItem -> {
              Receipt receipt = new Receipt();
              receipt.setDeliveryNumber(deliveryNumber);
              receipt.setDoorNumber(containerRequest.getLocation());
              receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
              receipt.setPurchaseReferenceLineNumber(
                  containerItem.getPurchaseReferenceLineNumber());
              receipt.setQuantity(
                  ReceivingUtils.conversionToVendorPack(
                      containerItem.getQuantity(),
                      containerItem.getQuantityUom(),
                      containerItem.getVnpkQty(),
                      containerItem.getWhpkQty()));
              receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
              receipt.setEachQty(
                  ReceivingUtils.conversionToEaches(
                      containerItem.getQuantity(),
                      containerItem.getQuantityUom(),
                      containerItem.getVnpkQty(),
                      containerItem.getWhpkQty()));
              receipt.setVnpkQty(containerItem.getVnpkQty());
              receipt.setWhpkQty(containerItem.getWhpkQty());
              receipt.setCreateUserId(userId);
              receipts.add(receipt);
            });
    LOGGER.debug("Exit prepareReceipts() with receipts :{}", receipts);
    return receipts;
  }

  /**
   * Get receipts by po and po line
   *
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Receipt> getReceiptsByAndPoPoLine(
      String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {
    return receiptRepository.findByPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
        purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * Get and update receipt by PO,PoLine and DeliveryNumber. if Not present then create one.
   *
   * @param doorNumber
   * @param deliveryNumber
   * @param poNumber
   * @param purchaseOrderLine
   * @param quantity
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public Receipt createReceiptFromPurchaseOrderLine(
      String doorNumber,
      Long deliveryNumber,
      String poNumber,
      PurchaseOrderLine purchaseOrderLine,
      Integer quantity) {

    Receipt receipt = new Receipt();
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setDoorNumber(doorNumber);
    receipt.setPurchaseReferenceNumber(poNumber);
    receipt.setPurchaseReferenceLineNumber(purchaseOrderLine.getPoLineNumber());

    receipt.setQuantity(
        ReceivingUtils.conversionToVendorPack(
            quantity,
            purchaseOrderLine.getOrdered().getUom(),
            purchaseOrderLine.getVnpk().getQuantity(),
            purchaseOrderLine.getWhpk().getQuantity()));

    receipt.setQuantityUom(purchaseOrderLine.getOrdered().getUom());
    receipt.setVnpkQty(purchaseOrderLine.getVnpk().getQuantity());
    receipt.setWhpkQty(purchaseOrderLine.getWhpk().getQuantity());
    receipt.setEachQty(
        ReceivingUtils.conversionToEaches(
            quantity,
            purchaseOrderLine.getOrdered().getUom(),
            purchaseOrderLine.getVnpk().getQuantity(),
            purchaseOrderLine.getWhpk().getQuantity()));
    receipt.setCreateUserId(ReceivingConstants.DEFAULT_AUDIT_USER);
    return receiptRepository.save(receipt);
  }

  /**
   * This method is responsible for providing the received quantity in eaches across many POs and
   * POLines.
   *
   * @param purchaseReferenceNumberList
   * @param purchaseReferenceLineNumberSet
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryEachesResponse> receivedQtyByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyByPoAndPoLineList(
        purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryEachesResponse> receivedQtyByPoAndPoLinesAndDelivery(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyByPoAndPoLinesAndDelivery(
        deliveryNumber, purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  /**
   * This method fetches Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<Receipt> findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    return receiptRepository
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * This method fetches First Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public Receipt findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    return receiptRepository
        .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
  }

  /**
   * Saves (insert/update) given Receipt
   *
   * @param receipt
   * @return
   */
  @Transactional
  public Receipt saveReceipt(Receipt receipt) {
    return receiptRepository.save(receipt);
  }

  /**
   * Saves & Flush (insert/update) given Receipt
   *
   * @param receipt
   * @return
   */
  @Transactional
  public Receipt saveAndFlushReceipt(Receipt receipt) {
    return receiptRepository.saveAndFlush(receipt);
  }

  /**
   * SaveAll (insert/update) given Receipt
   *
   * @param receipts
   * @return
   */
  @Transactional
  public List<Receipt> saveAll(Iterable<Receipt> receipts) {
    return receiptRepository.saveAll(receipts);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyByPoAndLineListWithoutDelivery(
      List<String> poNumbers, Set<Integer> poLineNumbers, Long deliveryNumber) {
    return receiptCustomRepository.receivedQtyByPoAndPoLineListWithoutDelivery(
        poNumbers, poLineNumbers, deliveryNumber);
  }

  /**
   * This method fetches Receipt by DeliveryNumber, PurchaseReferenceNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Receipt> findByDeliveryNumberAndPurchaseReferenceNumber(
      Long deliveryNumber, String purchaseReferenceNumber) {

    return receiptRepository.findByDeliveryNumberAndPurchaseReferenceNumber(
        deliveryNumber, purchaseReferenceNumber);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Long getReceivedQtyByPoAndDeliveryNumber(
      String purchaseReferenceNumber, Long deliveryNumber) {

    ReceiptSummaryQtyByPoAndDeliveryResponse currentReceiveQuantitySummary =
        receiptCustomRepository.receivedQtyByPoAndDeliveryNumber(
            purchaseReferenceNumber, deliveryNumber);

    return (currentReceiveQuantitySummary != null)
        ? currentReceiveQuantitySummary.getReceivedQty()
        : 0;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Map<String, Pair<Long, Long>> getReceivedQtyAndPalletQtyByPoAndDeliveryNumber(
      Set<String> purchaseReferenceNumbers, Long deliveryNumber) {

    List<ReceiptSummaryResponse> currentReceiveQuantitySummaryList =
        receiptCustomRepository.receivedQtyAndPalletQtyByPoAndDeliveryNumber(
            purchaseReferenceNumbers, deliveryNumber);

    Map<String, Pair<Long, Long>> poPalletCaseQtyMap = new HashMap<>();

    if (Objects.nonNull(currentReceiveQuantitySummaryList)) {
      currentReceiveQuantitySummaryList.forEach(
          currentReceiveQuantitySummary -> {
            poPalletCaseQtyMap.put(
                currentReceiveQuantitySummary.getPurchaseReferenceNumber(),
                new Pair<>(
                    currentReceiveQuantitySummary.getReceivedQty(),
                    currentReceiveQuantitySummary.getPalletQty()));
          });
    }
    return poPalletCaseQtyMap;
  }
  /**
   * This method checks if the Purchase Order for the given delivery and Purchase Reference Number
   * is finalized or not.
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public boolean isPOFinalized(String deliveryNumber, String purchaseReferenceNumber) {

    return receiptRepository.getFinalizedReceiptCountByDeliveryAndPoRefNumber(
            deliveryNumber, purchaseReferenceNumber)
        != 0;
  }

  /**
   * This method fetches OSDR Master Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Receipt
      findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber) {

    return receiptRepository
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMaster(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, 1);
  }

  /**
   * This method fetches first OSDR Master Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber in ascending order
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Receipt
      findFirstMasterReceiptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          Integer purchaseReferenceLineNumber) {
    return receiptRepository
        .findFirstByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndOsdrMasterOrderByCreateTsAsc(
            deliveryNumber,
            purchaseReferenceNumber,
            purchaseReferenceLineNumber,
            ReceivingConstants.OSDR_MASTER_RECORD_VALUE);
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines.
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Long receivedQtyByDeliveryPoAndPoLine(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        receiptCustomRepository.receivedQtyByDeliveryPoAndPoLine(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);

    return isNull(receiptSummaryQtyByPoAndPoLineResponse)
        ? 0l
        : receiptSummaryQtyByPoAndPoLineResponse.getReceivedQty();
  }

  /**
   * This method is responsible for providing the received quantity across many Po's and PoLines in
   * eaches.
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Long receivedQtyByDeliveryPoAndPoLineInEaches(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    ReceiptSummaryQtyByPoAndPoLineResponse receiptSummaryQtyByPoAndPoLineResponse =
        receiptCustomRepository.receivedQtyByDeliveryPoAndPoLineInEaches(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);

    return isNull(receiptSummaryQtyByPoAndPoLineResponse)
        ? 0l
        : receiptSummaryQtyByPoAndPoLineResponse.getReceivedQty();
  }

  /**
   * This method fetches OSDR Master Receipt by DeliveryNumber, PurchaseReferenceNumber and
   * PurchaseReferenceLineNumber
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @return
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Receipt> findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(
      Long deliveryNumber, String purchaseReferenceNumber) {

    return receiptRepository.findByDeliveryNumberAndPurchaseReferenceNumberAndOsdrMaster(
        deliveryNumber, purchaseReferenceNumber, 1);
  }

  /**
   * This method fetches OSDR Master Receipt by DeliveryNumber
   *
   * @param deliveryNumber
   * @return receipts
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Receipt> findMasterReceiptByDeliveryNumber(Long deliveryNumber) {
    return receiptRepository.findByDeliveryNumberAndOsdrMaster(deliveryNumber, 1);
  }

  /**
   * This method fetches finalized OSDR Master Receipt by DeliveryNumber
   *
   * @param deliveryNumber
   * @return receipts
   */
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<Receipt> findFinalizedReceiptsFor(Long deliveryNumber) {
    return receiptRepository
        .findByDeliveryNumberAndOsdrMasterAndFinalizedUserIdIsNotNullAndFinalizeTsIsNotNull(
            deliveryNumber, 1);
  }

  /**
   * Prepare the master receipt
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @param receivingCountSummary
   * @return Receipt
   */
  public Receipt prepareMasterReceipt(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      ReceivingCountSummary receivingCountSummary) {

    Receipt receipt = new Receipt();
    receipt.setCreateTs(Date.from(Instant.now()));
    receipt.setQuantity(0);
    receipt.setEachQty(0);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receipt.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
    receipt.setOsdrMaster(1);
    receipt.setFbProblemQty(0);
    receipt.setFbDamagedQty(0);
    receipt.setFbRejectedQty(0);
    receipt.setFbShortQty(0);
    receipt.setFbOverQty(0);
    receipt.setFbConcealedShortageQty(0);
    receipt =
        populateValidVnpkAndWnpk(
            receipt, receivingCountSummary, purchaseReferenceNumber, purchaseReferenceLineNumber);

    LOGGER.info("Prepared the master receipt from receivingCountSummary is {}", receipt);
    return receipt;
  }

  /**
   * Creates receipt from an updateInstructionRequest object for POCON case.
   *
   * @param instructionUpdateRequestFromClient
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public void createReceiptsFromInstructionForPOCON(
      UpdateInstructionRequest instructionUpdateRequestFromClient, String userId) {
    Receipt receipt = new Receipt();
    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
    receipt.setCreateUserId(userId);
    receipt.setDeliveryNumber(instructionUpdateRequestFromClient.getDeliveryNumber());
    receipt.setDoorNumber(instructionUpdateRequestFromClient.getDoorNumber());
    receipt.setEachQty(documentLine.getQuantity());
    receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
    receipt.setQuantity(documentLine.getQuantity());
    receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
    receipt.setVnpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
    receipt.setWhpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
    receiptRepository.save(receipt);
  }

  @Transactional
  @InjectTenantFilter
  public Receipt createAndCompleteReceiptsFromInstructionForPOCON(
      UpdateInstructionRequest instructionUpdateRequestFromClient, String userId) {
    Receipt receipt = new Receipt();
    DocumentLine documentLine =
        instructionUpdateRequestFromClient.getDeliveryDocumentLines().get(0);
    receipt.setCreateUserId(userId);
    receipt.setDeliveryNumber(instructionUpdateRequestFromClient.getDeliveryNumber());
    receipt.setDoorNumber(instructionUpdateRequestFromClient.getDoorNumber());
    receipt.setEachQty(documentLine.getQuantity());
    receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
    receipt.setQuantity(documentLine.getQuantity());
    receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
    receipt.setVnpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
    receipt.setWhpkQty(ReceivingConstants.PO_CON_VNPK_WHPK_QTY);
    return receipt;
  }

  /**
   * Creates receipt from an updateInstructionRequest object for POCON case.
   *
   * @param instructionResponse
   * @param userId
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public void createReceiptsFromInstructionForNonNationalPo(
      Instruction instructionResponse, InstructionRequest instructionRequest, String userId) {
    List<Receipt> receipts = new ArrayList<>();
    List<DeliveryDocument> deliveryDocument = instructionRequest.getDeliveryDocuments();
    deliveryDocument.forEach(
        document -> {
          Receipt receipt = new Receipt();
          receipt.setCreateUserId(userId);
          receipt.setDeliveryNumber(instructionResponse.getDeliveryNumber());
          receipt.setDoorNumber(instructionRequest.getDoorNumber());
          receipt.setEachQty(document.getQuantity());
          receipt.setPurchaseReferenceNumber(document.getPurchaseReferenceNumber());
          receipt.setQuantity(document.getQuantity());
          receipt.setQuantityUom(ReceivingConstants.Uom.EACHES);
          receipt.setVnpkQty(ReceivingConstants.DSDC_VNPK_WHPK_QTY);
          receipt.setWhpkQty(ReceivingConstants.DSDC_VNPK_WHPK_QTY);
          receipt.setPalletQty(ReceivingConstants.NON_NATIONAL_PALLET_QTY);
          receipts.add(receipt);
        });
    receiptRepository.saveAll(receipts);
  }

  @Transactional
  @InjectTenantFilter
  public void createReceiptsFromInstructionForWFSPo(
      InstructionRequest instructionRequest, String userId) {
    List<Receipt> receipts = new ArrayList<>();

    List<DeliveryDocument> deliveryDocumentList = instructionRequest.getDeliveryDocuments();
    DeliveryDocumentLine deliveryDocumentLine =
        deliveryDocumentList.get(0).getDeliveryDocumentLines().get(0);
    Receipt receipt = new Receipt();
    receipt.setCreateUserId(userId);
    receipt.setDeliveryNumber(Long.parseLong(instructionRequest.getDeliveryNumber()));
    receipt.setDoorNumber(instructionRequest.getDoorNumber());
    receipt.setPurchaseReferenceNumber(deliveryDocumentList.get(0).getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    receipt.setQuantity(instructionRequest.getEnteredQty());
    receipt.setQuantityUom(instructionRequest.getEnteredQtyUOM());
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    receipt.setEachQty(instructionRequest.getEnteredQty());
    receipts.add(receipt);
    receiptRepository.saveAll(receipts);
  }

  @Transactional
  @InjectTenantFilter
  public void createReceiptsFromInstructionForWFSPoRIR(
      Instruction instruction, ReceiveInstructionRequest receiveInstructionRequest, String userId) {
    List<Receipt> receipts = new ArrayList<>();

    DeliveryDocumentLine deliveryDocumentLine =
        receiveInstructionRequest.getDeliveryDocumentLines().get(0);
    Receipt receipt = new Receipt();
    receipt.setCreateUserId(userId);
    receipt.setDeliveryNumber(instruction.getDeliveryNumber());
    receipt.setDoorNumber(receiveInstructionRequest.getDoorNumber());
    receipt.setPurchaseReferenceNumber(deliveryDocumentLine.getPurchaseReferenceNumber());
    receipt.setPurchaseReferenceLineNumber(deliveryDocumentLine.getPurchaseReferenceLineNumber());
    receipt.setQuantity(receiveInstructionRequest.getQuantity());
    receipt.setQuantityUom(receiveInstructionRequest.getQuantityUOM());
    receipt.setVnpkQty(deliveryDocumentLine.getVendorPack());
    receipt.setWhpkQty(deliveryDocumentLine.getWarehousePack());
    receipt.setEachQty(
        ReceivingUtils.conversionToEaches(
            instruction.getReceivedQuantity(),
            ReceivingConstants.Uom.VNPK,
            deliveryDocumentLine.getVendorPack(),
            deliveryDocumentLine.getWarehousePack()));
    receipts.add(receipt);
    receiptRepository.saveAll(receipts);
  }

  /**
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param purchaseReferenceLineNumber
   * @return
   */
  @Transactional
  @InjectTenantFilter
  public List<Receipt> getReceiptSummary(
      Long deliveryNumber, String purchaseReferenceNumber, Integer purchaseReferenceLineNumber) {

    List<Receipt> receipts;
    if (Objects.nonNull(purchaseReferenceNumber) && Objects.nonNull(purchaseReferenceLineNumber))
      receipts =
          receiptRepository
              .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                  deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    else if (Objects.nonNull(purchaseReferenceNumber))
      receipts =
          receiptRepository.findByDeliveryNumberAndPurchaseReferenceNumber(
              deliveryNumber, purchaseReferenceNumber);
    else receipts = receiptRepository.findByDeliveryNumber(deliveryNumber);
    if (CollectionUtils.isEmpty(receipts)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.RECEIPTS_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.RECEIPTS_NOT_FOUND_ERROR_MSG, deliveryNumber));
    }
    return receipts;
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> getReceiptSummary(String purchaseReferenceNumber) {
    List<Receipt> receipts =
        receiptRepository.findByPurchaseReferenceNumber(purchaseReferenceNumber);
    if (CollectionUtils.isEmpty(receipts)) {
      throw new ReceivingDataNotFoundException(
          ExceptionCodes.RECEIPTS_NOT_FOUND,
          String.format(
              ExceptionDescriptionConstants.RECEIPTS_NOT_FOUND_FOR_PO_ERROR_MSG,
              purchaseReferenceNumber));
    }
    return receipts;
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> createMasterReceiptsFromInstruction(
      UpdateInstructionRequest instruction, String problemTagId, String userId) {
    final List<Receipt> receipts = new ArrayList<>(instruction.getDeliveryDocumentLines().size());
    instruction
        .getDeliveryDocumentLines()
        .forEach(
            documentLine -> {
              Receipt receipt = new Receipt();
              receipt.setOsdrMaster(1);
              receipt.setDeliveryNumber(instruction.getDeliveryNumber());
              receipt.setDoorNumber(instruction.getDoorNumber());
              receipt.setPurchaseReferenceNumber(documentLine.getPurchaseReferenceNumber());
              receipt.setPurchaseReferenceLineNumber(documentLine.getPurchaseReferenceLineNumber());
              receipt.setQuantity(documentLine.getQuantity());
              receipt.setQuantityUom(documentLine.getQuantityUOM());
              receipt.setVnpkQty(documentLine.getVnpkQty());
              receipt.setProblemId(problemTagId);
              receipt.setWhpkQty(documentLine.getWhpkQty());
              receipt.setEachQty(
                  ReceivingUtils.conversionToEaches(
                      documentLine.getQuantity(),
                      documentLine.getQuantityUOM(),
                      documentLine.getVnpkQty(),
                      documentLine.getWhpkQty()));
              receipt.setCreateUserId(userId);
              receipts.add(receipt);
            });
    receiptRepository.saveAll(receipts);
    return receipts;
  }

  @Transactional(readOnly = true)
  public List<ReceiptSummaryByDeliveryPoResponse> getReceivedQuantitySummaryByDeliveryPo(
      Long deliveryNumber, Set<String> purchaseReferenceNumbers) {
    return receiptCustomRepository.receivedQtySummaryInVNPKByDeliveryPo(
        deliveryNumber, purchaseReferenceNumbers);
  }

  /**
   * This method is responsible for providing the received quantity in VNPK across many POs and
   * POLines.
   *
   * @param purchaseReferenceNumberList list of purchase reference number
   * @param purchaseReferenceLineNumberSet list of purchase reference line number
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInVNPKByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyInVNPKByPoAndPoLineList(
        purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInEAByPoAndPoLineList(
      List<String> purchaseReferenceNumberList, Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyInEaByPoAndPoLineList(
        purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  /**
   * This method is responsible for providing the received quantity in VNPK across many POs and
   * POLines for a delivery.
   *
   * @param purchaseReferenceNumberList list of purchase reference number
   * @param purchaseReferenceLineNumberSet list of purchase reference line number
   * @return
   */
  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInVNPKByDeliveryPoAndPoLineList(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyInVNPKByDeliveryPoAndPoLineList(
        deliveryNumber, purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> receivedQtyInEAByDeliveryPoAndPoLineList(
      Long deliveryNumber,
      List<String> purchaseReferenceNumberList,
      Set<Integer> purchaseReferenceLineNumberSet) {
    return receiptCustomRepository.receivedQtyInEaByDeliveryPoAndPoLineList(
        deliveryNumber, purchaseReferenceNumberList, purchaseReferenceLineNumberSet);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryByDeliveryNumbers(
      List<String> deliveryNumbers) {
    return receiptCustomRepository.receivedQtySummaryByDeliveryNumbers(deliveryNumbers);
  }

  @Transactional(readOnly = true)
  @InjectTenantFilter
  public List<ReceiptSummaryVnpkResponse> receivedQtySummaryByPoNumbers(List<String> poNumbers) {
    return receiptCustomRepository.receivedQtySummaryByPoNumbers(poNumbers);
  }

  @Override
  @Transactional
  public long purge(PurgeData purgeEntity, PageRequest pageRequest, int purgeEntitiesBeforeXdays) {
    List<Receipt> receiptList =
        receiptRepository.findByIdGreaterThanEqual(purgeEntity.getLastDeleteId(), pageRequest);

    Date deleteDate = getPurgeDate(purgeEntitiesBeforeXdays);

    // filter out list by validating last createTs
    receiptList =
        receiptList
            .stream()
            .filter(receipt -> receipt.getCreateTs().before(deleteDate))
            .sorted(Comparator.comparing(Receipt::getId))
            .collect(Collectors.toList());

    if (CollectionUtils.isEmpty(receiptList)) {
      LOGGER.info("Purge RECEIPT: Nothing to delete");
      return purgeEntity.getLastDeleteId();
    }
    long lastDeletedId = receiptList.get(receiptList.size() - 1).getId();

    LOGGER.info(
        "Purge RECEIPT: {} records : ID {} to {} : START",
        receiptList.size(),
        receiptList.get(0).getId(),
        lastDeletedId);
    receiptRepository.deleteAll(receiptList);
    LOGGER.info("Purge RECEIPT: END");
    return lastDeletedId;
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public RxReceiptsSummaryResponse
      getReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          int purchaseReferenceLineNumber,
          String sscc) {
    return receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
        deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, sscc);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public Receipt
      findMasterReceiptsByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          int purchaseReferenceLineNumber,
          String sscc) {

    return receiptRepository
        .findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSsccNumberAndOsdrMaster(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, sscc, 1);
  }

  @InjectTenantFilter
  @Transactional(readOnly = true)
  public long
      findReceiptsSummaryByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumberAndSSCC(
          Long deliveryNumber,
          String purchaseReferenceNumber,
          int purchaseReferenceLineNumber,
          String sscc) {
    RxReceiptsSummaryResponse receiptsSummaryResponse =
        receiptCustomRepository.getReceiptsQtySummaryByDeliveryAndPoAndPoLineAndSSCC(
            deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber, sscc);

    return isNull(receiptsSummaryResponse) ? 0 : receiptsSummaryResponse.getReceivedQty();
  }

  @Transactional
  @InjectTenantFilter
  public void deleteByDeliveryNumber(Long deliveryNumber) {
    receiptRepository.deleteByDeliveryNumber(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public Receipt findLatestReceiptByDeliveryNumber(Long deliveryNumber) {
    return receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(deliveryNumber);
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> findByDeliveryNumberIn(List<Long> deliveryNumbers) {
    return receiptRepository.findByDeliveryNumberIn(deliveryNumbers);
  }

  @Transactional
  @InjectTenantFilter
  public List<String> fetchReceiptPOsBasedOnDelivery(
      String deliveryNumber, Date osdrLastProcessedDate) {
    List<Receipt> receiptList =
        receiptRepository.findByDeliveryNumberAndCreateTsGreaterThanEqual(
            Long.valueOf(deliveryNumber), osdrLastProcessedDate);
    return receiptList
        .stream()
        .map(Receipt::getPurchaseReferenceNumber)
        .distinct()
        .collect(Collectors.toList());
  }

  @Transactional
  @InjectTenantFilter
  public List<ReceiptForOsrdProcess> fetchReceiptForOsrdProcess(Date unloadingCompleteDate) {
    return receiptRepository.fetchReceiptForOsrdProcess(unloadingCompleteDate);
  }

  @Transactional
  @InjectTenantFilter
  public List<Receipt> fetchPoReceipts(LocalDateTime from, LocalDateTime to) {
    return receiptRepository.fetchPoReceipts(from, to);
  }

  public List<DeliveryDocument> getStoreDistributionByDeliveryAndPoPoLine(
      Long deliveryNumber,
      String poNumber,
      int poLineNumber,
      HttpHeaders headers,
      boolean isAtlasItem)
      throws ReceivingException {

    ReceiptSummaryProcessor receiptSummaryProcessor =
        tenantSpecificConfigReader.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.RECEIPT_SUMMARY_PROCESSOR,
            ReceiptSummaryProcessor.class);
    LOGGER.info(
        "Delivery Document Processor for facilityNum = {} is {}",
        TenantContext.getFacilityNum(),
        receiptSummaryProcessor);

    return receiptSummaryProcessor.getStoreDistributionByDeliveryPoPoLine(
        deliveryNumber, poNumber, poLineNumber, headers, isAtlasItem);
  }

  /**
   * Preparation of build receipts from container items
   *
   * @param deliveryNumber
   * @param asnNumber
   * @param doorNumber
   * @param packNumber
   * @param userId
   * @param containerItems
   * @return
   */
  public List<Receipt> buildReceiptsFromContainerItems(
      Long deliveryNumber,
      String asnNumber,
      String doorNumber,
      String packNumber,
      String userId,
      List<ContainerItem> containerItems) {
    // filter only the child container items which has Po/PoLine details
    containerItems =
        containerItems
            .stream()
            .filter(
                containerItem ->
                    ObjectUtils.allNotNull(
                        containerItem.getPurchaseReferenceNumber(),
                        containerItem.getPurchaseReferenceLineNumber(),
                        containerItem.getQuantity()))
            .collect(Collectors.toList());
    final List<Receipt> receipts = new ArrayList<>();
    try {
      containerItems.forEach(
          containerItem -> {
            Integer receivedQtyInVnpk =
                ReceivingUtils.conversionToVendorPack(
                    containerItem.getQuantity(),
                    Uom.EACHES,
                    containerItem.getVnpkQty(),
                    containerItem.getWhpkQty());
            Receipt receipt = new Receipt();
            receipt.setDeliveryNumber(deliveryNumber);
            receipt.setDoorNumber(doorNumber);
            receipt.setPurchaseReferenceNumber(containerItem.getPurchaseReferenceNumber());
            receipt.setPurchaseReferenceLineNumber(containerItem.getPurchaseReferenceLineNumber());
            receipt.setInboundShipmentDocId(asnNumber);
            receipt.setQuantity(receivedQtyInVnpk);
            receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
            receipt.setVnpkQty(containerItem.getVnpkQty());
            receipt.setWhpkQty(containerItem.getWhpkQty());
            receipt.setEachQty(containerItem.getQuantity());
            receipt.setCreateUserId(userId);
            receipt.setSsccNumber(packNumber);
            receipts.add(receipt);
          });
    } catch (Exception ex) {
      LOGGER.info(
          "build receipts failed for delivery number {}, asn number {}, sscc number {}",
          deliveryNumber,
          asnNumber,
          packNumber);
    }
    return receipts;
  }

  /**
   * This method returns Receipt Summary Quantity for given PO and delivery number.
   *
   * @param purchaseOrders list of purchase orders
   * @param deliveryNumber delivery number
   * @return
   */
  @Transactional
  public List<ReceiptSummaryQtyByPoAndPoLineResponse> getReceiptSummaryQtyByPOandPOLineResponse(
      List<PurchaseOrder> purchaseOrders, Long deliveryNumber) {
    List<String> poNumbers = new ArrayList<>();
    Set<Integer> poLineNumberSet = new HashSet<>();
    purchaseOrders.forEach(
        purchaseOrder -> {
          poNumbers.add(purchaseOrder.getPoNumber());
          purchaseOrder.getLines().forEach(line -> poLineNumberSet.add(line.getPoLineNumber()));
        });
    return receivedQtyByPoAndLineListWithoutDelivery(poNumbers, poLineNumberSet, deliveryNumber);
  }
}
