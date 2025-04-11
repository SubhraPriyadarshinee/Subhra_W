package com.walmart.move.nim.receiving.witron.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.getForwardablHeaderWithTenantData;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.RECEIVING_INTERNAL_ERROR;
import static com.walmart.move.nim.receiving.utils.constants.DeliveryStatus.COMPLETE;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USER_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.EACHES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang.StringUtils.isNotBlank;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import com.walmart.move.nim.receiving.core.builder.ConfirmPoResponseBuilder;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOOSDRInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOReasonCode;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.repositories.ReceiptCustomRepository;
import com.walmart.move.nim.receiving.core.service.ContainerPersisterService;
import com.walmart.move.nim.receiving.core.service.DefaultCompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import com.walmart.move.nim.receiving.witron.common.GDCFlagReader;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class GdcCompleteDeliveryProcessor extends DefaultCompleteDeliveryProcessor {
  @Autowired private GDCFlagReader gDCFlagReader;
  @Autowired private OSDRCalculator osdrCalculator;
  @Autowired private GDMRestApiClient gdmRestApiClient;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;
  @Autowired private ReceiptCustomRepository receiptCustomRepository;
  @Autowired private ConfirmPoResponseBuilder confirmPoResponseBuilder;
  @Autowired private ContainerPersisterService containerPersisterService;
  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;

  private static final Logger LOGGER = getLogger(GdcCompleteDeliveryProcessor.class);

  @Override
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {
    Integer numberOfPallets = null;

    validateForOpenInstructions(deliveryNumber);
    hasAllPOsFinalized(deliveryNumber, headers);

    List<ReceiptSummaryResponse> receiptsInEaches =
        receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, EACHES);
    List<ReceiptSummaryResponse> receiptsInVnpk =
        receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, VNPK);

    if (gDCFlagReader.isIncludePalletCount()) {
      List<Container> containers =
          containerPersisterService.findContainerByDeliveryNumber(deliveryNumber);
      List<Container> validContainers =
          containers
              .stream()
              .filter(
                  container ->
                      !ReceivingConstants.STATUS_BACKOUT.equals(container.getContainerStatus()))
              .collect(Collectors.toList());
      numberOfPallets = CollectionUtils.isNotEmpty(validContainers) ? validContainers.size() : 0;
    }

    // Post receipts in eaches to GDM
    DeliveryInfo deliveryInfo =
        deliveryStatusPublisher.publishDeliveryStatus(
            deliveryNumber,
            COMPLETE.name(),
            receiptsInEaches,
            constructDeliveryCompleteHeaders(deliveryNumber, headers));

    // Send VNPK to client
    deliveryInfo.setReceipts(receiptsInVnpk);
    deliveryInfo.setNumberOfPallets(numberOfPallets);

    return deliveryInfo;
  }

  protected void hasAllPOsFinalized(Long deliveryNumber, HttpHeaders headers)
      throws ReceivingException {

    final List<Receipt> rcvPos = receiptService.findFinalizedReceiptsFor(deliveryNumber);

    Set<String> finalizedPOsInRcv =
        rcvPos.stream().map(Receipt::getPurchaseReferenceNumber).collect(Collectors.toSet());
    Set<String> gdmPOs = getGdmPOs(deliveryNumber, headers);
    gdmPOs.removeAll(finalizedPOsInRcv);

    if (gdmPOs.size() > 0) {
      LOGGER.info("for deliveryNumber={}, not finalized POs are:{}", deliveryNumber, gdmPOs);
      throw new ReceivingException(
          String.format(COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE, gdmPOs.size()),
          INTERNAL_SERVER_ERROR,
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE,
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER,
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE + " : " + gdmPOs);
    }
  }

  private Set<String> getGdmPOs(Long deliveryNumber, HttpHeaders httpHeaders)
      throws ReceivingException {
    Set<String> gdmPOs = new HashSet<>();
    try {
      DeliveryWithOSDRResponse res =
          gdmRestApiClient.getDelivery(
              deliveryNumber, getForwardablHeaderWithTenantData(httpHeaders));
      final List<PurchaseOrderWithOSDRResponse> POs = res.getPurchaseOrders();
      gdmPOs =
          POs.stream().map(PurchaseOrderWithOSDRResponse::getPoNumber).collect(Collectors.toSet());

    } catch (GDMRestApiClientException e) {
      LOGGER.error("error while getting GDM POs error=" + e.getMessage());
      throw new ReceivingException(
          String.format(
              COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE,
              gdmPOs != null ? gdmPOs.size() : "some GDM"),
          INTERNAL_SERVER_ERROR,
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE,
          COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER);
    }
    return gdmPOs;
  }

  /**
   * Completes the given delivery and POs on it
   *
   * @param deliveryNumber
   * @param httpHeaders
   * @return DeliveryInfo
   */
  @Override
  public DeliveryInfo completeDeliveryAndPO(Long deliveryNumber, HttpHeaders httpHeaders) {
    try {
      LOGGER.info(
          "GDC implementation of completeDeliveryAndPO with deliveryNumber :{}", deliveryNumber);
      // Prepare the headers
      Map<String, Object> headers = constructDeliveryCompleteHeaders(deliveryNumber, httpHeaders);
      headers.put(TENENT_FACLITYNUM, httpHeaders.getFirst(TENENT_FACLITYNUM));
      headers.put(TENENT_COUNTRY_CODE, httpHeaders.getFirst(TENENT_COUNTRY_CODE));
      headers.put(CORRELATION_ID_HEADER_KEY, httpHeaders.getFirst(CORRELATION_ID_HEADER_KEY));
      if (isNull(headers.get(ORG_UNIT_ID_HEADER))) {
        String orgUnitId = tenantSpecificConfigReader.getOrgUnitId();
        if (nonNull(orgUnitId)) headers.put(ORG_UNIT_ID_HEADER, orgUnitId);
      }

      // Finalize all purchase orders
      completeAllPOs(deliveryNumber, headers);

      // Complete the delivery
      return deliveryStatusPublisher.publishDeliveryStatus(
          deliveryNumber,
          COMPLETE.name(),
          receiptCustomRepository.receivedQtySummaryInEachesByDelivery(deliveryNumber),
          headers);
    } catch (GDMRestApiClientException gdmE) {
      LOGGER.error(
          "GDMRestApiClientException deliveryNumber :{}, stack={}",
          deliveryNumber,
          getStackTrace(gdmE));
      throw new ReceivingDataNotFoundException(
          gdmE.getErrorResponse().getErrorCode(),
          gdmE.getErrorResponse().getErrorMessage().toString());
    } catch (Exception e) {
      LOGGER.error(
          "General Exception deliveryNumber :{}, stack={}", deliveryNumber, getStackTrace(e));
      throw new ReceivingBadDataException(
          RECEIVING_INTERNAL_ERROR,
          String.format(COMPLETE_DELIVERY_DEFAULT_ERROR_MESSAGE, deliveryNumber, e.getMessage()));
    }
  }

  /**
   * Close all purchase orders
   *
   * @param deliveryNumber
   * @param headers
   * @throws GDMRestApiClientException
   */
  private void completeAllPOs(Long deliveryNumber, Map<String, Object> headers)
      throws GDMRestApiClientException {
    // Get delivery details from GDM
    DeliveryWithOSDRResponse delivery = gdmRestApiClient.getDelivery(deliveryNumber, headers);

    // Pull all the receipts from DB
    List<Receipt> receipts = receiptService.findByDeliveryNumber(deliveryNumber);

    // Summarize receipts by POPOLineKey
    Map<POPOLineKey, Long> receivedQtyMap =
        osdrRecordCountAggregator.getAggregateReceiveQtyForPoPolineList(receipts);

    // Prepare OSDR master receipt per POPOLineKey
    Map<POPOLineKey, Receipt> masterReceiptMap = prepareMasterReceipts(receipts);

    for (PurchaseOrderWithOSDRResponse po : delivery.getPurchaseOrders()) {
      String poNbr = po.getPoNumber();
      // Ignore the finalized PO
      if (EVENT_TYPE_FINALIZED.equalsIgnoreCase(po.getOperationalInfo().getState())) {
        LOGGER.info("PO: {} already FINALIZED in GDM so skipping it", poNbr);
        continue;
      }

      // Initialize the data
      ReceivingCountSummary poLevelSummary = new ReceivingCountSummary();
      List<FinalizePOLine> gdmFinalizePOLineList = new ArrayList<>();
      List<DCFinPOCloseDocumentLineRequestBody> dcfinPoCloseDocumentLineList = new ArrayList<>();
      for (PurchaseOrderLineWithOSDRResponse poLine : po.getLines()) {
        Integer poLineNbr = poLine.getPoLineNumber();
        POPOLineKey poPoLineKey = new POPOLineKey(poNbr, poLineNbr);
        Receipt masterReceipt = masterReceiptMap.get(poPoLineKey);
        int receivedQty =
            receivedQtyMap.get(poPoLineKey) == null
                ? 0
                : receivedQtyMap.get(poPoLineKey).intValue();
        LOGGER.info(
            "Working on DELIVERY: {} PO: {} LINE: {} ReceivedQty: {}",
            deliveryNumber,
            poNbr,
            poLineNbr,
            receivedQty);

        // Create OSDR master if it's not exists
        createMasterReceiptIfNeeded(masterReceipt, deliveryNumber, poNbr, poLine, headers);

        // Prepare PoLine level summary
        ReceivingCountSummary poLineLevelSummary = preparePoLineSummary(poLine, receivedQty);

        // Update PO level ReceivedQty
        updatePoLevelTotalQty(poLevelSummary, poLine.getFreightBillQty(), receivedQty);

        // Handle rejects
        if (masterReceipt != null && masterReceipt.getFbRejectedQty() != null) {
          // Update PoLine RejectedQty
          poLineLevelSummary.setRejectedQty(masterReceipt.getFbRejectedQty());
          poLineLevelSummary.setRejectedQtyUOM(masterReceipt.getFbRejectedQtyUOM());
          poLineLevelSummary.setRejectedComment(masterReceipt.getFbRejectionComment());
          if (masterReceipt.getFbRejectedReasonCode() != null) {
            poLineLevelSummary.setRejectedReasonCode(
                masterReceipt.getFbRejectedReasonCode().getCode());
          }

          // Update PO level RejectedQty
          updatePoLevelRejectQty(poLevelSummary, masterReceipt.getFbRejectedQty());
        }

        // Handle damages
        if (masterReceipt != null && masterReceipt.getFbDamagedQty() != null) {
          // Update PoLine DamageQty
          poLineLevelSummary.setDamageQty(masterReceipt.getFbDamagedQty());
          poLineLevelSummary.setDamageQtyUOM(masterReceipt.getFbDamagedQtyUOM());
          poLineLevelSummary.setDamageClaimType(masterReceipt.getFbDamagedClaimType());
          if (masterReceipt.getFbDamagedReasonCode() != null) {
            poLineLevelSummary.setDamageReasonCode(
                masterReceipt.getFbDamagedReasonCode().getCode());
          }

          // Update PO level RejectedQty
          updatePoLevelDamageQty(poLevelSummary, masterReceipt.getFbDamagedQty());
        }

        // Calculate OSDR at PoLine level
        osdrCalculator.calculate(poLineLevelSummary);

        // Prepare GDM payload at PoLine level
        FinalizePOLine gdmFinalizePOLine = prepareGdmFinalizePOLine(poLineNbr, poLineLevelSummary);
        gdmFinalizePOLineList.add(gdmFinalizePOLine);

        // Prepare DCFIN payload at PoLine level
        DCFinPOCloseDocumentLineRequestBody dcfinPoCloseDocumentLine =
            prepareDcfinFinalizePOLine(poLineNbr, receivedQty);
        dcfinPoCloseDocumentLineList.add(dcfinPoCloseDocumentLine);
      }

      // Calculate OSDR at PO level
      osdrCalculator.calculate(poLevelSummary);

      // Prepare GDM payload at PO level
      FinalizePORequestBody gdmPoFinalizePayload =
          prepareFinalizePoForGDM(poLevelSummary, gdmFinalizePOLineList, headers);

      // Prepare DCFIN payload at PO level
      DCFinPOCloseRequestBody dcfinPoClosePayload =
          preparePoCloseForDCFIN(deliveryNumber, po, dcfinPoCloseDocumentLineList, headers);

      completePO(deliveryNumber, poNbr, gdmPoFinalizePayload, dcfinPoClosePayload, headers);
    }
  }

  private void completePO(
      Long deliveryNumber,
      String poNbr,
      FinalizePORequestBody gdmPoFinalizePayload,
      DCFinPOCloseRequestBody dcfinPoClosePayload,
      Map<String, Object> headers)
      throws GDMRestApiClientException {
    // Submit PO finalization to GDM
    gdmRestApiClient.finalizePurchaseOrder(deliveryNumber, poNbr, gdmPoFinalizePayload, headers);

    // RCV finalize the OSDR master receipt
    finalizeAllMasterReceipts(
        deliveryNumber, poNbr, headers.get(USER_ID_HEADER_KEY).toString(), new Date());

    // Submit PO close to DCFIN
    dcFinRestApiClient.poCloseAsync(dcfinPoClosePayload, headers);
  }

  private void updatePoLevelRejectQty(ReceivingCountSummary poLevelSummary, Integer rejectedQty) {
    poLevelSummary.addRejectedQty(rejectedQty);
    poLevelSummary.setRejectedQtyUOM(VNPK);
  }

  private void updatePoLevelDamageQty(ReceivingCountSummary poLevelSummary, Integer damageQty) {
    poLevelSummary.addDamageQty(damageQty);
    poLevelSummary.setDamageQtyUOM(VNPK);
  }

  private void updatePoLevelTotalQty(
      ReceivingCountSummary poLevelSummary, Integer freightBillQty, int receivedQty) {
    poLevelSummary.addReceiveQty(receivedQty);
    poLevelSummary.addTotalFBQty(freightBillQty);
  }

  private ReceivingCountSummary preparePoLineSummary(
      PurchaseOrderLineWithOSDRResponse poLine, int receivedQty) {
    ReceivingCountSummary lineLevelSummary = new ReceivingCountSummary();
    lineLevelSummary.setTotalFBQty(poLine.getFreightBillQty());
    lineLevelSummary.setTotalFBQtyUOM(poLine.getOrdered().getUom());
    lineLevelSummary.setReceiveQty(receivedQty);
    lineLevelSummary.setReceiveQtyUOM(VNPK);
    lineLevelSummary.setVnpkQty(poLine.getVnpk().getQuantity());
    lineLevelSummary.setWhpkQty(poLine.getWhpk().getQuantity());

    return lineLevelSummary;
  }

  private DCFinPOCloseRequestBody preparePoCloseForDCFIN(
      Long deliveryNumber,
      PurchaseOrderWithOSDRResponse po,
      List<DCFinPOCloseDocumentLineRequestBody> dcfinPoCloseDocumentLineList,
      Map<String, Object> headers) {
    DCFinPOCloseDocumentRequestBody document = new DCFinPOCloseDocumentRequestBody();
    document.setDocumentNum(po.getPoNumber());
    document.setDocType(po.getLegacyType().toString());
    document.setDeliveryNum(deliveryNumber.toString());
    document.setDeliveryGateInTs(ReceivingUtils.dateConversionToUTC(new Date()));
    document.setDocumentClosed(true);
    document.setFreightBillQty(po.getTotalBolFbq());
    document.setFreightBillQtyUom(VNPK);
    document.setDocumentLineItems(dcfinPoCloseDocumentLineList);

    DCFinPOCloseRequestBody dcfinPoClosePayload = new DCFinPOCloseRequestBody();
    dcfinPoClosePayload.setTxnId(
        headers.getOrDefault(CORRELATION_ID_HEADER_KEY, UUID.randomUUID()).toString()
            + "-"
            + po.getPoNumber());
    dcfinPoClosePayload.setDocument(document);

    return dcfinPoClosePayload;
  }

  private Map<POPOLineKey, Receipt> prepareMasterReceipts(List<Receipt> receipts) {
    Map<POPOLineKey, Receipt> masterReceiptMap = new HashMap<>();
    for (Receipt receipt : receipts) {
      if (nonNull(receipt.getOsdrMaster()) && receipt.getOsdrMaster() == 1) {
        POPOLineKey poPoLineKey =
            new POPOLineKey(
                receipt.getPurchaseReferenceNumber(), receipt.getPurchaseReferenceLineNumber());
        masterReceiptMap.put(poPoLineKey, receipt);
      }
    }

    return masterReceiptMap;
  }

  private FinalizePORequestBody prepareFinalizePoForGDM(
      ReceivingCountSummary poSummary,
      List<FinalizePOLine> gdmFinalizePOLineList,
      Map<String, Object> headers) {
    FinalizePORequestBody finalizePORequestBody = new FinalizePORequestBody();
    finalizePORequestBody.setReasonCode(FinalizePOReasonCode.PURCHASE_ORDER_FINALIZE);
    finalizePORequestBody.setUserId(headers.get(USER_ID_HEADER_KEY).toString());
    finalizePORequestBody.setFinalizedTime(new Date());
    finalizePORequestBody.setTotalBolFbq(poSummary.getTotalFBQty());
    finalizePORequestBody.setRcvdQty(poSummary.getReceiveQty());
    finalizePORequestBody.setRcvdQtyUom(VNPK);

    if (poSummary.getOverageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setCode(OSDRCode.O13.name());
      finalizePOOSDRInfo.setQuantity(poSummary.getOverageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePORequestBody.setOverage(finalizePOOSDRInfo);
    }

    if (poSummary.getShortageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setCode(OSDRCode.S10.name());
      finalizePOOSDRInfo.setQuantity(poSummary.getShortageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePORequestBody.setShortage(finalizePOOSDRInfo);
    }

    if (poSummary.getRejectedQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setQuantity(poSummary.getRejectedQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOOSDRInfo.setCode(poSummary.getRejectedReasonCode());
      finalizePOOSDRInfo.setComment(poSummary.getRejectedComment());
      finalizePORequestBody.setReject(finalizePOOSDRInfo);
    }

    if (poSummary.getDamageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setQuantity(poSummary.getDamageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOOSDRInfo.setCode(poSummary.getDamageReasonCode());
      finalizePOOSDRInfo.setClaimType(poSummary.getDamageClaimType());
      finalizePORequestBody.setDamage(finalizePOOSDRInfo);
    }

    finalizePORequestBody.setLines(gdmFinalizePOLineList);
    return finalizePORequestBody;
  }

  private DCFinPOCloseDocumentLineRequestBody prepareDcfinFinalizePOLine(
      Integer poLineNbr, int rcvdQty) {
    DCFinPOCloseDocumentLineRequestBody dcFinPOCloseDocumentLineRequestBody =
        new DCFinPOCloseDocumentLineRequestBody();
    dcFinPOCloseDocumentLineRequestBody.setDocLineClosedTs(
        ReceivingUtils.dateConversionToUTC(new Date()));
    dcFinPOCloseDocumentLineRequestBody.setDocumentLineNo(poLineNbr);
    dcFinPOCloseDocumentLineRequestBody.setPrimaryQty(rcvdQty);
    dcFinPOCloseDocumentLineRequestBody.setLineQtyUOM(VNPK);
    dcFinPOCloseDocumentLineRequestBody.setDocumentLineClosed(true);

    return dcFinPOCloseDocumentLineRequestBody;
  }

  private FinalizePOLine prepareGdmFinalizePOLine(
      Integer poLineNbr, ReceivingCountSummary poLineLevelSummary) {
    FinalizePOLine finalizePOLine = new FinalizePOLine();
    finalizePOLine.setLineNumber(poLineNbr);
    finalizePOLine.setRcvdQty(poLineLevelSummary.getReceiveQty());
    finalizePOLine.setRcvdQtyUom(VNPK);

    if (poLineLevelSummary.getOverageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setCode(OSDRCode.O13.name());
      finalizePOOSDRInfo.setQuantity(poLineLevelSummary.getOverageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOLine.setOverage(finalizePOOSDRInfo);
    }

    if (poLineLevelSummary.getShortageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setCode(OSDRCode.S10.name());
      finalizePOOSDRInfo.setQuantity(poLineLevelSummary.getShortageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOLine.setShortage(finalizePOOSDRInfo);
    }

    if (poLineLevelSummary.getRejectedQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setQuantity(poLineLevelSummary.getRejectedQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOOSDRInfo.setCode(poLineLevelSummary.getRejectedReasonCode());
      finalizePOOSDRInfo.setComment(poLineLevelSummary.getRejectedComment());
      finalizePOLine.setReject(finalizePOOSDRInfo);
    }

    if (poLineLevelSummary.getDamageQty() > 0) {
      FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();
      finalizePOOSDRInfo.setQuantity(poLineLevelSummary.getDamageQty());
      finalizePOOSDRInfo.setUom(VNPK);
      finalizePOOSDRInfo.setCode(poLineLevelSummary.getDamageReasonCode());
      finalizePOOSDRInfo.setClaimType(poLineLevelSummary.getDamageClaimType());
      finalizePOLine.setDamage(finalizePOOSDRInfo);
    }

    return finalizePOLine;
  }

  private void createMasterReceiptIfNeeded(
      Receipt masterReceipt,
      Long deliveryNumber,
      String poNbr,
      PurchaseOrderLineWithOSDRResponse poLine,
      Map<String, Object> httpHeaders) {
    if (masterReceipt == null) {
      Receipt receipt = new Receipt();
      receipt.setDeliveryNumber(deliveryNumber);
      receipt.setPurchaseReferenceNumber(poNbr);
      receipt.setPurchaseReferenceLineNumber(poLine.getPoLineNumber());
      receipt.setQuantity(0);
      receipt.setEachQty(0);
      receipt.setQuantityUom(VNPK);
      receipt.setVnpkQty(poLine.getVnpk().getQuantity());
      receipt.setWhpkQty(poLine.getWhpk().getQuantity());
      receipt.setCreateTs(Date.from(Instant.now()));
      receipt.setOsdrMaster(1);
      if (httpHeaders.containsKey(ORG_UNIT_ID_HEADER)
          && isNotBlank(httpHeaders.get(ORG_UNIT_ID_HEADER).toString()))
        receipt.setOrgUnitId(Integer.valueOf(httpHeaders.get(ORG_UNIT_ID_HEADER).toString()));
      LOGGER.info(
          "Creating OSDR_MASTER receipt for DELIVERY :{} PO :{} LINE :{}",
          deliveryNumber,
          poNbr,
          poLine.getPoLineNumber());
      receiptService.saveAndFlushReceipt(receipt);
    }
  }

  private void finalizeAllMasterReceipts(
      Long deliveryNbr, String poNbr, String userId, Date timestamp) {
    // Get receipts with OSDR_MASTER = 1
    List<Receipt> osdrMasterList =
        receiptService.findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumber(
            deliveryNbr, poNbr);
    confirmPoResponseBuilder.updateReceiptsWithFinalizedDetails(osdrMasterList, userId, timestamp);
  }
}
