package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.client.damage.DamageCode.D10;
import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.isPoFinalized;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.USE_DEFAULTS_FOR_DAMAGES;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.VDM_CLAIM_TYPE;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.walmart.move.nim.receiving.core.client.damage.AsyncDamageRestApiClient;
import com.walmart.move.nim.receiving.core.client.damage.DamageDeliveryInfo;
import com.walmart.move.nim.receiving.core.client.fit.AsyncFitRestApiClient;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponse;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponsePO;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponsePOLine;
import com.walmart.move.nim.receiving.core.client.gdm.AsyncGdmRestApiClient;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderLineWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.Reject;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OSDRRecordCountAggregator {

  private static final Logger LOGGER = LoggerFactory.getLogger(OSDRRecordCountAggregator.class);

  @Autowired private AsyncGdmRestApiClient asyncGdmRestApiClient;

  @Autowired private AsyncDamageRestApiClient asyncDamageRestApiClient;

  @Autowired private AsyncFitRestApiClient asyncFitRestApiClient;

  @Autowired private OSDRCalculator osdrCalculator;

  @Autowired private ReceiptService receiptService;

  @Autowired private TenantSpecificConfigReader configUtils;

  /**
   * Get receiving summary
   *
   * @param deliveryNumber
   * @param headers
   * @return Map<POPOLineKey, ReceivingCountSummary>
   * @throws ReceivingException
   */
  public Map<POPOLineKey, ReceivingCountSummary> getReceivingCountSummary(
      Long deliveryNumber, Map<String, Object> headers) throws ReceivingException {

    // what is this 3 call for, which /api we call this?
    CompletableFuture<DeliveryWithOSDRResponse> deliveryFuture =
        asyncGdmRestApiClient.getDelivery(deliveryNumber, headers);

    CompletableFuture<Optional<ProblemCountByDeliveryResponse>> problemCountByDeliveryFuture =
        asyncFitRestApiClient.findProblemCountByDelivery(deliveryNumber, headers);

    CompletableFuture<Optional<List<DamageDeliveryInfo>>> damagesByDeliveryFuture =
        asyncDamageRestApiClient.findDamagesByDelivery(deliveryNumber, headers);

    try {
      CompletableFuture.allOf(deliveryFuture, damagesByDeliveryFuture, problemCountByDeliveryFuture)
          .join();

      DeliveryWithOSDRResponse deliveryWithOSDRResponse = deliveryFuture.get();
      Optional<ProblemCountByDeliveryResponse> problemCountByDeliveryResponseOptional =
          problemCountByDeliveryFuture.get();
      Optional<List<DamageDeliveryInfo>> damageDeliveriesOptional = damagesByDeliveryFuture.get();

      Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap =
          enrichWithGDMData(deliveryWithOSDRResponse);
      if (problemCountByDeliveryResponseOptional.isPresent()) {
        enrichProblemCnt(deliveryFBQMap, problemCountByDeliveryResponseOptional.get());
      }
      if (damageDeliveriesOptional.isPresent()) {
        enrichDamageCnt(deliveryFBQMap, damageDeliveriesOptional.get());
      }

      for (PurchaseOrderWithOSDRResponse purchaseOrder :
          deliveryWithOSDRResponse.getPurchaseOrders()) {
        for (PurchaseOrderLineWithOSDRResponse poLine : purchaseOrder.getLines()) {
          POPOLineKey poPoLineKey =
              new POPOLineKey(purchaseOrder.getPoNumber(), poLine.getPoLineNumber());

          Optional<Receipt> osdrMasterReceiptOptional =
              findOsdrMasterReceipt(
                  deliveryWithOSDRResponse.getDeliveryNumber(),
                  purchaseOrder.getPoNumber(),
                  poLine.getPoLineNumber());
          ReceivingCountSummary receivingCountSummary =
              deliveryFBQMap.getOrDefault(poPoLineKey, new ReceivingCountSummary());

          // use vnpk,whpk from gdm
          receivingCountSummary.setVnpkQty(poLine.getVnpk().getQuantity());
          receivingCountSummary.setWhpkQty(poLine.getWhpk().getQuantity());

          receivingCountSummary.setTotalBolFbq(purchaseOrder.getTotalBolFbq());
          if (osdrMasterReceiptOptional.isPresent()) {
            Receipt osdrMasterReceipt = osdrMasterReceiptOptional.get();

            enrichWithReceiptDetails(receivingCountSummary, osdrMasterReceipt);
          }
          osdrCalculator.calculate(receivingCountSummary);
          if (receivingCountSummary.isOverage()) {
            receivingCountSummary.setOverageReasonCode(OSDRCode.O13.name());
          } else {
            receivingCountSummary.setOverageReasonCode(null);
          }

          if (receivingCountSummary.isShortage()) {
            receivingCountSummary.setShortageReasonCode(OSDRCode.S10.name());
          } else {
            receivingCountSummary.setShortageReasonCode(null);
          }
          LOGGER.info(
              "Adding to deliveryFBQMap with poPoLineKey={}, TotalFBQty={}, TotalBolFbq={}, ReceiveQty={}, totalReceiveQty={}",
              poPoLineKey,
              receivingCountSummary.getTotalFBQty(),
              receivingCountSummary.getTotalBolFbq(),
              receivingCountSummary.getReceiveQty(),
              receivingCountSummary.getTotalReceiveQty());

          deliveryFBQMap.put(poPoLineKey, receivingCountSummary);
        }

        if (CollectionUtils.isNotEmpty(purchaseOrder.getLines())) {
          purchaseOrder.setFinalizedUserId(purchaseOrder.getLines().get(0).getFinalizedUserId());
          purchaseOrder.setFinalizedTimeStamp(
              purchaseOrder.getLines().get(0).getFinalizedTimeStamp());
        }
      }
      return deliveryFBQMap;

    } catch (ReceivingException e) {
      LOGGER.error(
          "Get ReceivingCountSummary failed for delivery:{}, Error:{}",
          deliveryNumber,
          e.getMessage());
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode(),
          e.getErrorResponse().getErrorHeader());
    } catch (Exception e) {
      String correlationId = headers.get(ReceivingConstants.CORRELATION_ID_HEADER_KEY).toString();
      LOGGER.error(
          "correlationId : {}, delivery number : {}, Message : {}",
          correlationId,
          deliveryNumber,
          e.getMessage(),
          e);
      throw new ReceivingException(
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE);
    }
  }

  public Optional<Receipt> findOsdrMasterReceipt(
      long deliveryNumber, String poNumber, int poLineNumber) {

    Optional<Receipt> resultOptional = Optional.empty();

    List<Receipt> receipts =
        receiptService.findByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
            deliveryNumber, poNumber, poLineNumber);

    if (!CollectionUtils.isEmpty(receipts)) {
      Optional<Receipt> receiptOptional = receipts.stream().filter(this::isOSDRMaster).findFirst();
      if (receiptOptional.isPresent()) {
        return Optional.of(receiptOptional.get());
      } else {
        Receipt receipt = receipts.get(0);
        receipt.setOsdrMaster(1);
        return Optional.of(receipt);
      }
    }
    return resultOptional;
  }

  /**
   * Gets OsdrMaster Receipt from in memory DeliveryReceipts for given po, poLine if exist else will
   * mark the 1st po,poline receipt as master and return
   *
   * @param allDeliveryReceipts
   * @param poNumber
   * @param poLineNumber
   * @return OsdrMaster Receipt
   */
  public Optional<Receipt> findOsdrMasterReceiptFromInMemory(
      List<Receipt> allDeliveryReceipts, String poNumber, int poLineNumber) {

    Optional<Receipt> resultOptional = Optional.empty();

    if (CollectionUtils.isEmpty(allDeliveryReceipts)) {
      return resultOptional;
    }
    final List<Receipt> allReceiptsForPoPoLine =
        allDeliveryReceipts
            .parallelStream()
            .filter(
                r ->
                    poNumber.equals(r.getPurchaseReferenceNumber())
                        && poLineNumber == r.getPurchaseReferenceLineNumber())
            .collect(Collectors.toList());

    if (!CollectionUtils.isEmpty(allReceiptsForPoPoLine)) {
      Optional<Receipt> masterReceipt =
          allReceiptsForPoPoLine.parallelStream().filter(this::isOSDRMaster).findFirst();
      if (masterReceipt.isPresent()) {
        return Optional.of(masterReceipt.get());
      } else {
        Receipt receipt = allReceiptsForPoPoLine.get(0);
        receipt.setOsdrMaster(1);
        return Optional.of(receipt);
      }
    }
    return resultOptional;
  }

  private boolean isOSDRMaster(Receipt receipt) {
    return receipt.getOsdrMaster() != null && 1 == receipt.getOsdrMaster();
  }

  public void enrichWithReceiptDetails(
      ReceivingCountSummary receivingCountSummary, Receipt osdrMasterReceipt) {

    if (isPoFinalized(osdrMasterReceipt)) {
      updateDamages(receivingCountSummary, osdrMasterReceipt);
      updateProblems(receivingCountSummary, osdrMasterReceipt);
    }
    Long receiveQty =
        receiptService.receivedQtyByDeliveryPoAndPoLine(
            osdrMasterReceipt.getDeliveryNumber(),
            osdrMasterReceipt.getPurchaseReferenceNumber(),
            osdrMasterReceipt.getPurchaseReferenceLineNumber());
    receivingCountSummary.setReceiveQty(receiveQty.intValue());
    receivingCountSummary.setReceiveQtyUOM(VNPK);

    updateRejects(receivingCountSummary, osdrMasterReceipt);
  }

  public void enrichWithReceiptDetails(
      ReceivingCountSummary receivingCountSummary,
      Receipt osdrMasterReceipt,
      Long aggregateReceiveQtyForPoPoline) {

    if (isPoFinalized(osdrMasterReceipt)) {
      updateDamages(receivingCountSummary, osdrMasterReceipt);
      updateProblems(receivingCountSummary, osdrMasterReceipt);
    }
    receivingCountSummary.setReceiveQty(aggregateReceiveQtyForPoPoline.intValue());
    receivingCountSummary.setReceiveQtyUOM(VNPK);
    updateRejects(receivingCountSummary, osdrMasterReceipt);
  }

  private void updateRejects(
      ReceivingCountSummary receivingCountSummary, Receipt osdrMasterReceipt) {
    if (osdrMasterReceipt.getFbRejectedQty() != null) {
      receivingCountSummary.setRejectedQty(osdrMasterReceipt.getFbRejectedQty());
      receivingCountSummary.setRejectedQtyUOM(osdrMasterReceipt.getFbRejectedQtyUOM());
      if (osdrMasterReceipt.getFbRejectedReasonCode() != null) {
        receivingCountSummary.setRejectedReasonCode(
            osdrMasterReceipt.getFbRejectedReasonCode().name());
      }
      receivingCountSummary.setRejectedComment(osdrMasterReceipt.getFbRejectionComment());
    }
  }

  private void updateProblems(
      ReceivingCountSummary receivingCountSummary, Receipt osdrMasterReceipt) {
    if (osdrMasterReceipt.getFbProblemQty() != null) {
      receivingCountSummary.setProblemQty(osdrMasterReceipt.getFbProblemQty());
      receivingCountSummary.setProblemQtyUOM(osdrMasterReceipt.getFbProblemQtyUOM());
    }
  }

  private void updateDamages(ReceivingCountSummary receivingCountSummary, Receipt receipt) {
    if (receipt.getFbDamagedQty() != null) {
      receivingCountSummary.setDamageQty(receipt.getFbDamagedQty());
      receivingCountSummary.setDamageQtyUOM(receipt.getFbDamagedQtyUOM());
      receivingCountSummary.setDamageClaimType(receipt.getFbDamagedClaimType());
      if (receipt.getFbDamagedReasonCode() != null) {
        receivingCountSummary.setDamageReasonCode(receipt.getFbDamagedReasonCode().name());
      }
    }
  }

  /**
   * Populate the summary with GDM data
   *
   * @param deliveryWithOSDRResponse
   * @return Map<POPOLineKey, ReceivingCountSummary>
   */
  public Map<POPOLineKey, ReceivingCountSummary> enrichWithGDMData(
      DeliveryWithOSDRResponse deliveryWithOSDRResponse) {

    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap = new HashMap<>();
    for (PurchaseOrderWithOSDRResponse poWithOSDRResponse :
        deliveryWithOSDRResponse.getPurchaseOrders()) {
      for (PurchaseOrderLineWithOSDRResponse poLineWithOSDRResponse :
          poWithOSDRResponse.getLines()) {
        POPOLineKey pOPOLineKey =
            new POPOLineKey(
                poWithOSDRResponse.getPoNumber(), poLineWithOSDRResponse.getPoLineNumber());

        ReceivingCountSummary receivingCountSummary = new ReceivingCountSummary();
        receivingCountSummary.setTotalFBQty(poLineWithOSDRResponse.getFreightBillQty());
        receivingCountSummary.setTotalFBQtyUOM(poLineWithOSDRResponse.getOrdered().getUom());

        // use vnpk,whpk from gdm
        receivingCountSummary.setVnpkQty(poLineWithOSDRResponse.getVnpk().getQuantity());
        receivingCountSummary.setWhpkQty(poLineWithOSDRResponse.getWhpk().getQuantity());

        Reject reject = poLineWithOSDRResponse.getReject();
        if (Objects.nonNull(reject)) {
          if (Objects.nonNull(reject.getQuantity())) {
            receivingCountSummary.setRejectedQty(reject.getQuantity());
          }
          receivingCountSummary.setRejectedQtyUOM(reject.getUom());
          receivingCountSummary.setRejectedReasonCode(reject.getCode());
        }

        deliveryFBQMap.put(pOPOLineKey, receivingCountSummary);
      }
    }
    return deliveryFBQMap;
  }

  public void enrichDamageCnt(
      Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap,
      List<DamageDeliveryInfo> damageDeliveries)
      throws ReceivingException {

    for (DamageDeliveryInfo damageDelivery : damageDeliveries) {
      final String poNumber = damageDelivery.getPoNumber();
      final Integer poLineNumber = damageDelivery.getPoLineNumber();
      POPOLineKey poPoLineKey = new POPOLineKey(poNumber, poLineNumber);

      if (deliveryFBQMap.containsKey(poPoLineKey)) {
        ReceivingCountSummary receivingCountSummary = deliveryFBQMap.get(poPoLineKey);
        if (!configUtils.getConfiguredFeatureFlag(
            getFacilityNum().toString(), USE_DEFAULTS_FOR_DAMAGES, false)) {
          // Validate the mandatory data from Damage
          validateDamage(damageDelivery);
          receivingCountSummary.setDamageReasonCode(damageDelivery.getDamageCode().name());
          receivingCountSummary.setDamageClaimType(damageDelivery.getClaimType());
        } else {
          // default values for null values in Damage
          receivingCountSummary.setDamageReasonCode(
              isNull(damageDelivery.getDamageCode())
                  ? D10.name()
                  : damageDelivery.getDamageCode().name());
          receivingCountSummary.setDamageClaimType(
              isBlank(damageDelivery.getClaimType())
                  ? VDM_CLAIM_TYPE
                  : damageDelivery.getClaimType());
        }

        receivingCountSummary.addDamageQty(damageDelivery.getQuantity());
        receivingCountSummary.setDamageQtyUOM(damageDelivery.getUom());
        deliveryFBQMap.put(poPoLineKey, receivingCountSummary);
      } else {
        LOGGER.warn("Ignoring Damages as no match GDM.{}", damageDelivery);
      }
    }
  }

  private static void validateDamage(DamageDeliveryInfo damageDelivery) throws ReceivingException {
    boolean isDamageCodeNull = isNull(damageDelivery.getDamageCode());
    boolean isClaimTypeBlank = isBlank(damageDelivery.getClaimType());
    if (isDamageCodeNull && isClaimTypeBlank) {
      LOGGER.error(
          "Response damageCode & claimType are null for DamageDelivery={}", damageDelivery);
      throw new ReceivingException(
          ReceivingException.GET_DELIVERY_MISSING_DAMAGE_CODE_CLAIM_TYPE,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_HEADER);
    }
    if (isDamageCodeNull) {
      LOGGER.error("Response damageCode is null for DamageDelivery={}", damageDelivery);
      throw new ReceivingException(
          ReceivingException.GET_DELIVERY_MISSING_DAMAGE_CODE,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_HEADER);
    }
    if (isClaimTypeBlank) {
      LOGGER.error("Response isClaimTypeBlank is null for DamageDelivery={}", damageDelivery);
      throw new ReceivingException(
          ReceivingException.GET_DELIVERY_MISSING_CLAIM_TYPE,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_HEADER);
    }
  }

  public void enrichProblemCnt(
      Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap,
      ProblemCountByDeliveryResponse problemCountByDeliveryResponse) {
    List<ProblemCountByDeliveryResponsePO> problemCntByDeliveryResponsePOs =
        problemCountByDeliveryResponse.getPurchaseOrders();
    for (ProblemCountByDeliveryResponsePO problemPO : problemCntByDeliveryResponsePOs) {
      List<ProblemCountByDeliveryResponsePOLine> problemCntByDeliveryResponsePoLines =
          problemPO.getPoLines();
      for (ProblemCountByDeliveryResponsePOLine poLine : problemCntByDeliveryResponsePoLines) {
        final String poNumber = problemPO.getPoNumber();
        POPOLineKey poPoLineKey = new POPOLineKey(poNumber, poLine.getPoLineNumber());

        if (deliveryFBQMap.containsKey(poPoLineKey)) {
          ReceivingCountSummary receivingCountSummary = deliveryFBQMap.get(poPoLineKey);
          receivingCountSummary.setProblemQty(poLine.getIssueQty());
          receivingCountSummary.setProblemQtyUOM(poLine.getIssueQtyUom());

          deliveryFBQMap.put(poPoLineKey, receivingCountSummary);
        } else {
          LOGGER.warn("Ignoring Problems as no match GDM PO={},{}", poNumber, poLine);
        }
      }
    }
  }

  public Map<POPOLineKey, Long> getAggregateReceiveQtyForPoPolineList(
      List<Receipt> allDeliveryReceipts) {
    Map<POPOLineKey, Long> poPoLineQuantityMap = new HashMap<>();
    allDeliveryReceipts.forEach(
        r -> {
          final String po = r.getPurchaseReferenceNumber();
          final Integer poLine = r.getPurchaseReferenceLineNumber();
          final POPOLineKey poPoLineKey = new POPOLineKey(po, poLine);
          final long aggregateQuantity =
              allDeliveryReceipts
                  .parallelStream()
                  .filter(
                      receipt ->
                          po.equals(receipt.getPurchaseReferenceNumber())
                              && poLine == receipt.getPurchaseReferenceLineNumber())
                  .mapToLong(Receipt::getQuantity)
                  .sum();
          // TODO optimize loop and remove this check so only 1 time looped through streams
          if (poPoLineQuantityMap.get(poPoLineKey) == null) {
            poPoLineQuantityMap.put(poPoLineKey, aggregateQuantity);
          }
        });
    return poPoLineQuantityMap;
  }
}
