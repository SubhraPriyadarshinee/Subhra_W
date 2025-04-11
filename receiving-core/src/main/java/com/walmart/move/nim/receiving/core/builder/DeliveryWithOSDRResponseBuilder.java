package com.walmart.move.nim.receiving.core.builder;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getCorrelationId;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.GET_DELIVERY_DETAILS_PERF_V1;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.util.Objects.isNull;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.walmart.move.nim.receiving.core.client.damage.AsyncDamageRestApiClient;
import com.walmart.move.nim.receiving.core.client.damage.DamageDeliveryInfo;
import com.walmart.move.nim.receiving.core.client.fit.AsyncFitRestApiClient;
import com.walmart.move.nim.receiving.core.client.fit.ProblemCountByDeliveryResponse;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.DeliveryWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.OSDR;
import com.walmart.move.nim.receiving.core.model.POLineOSDR;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderLineWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.PurchaseOrderWithOSDRResponse;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.google.common.base.Enums;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for building DeliveryWithOSDRResponse
 *
 * @author v0k00fe
 */
@Component
public class DeliveryWithOSDRResponseBuilder {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DeliveryWithOSDRResponseBuilder.class);

  @Autowired private GDMRestApiClient gdmRestApiClient;

  @Autowired private AsyncDamageRestApiClient asyncDamageRestApiClient;

  @Autowired private AsyncFitRestApiClient asyncFitRestApiClient;

  @Autowired private ReceiptService receiptService;

  @Autowired private OSDRCalculator osdrCalculator;

  @Autowired private POHashKeyBuilder poHashKeyBuilder;

  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;

  @Autowired private TemporaryTiHiEnricher temporaryTiHiEnricher;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  protected WitronDeliveryMetaDataService deliveryMetaDataService;

  public DeliveryWithOSDRResponse build(
      Long deliveryNumber,
      Map<String, Object> forwardableHeaders,
      boolean includeOSDR,
      String poNumber)
      throws ReceivingException {
    DeliveryWithOSDRResponse deliveryWithOSDRResponse_gdm = null;
    try {
      deliveryWithOSDRResponse_gdm =
          gdmRestApiClient.getDelivery(deliveryNumber, forwardableHeaders);

      // Populate DELIVERY_METADATA, update if already exists
      populateDeliveryMetaDataWithGdm(deliveryNumber, deliveryWithOSDRResponse_gdm);

      if (tenantSpecificConfigReader.isDeliveryItemOverrideEnabled(getFacilityNum())) {
        temporaryTiHiEnricher.enrich(deliveryWithOSDRResponse_gdm);
      }

      if (includeOSDR) {
        aggregateOSDRFromDamagesAndProblems(
            deliveryNumber, forwardableHeaders, poNumber, deliveryWithOSDRResponse_gdm);
      }

    } catch (GDMRestApiClientException gdmEx) {
      LOGGER.error(
          "delivery number : {}, Message : {}",
          deliveryNumber,
          gdmEx.getErrorResponse().toString(),
          gdmEx);
      throw new ReceivingException(
          String.format(ReceivingException.DELIVERY_NOT_FOUND_ERROR_MESSAGE, deliveryNumber),
          gdmEx.getHttpStatus(),
          ReceivingException.GDM_SEARCH_DOCUMENT_ERROR_CODE,
          ReceivingException.DELIVERY_NOT_FOUND_HEADER);
    } catch (ReceivingException e) {
      LOGGER.error(
          "Get delivery with OSDR failed for delivery:{}, Error:{}, stackTrace={}",
          deliveryNumber,
          e.getMessage(),
          ExceptionUtils.getStackTrace(e));
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode(),
          e.getErrorResponse().getErrorHeader());
    } catch (Exception e) {
      LOGGER.error("delivery number : {}, Message : {}", deliveryNumber, e.getMessage(), e);
      throw new ReceivingException(
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR,
          HttpStatus.INTERNAL_SERVER_ERROR,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_CODE,
          ReceivingException.GET_DELIVERY_WITH_OSDR_ERROR_HEADER);
    }
    return deliveryWithOSDRResponse_gdm;
  }

  private void aggregateOSDRFromDamagesAndProblems(
      Long deliveryNumber,
      Map<String, Object> forwardableHeaders,
      String poNumber,
      DeliveryWithOSDRResponse deliveryWithGdmOSDR)
      throws InterruptedException, ExecutionException, ReceivingException {

    CompletableFuture<Optional<ProblemCountByDeliveryResponse>> problems =
        asyncFitRestApiClient.findProblemCountByDelivery(deliveryNumber, forwardableHeaders);

    CompletableFuture<Optional<List<DamageDeliveryInfo>>> damages =
        asyncDamageRestApiClient.findDamagesByDelivery(deliveryNumber, forwardableHeaders);

    CompletableFuture.allOf(damages, problems).join();

    Optional<ProblemCountByDeliveryResponse> problemCountByDeliveryResponseOptional =
        problems.get();
    Optional<List<DamageDeliveryInfo>> damageDeliveriesOptional = damages.get();

    // aggregate all OSDRs gdm+problems+damages
    enrichOSDRValues(
        deliveryWithGdmOSDR, problemCountByDeliveryResponseOptional, damageDeliveriesOptional);

    filterDeliveryResponseForPo(poNumber, deliveryWithGdmOSDR);
  }

  private void populateDeliveryMetaDataWithGdm(
      Long deliveryNumber, DeliveryWithOSDRResponse gdmDeliveryWithOSDR) {
    Optional<DeliveryMetaData> deliveryMetaData =
        deliveryMetaDataService.findByDeliveryNumber(deliveryNumber.toString());
    if (deliveryMetaData.isPresent()) {
      LOGGER.info("updateDeliveryMetaData() with deliveryNumber :{}", deliveryNumber);
      deliveryMetaDataService.updateDeliveryMetaData(deliveryMetaData.get(), gdmDeliveryWithOSDR);
    } else {
      LOGGER.info("createDeliveryMetaData() with deliveryNumber :{}", deliveryNumber);
      deliveryMetaDataService.createDeliveryMetaData(gdmDeliveryWithOSDR);
    }
  }

  public void filterDeliveryResponseForPo(
      String poNumber, DeliveryWithOSDRResponse deliveryWithOSDRResponse) {
    if (isBlank(poNumber) || isNull(deliveryWithOSDRResponse)) {
      return;
    }
    LOGGER.info("Before filter for PO={}, OSDR={}", poNumber, deliveryWithOSDRResponse.getOsdr());
    final List<PurchaseOrderWithOSDRResponse> purchaseOrders =
        deliveryWithOSDRResponse.getPurchaseOrders();
    final PurchaseOrderWithOSDRResponse poFilteredResponse =
        purchaseOrders
            .stream()
            .filter(aPoResponse -> poNumber.equals(aPoResponse.getPoNumber()))
            .findFirst()
            .orElse(null);
    LOGGER.info("After filter for PO={}, poFilteredResponse={}", poNumber, poFilteredResponse);
    purchaseOrders.clear();
    purchaseOrders.add(poFilteredResponse);
  }

  private void enrichOSDRValues(
      DeliveryWithOSDRResponse deliveryWithGdmOSDRResponse,
      Optional<ProblemCountByDeliveryResponse> problems,
      Optional<List<DamageDeliveryInfo>> damages)
      throws ReceivingException {

    // break and reuse
    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap =
        getReceivingCountSummaryMap(deliveryWithGdmOSDRResponse, problems, damages);

    if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
        getFacilityNum().toString(), GET_DELIVERY_DETAILS_PERF_V1, false)) {
      includeOsdrDeliveryWiseAtOnce(deliveryWithGdmOSDRResponse, deliveryFBQMap);
    } else {
      includeOsdrLineWiseSequentially(deliveryWithGdmOSDRResponse, deliveryFBQMap);
    }
  }

  private Map<POPOLineKey, ReceivingCountSummary> getReceivingCountSummaryMap(
      DeliveryWithOSDRResponse gdm,
      Optional<ProblemCountByDeliveryResponse> problems,
      Optional<List<DamageDeliveryInfo>> damages)
      throws ReceivingException {
    Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap =
        osdrRecordCountAggregator.enrichWithGDMData(gdm);
    if (problems.isPresent()) {
      osdrRecordCountAggregator.enrichProblemCnt(deliveryFBQMap, problems.get());
    }
    if (damages.isPresent()) {
      osdrRecordCountAggregator.enrichDamageCnt(deliveryFBQMap, damages.get());
    }
    return deliveryFBQMap;
  }

  private void includeOsdrLineWiseSequentially(
      DeliveryWithOSDRResponse deliveryWithGdmOSDRResponse,
      Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap) {
    OSDR osdr_deliveryLevel = new OSDR();
    final Long deliveryNumber = deliveryWithGdmOSDRResponse.getDeliveryNumber();
    for (PurchaseOrderWithOSDRResponse purchaseOrder :
        deliveryWithGdmOSDRResponse.getPurchaseOrders()) {
      ReceivingCountSummary poReceivingCountSummary = new ReceivingCountSummary();
      final String po = purchaseOrder.getPoNumber();

      for (PurchaseOrderLineWithOSDRResponse aLine : purchaseOrder.getLines()) {

        final Integer poLine = aLine.getPoLineNumber();
        POPOLineKey poPoLineKey = new POPOLineKey(po, poLine);
        ReceivingCountSummary poLineReceivingCountSummary =
            deliveryFBQMap.getOrDefault(poPoLineKey, new ReceivingCountSummary());

        // get for all delivery
        Optional<Receipt> osdrMasterReceiptOptional =
            osdrRecordCountAggregator.findOsdrMasterReceipt(deliveryNumber, po, poLine);

        if (osdrMasterReceiptOptional.isPresent()) {
          Receipt osdrMasterReceipt = osdrMasterReceiptOptional.get();

          osdrRecordCountAggregator.enrichWithReceiptDetails(
              poLineReceivingCountSummary, osdrMasterReceipt);

          osdrCalculator.calculate(poLineReceivingCountSummary);
          enrichPOLine(poLineReceivingCountSummary, aLine);

          enrichReceipt(osdrMasterReceipt, aLine);

          Receipt savedRecipts = receiptService.saveReceipt(osdrMasterReceipt);
          aLine.setVersion(savedRecipts.getVersion());
          aLine.setFinalizedTimeStamp(savedRecipts.getFinalizeTs());
          aLine.setFinalizedUserId(savedRecipts.getFinalizedUserId());

        } else {
          osdrCalculator.calculate(poLineReceivingCountSummary);
          enrichPOLine(poLineReceivingCountSummary, aLine);
          aLine.setVersion(0);
        }
        deliveryFBQMap.put(poPoLineKey, poLineReceivingCountSummary);
        updatePoCounts(poReceivingCountSummary, aLine);
      }
      // PO Level
      osdrCalculator.calculate(poReceivingCountSummary);

      // Overriding freightBillQty with totalBolFbq
      purchaseOrder.setFreightBillQty(purchaseOrder.getTotalBolFbq());

      // OSDR
      final OSDR osdr_poLevel = getPoLevelOSDR(poReceivingCountSummary);
      purchaseOrder.setOsdr(osdr_poLevel);
      osdr_deliveryLevel.add(osdr_poLevel); // aggregate OSDR(s)

      if (CollectionUtils.isNotEmpty(purchaseOrder.getLines())) {
        purchaseOrder.setFinalizedUserId(purchaseOrder.getLines().get(0).getFinalizedUserId());
        purchaseOrder.setFinalizedTimeStamp(
            purchaseOrder.getLines().get(0).getFinalizedTimeStamp());
      }
      purchaseOrder.setPoHashKey(poHashKeyBuilder.build(deliveryNumber, po));
    }
    deliveryWithGdmOSDRResponse.setOsdr(osdr_deliveryLevel);
  }

  private void includeOsdrDeliveryWiseAtOnce(
      DeliveryWithOSDRResponse deliveryWithGdmOSDRResponse,
      Map<POPOLineKey, ReceivingCountSummary> deliveryFBQMap) {
    OSDR osdr_deliveryLevel = new OSDR();
    final Long deliveryNumber = deliveryWithGdmOSDRResponse.getDeliveryNumber();
    // Get osdrMaster receipts
    List<Receipt> allDeliveryReceipts = receiptService.findByDeliveryNumber(deliveryNumber);
    Map<POPOLineKey, Long> aggregateReceiveQtyForPoPolineList =
        osdrRecordCountAggregator.getAggregateReceiveQtyForPoPolineList(allDeliveryReceipts);

    for (PurchaseOrderWithOSDRResponse purchaseOrder :
        deliveryWithGdmOSDRResponse.getPurchaseOrders()) {
      ReceivingCountSummary poReceivingCountSummary = new ReceivingCountSummary();
      final String po = purchaseOrder.getPoNumber();

      for (PurchaseOrderLineWithOSDRResponse lineResponse : purchaseOrder.getLines()) {

        final Integer poLine = lineResponse.getPoLineNumber();
        POPOLineKey poPoLineKey = new POPOLineKey(po, poLine);
        ReceivingCountSummary poLineReceivingCountSummary =
            deliveryFBQMap.getOrDefault(poPoLineKey, new ReceivingCountSummary());

        // get for all delivery
        Optional<Receipt> osdrMasterReceiptOptional =
            osdrRecordCountAggregator.findOsdrMasterReceiptFromInMemory(
                allDeliveryReceipts, po, poLine);

        if (osdrMasterReceiptOptional.isPresent()) {
          Receipt osdrMasterReceipt = osdrMasterReceiptOptional.get();
          osdrRecordCountAggregator.enrichWithReceiptDetails(
              poLineReceivingCountSummary,
              osdrMasterReceipt,
              aggregateReceiveQtyForPoPolineList.get(poPoLineKey));

          osdrCalculator.calculate(poLineReceivingCountSummary);
          enrichPOLine(poLineReceivingCountSummary, lineResponse);

          enrichReceipt(osdrMasterReceipt, lineResponse);

          // TODO optimize WRITE line by line save receipts
          //  In Async or all at once after the loop TBD as perf v2
          Receipt savedRecipts = receiptService.saveReceipt(osdrMasterReceipt);
          lineResponse.setVersion(savedRecipts.getVersion());
          lineResponse.setFinalizedTimeStamp(savedRecipts.getFinalizeTs());
          lineResponse.setFinalizedUserId(savedRecipts.getFinalizedUserId());

        } else {
          osdrCalculator.calculate(poLineReceivingCountSummary);
          enrichPOLine(poLineReceivingCountSummary, lineResponse);
          lineResponse.setVersion(0);
        }
        deliveryFBQMap.put(poPoLineKey, poLineReceivingCountSummary);
        updatePoCounts(poReceivingCountSummary, lineResponse);
      }
      // PO Level
      osdrCalculator.calculate(poReceivingCountSummary);

      // Overriding freightBillQty with totalBolFbq
      purchaseOrder.setFreightBillQty(purchaseOrder.getTotalBolFbq());

      // OSDR
      final OSDR osdr_poLevel = getPoLevelOSDR(poReceivingCountSummary);
      purchaseOrder.setOsdr(osdr_poLevel);
      osdr_deliveryLevel.add(osdr_poLevel); // aggregate OSDR(s)

      if (CollectionUtils.isNotEmpty(purchaseOrder.getLines())) {
        purchaseOrder.setFinalizedUserId(purchaseOrder.getLines().get(0).getFinalizedUserId());
        purchaseOrder.setFinalizedTimeStamp(
            purchaseOrder.getLines().get(0).getFinalizedTimeStamp());
      }
      purchaseOrder.setPoHashKey(poHashKeyBuilder.build(deliveryNumber, po));
    }
    deliveryWithGdmOSDRResponse.setOsdr(osdr_deliveryLevel);
  }

  private OSDR getPoLevelOSDR(ReceivingCountSummary poReceivingCountSummary) {
    OSDR poOSDR = new OSDR();
    poOSDR.setOverageQty(poReceivingCountSummary.getOverageQty());
    poOSDR.setShortageQty(poReceivingCountSummary.getShortageQty());
    poOSDR.setDamageQty(poReceivingCountSummary.getDamageQty());
    poOSDR.setRejectedQty(poReceivingCountSummary.getRejectedQty());
    poOSDR.setProblemQty(poReceivingCountSummary.getProblemQty());
    poOSDR.setPofbqReceivedQty(poReceivingCountSummary.getReceiveQty());
    return poOSDR;
  }

  private void enrichPOLine(
      ReceivingCountSummary receivingCountSummary, PurchaseOrderLineWithOSDRResponse poLine) {

    POLineOSDR poLineOSDR = new POLineOSDR();
    if (receivingCountSummary.isOverage()) {

      poLineOSDR.setOverageQty(receivingCountSummary.getOverageQty());
      poLineOSDR.setOverageUOM(receivingCountSummary.getOverageQtyUOM());
      poLineOSDR.setOverageReasonCode("O13");
    }

    if (receivingCountSummary.isShortage()) {

      poLineOSDR.setShortageQty(receivingCountSummary.getShortageQty());
      poLineOSDR.setShortageUOM(receivingCountSummary.getShortageQtyUOM());
      poLineOSDR.setShortageReasonCode("S10");
    }

    poLineOSDR.setDamageQty(receivingCountSummary.getDamageQty());
    poLineOSDR.setDamageUOM(receivingCountSummary.getDamageQtyUOM());
    poLineOSDR.setDamageReasonCode(receivingCountSummary.getDamageReasonCode());
    poLineOSDR.setDamageClaimType(receivingCountSummary.getDamageClaimType());

    final int rejectedQty = receivingCountSummary.getRejectedQty();
    String rejectedQtyUOM = receivingCountSummary.getRejectedQtyUOM();
    if (rejectedQty > 0 && isBlank(rejectedQtyUOM)) {
      rejectedQtyUOM = VNPK;
      LOGGER.warn(
          "CorrelationId={} setting rejectedQtyUOM as VNPK as its blank for poLine={} receivingCountSummary={}",
          getCorrelationId(),
          poLine.getPoLineNumber(),
          receivingCountSummary);
    }
    poLineOSDR.setRejectedQty(rejectedQty);
    poLineOSDR.setRejectedUOM(rejectedQtyUOM);
    if (null != receivingCountSummary.getRejectedReasonCode()) {
      poLineOSDR.setRejectedReasonCode(receivingCountSummary.getRejectedReasonCode());
    }

    poLineOSDR.setProblemQty(receivingCountSummary.getProblemQty());
    poLineOSDR.setProblemUOM(receivingCountSummary.getProblemQtyUOM());

    poLineOSDR.setPofbqReceivedQty(receivingCountSummary.getReceiveQty());

    poLine.setOsdr(poLineOSDR);
  }

  private void updatePoCounts(
      ReceivingCountSummary poReceivingCountSummary, PurchaseOrderLineWithOSDRResponse poLine) {

    POLineOSDR poLineOSDR = poLine.getOsdr();

    poReceivingCountSummary.addDamageQty(poLineOSDR.getDamageQty());
    poReceivingCountSummary.addRejectedQty(poLineOSDR.getRejectedQty());
    poReceivingCountSummary.addProblemQty(poLineOSDR.getProblemQty());
    poReceivingCountSummary.addReceiveQty(poLineOSDR.getPofbqReceivedQty());
    poReceivingCountSummary.addTotalFBQty(poLine.getFreightBillQty());
  }

  private void enrichReceipt(Receipt receipt, PurchaseOrderLineWithOSDRResponse poLine) {

    POLineOSDR poLineOSDR = poLine.getOsdr();

    receipt.setFbOverQty(poLineOSDR.getOverageQty());
    receipt.setFbOverQtyUOM(poLineOSDR.getOverageUOM());
    if (poLineOSDR.getOverageReasonCode() != null) {
      receipt.setFbOverReasonCode(
          Enums.getIfPresent(OSDRCode.class, poLineOSDR.getOverageReasonCode()).orNull());
    }

    receipt.setFbShortQty(poLineOSDR.getShortageQty());
    receipt.setFbShortQtyUOM(poLineOSDR.getShortageUOM());
    if (poLineOSDR.getShortageReasonCode() != null) {
      receipt.setFbShortReasonCode(
          Enums.getIfPresent(OSDRCode.class, poLineOSDR.getShortageReasonCode()).orNull());
    }

    receipt.setFbDamagedQty(poLineOSDR.getDamageQty());
    receipt.setFbDamagedQtyUOM(poLineOSDR.getDamageUOM());
    if (poLineOSDR.getDamageReasonCode() != null) {
      receipt.setFbDamagedReasonCode(
          Enums.getIfPresent(OSDRCode.class, poLineOSDR.getDamageReasonCode()).orNull());
    }
    receipt.setFbDamagedClaimType(poLineOSDR.getDamageClaimType());

    receipt.setFbRejectedQty(poLineOSDR.getRejectedQty());
    receipt.setFbRejectedQtyUOM(poLineOSDR.getRejectedUOM());
    if (poLineOSDR.getRejectedReasonCode() != null) {
      receipt.setFbRejectedReasonCode(
          Enums.getIfPresent(OSDRCode.class, poLineOSDR.getRejectedReasonCode()).orNull());
    }

    receipt.setFbProblemQty(poLineOSDR.getProblemQty());
    receipt.setFbProblemQtyUOM(poLineOSDR.getProblemUOM());
  }
}
