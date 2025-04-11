package com.walmart.move.nim.receiving.core.builder;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.populateValidVnpkAndWnpk;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.ZERO_QTY_OSDR_MASTER_ENABLED;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderError;
import com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderErrorCode;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOLine;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOOSDRInfo;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOReasonCode;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.service.GDMOSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.libs.google.common.base.Enums;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for building GDM FinalizePORequestBody from given Receipts
 *
 * @author v0k00fe
 */
@Component
public class FinalizePORequestBodyBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(FinalizePORequestBodyBuilder.class);

  @Autowired private ReceiptService receiptService;

  @Autowired private OSDRCalculator osdrCalculator;

  @Autowired private GDMOSDRCalculator gdmosdrCalculator;

  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  private List<Receipt> createReceiptIfNotExists(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap) {

    List<Receipt> defaultReciepts = new ArrayList<>();
    List<Receipt> result = new ArrayList<>();

    for (Entry<POPOLineKey, ReceivingCountSummary> entry : receivingCountSummaryMap.entrySet()) {
      if (purchaseReferenceNumber.equals(entry.getKey().getPurchaseReferenceNumber())) {

        final Integer lineNumber = entry.getKey().getPurchaseReferenceLineNumber();
        Receipt masterReceipt =
            receiptService
                .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                    deliveryNumber, purchaseReferenceNumber, lineNumber);

        if (Objects.isNull(masterReceipt)) {
          // if no receipts then create master record with 0 qty only for OSDR math & values
          if (tenantSpecificConfigReader.getConfiguredFeatureFlag(
              getFacilityNum().toString(), ZERO_QTY_OSDR_MASTER_ENABLED, false)) {
            LOGGER.info(
                "creating zero quantity OSDR Master Receipt as no master Receipt for delivery={}, po={}, poLine={}",
                deliveryNumber,
                purchaseReferenceNumber,
                lineNumber);
            defaultReciepts.add(prepareMasterReceipt(deliveryNumber, entry, true));
          } else {
            LOGGER.info(
                "creating defaultReceipts as no master Receipt for delivery={}, po={}, poLine={}",
                deliveryNumber,
                purchaseReferenceNumber,
                lineNumber);
            defaultReciepts.add(prepareMasterReceipt(deliveryNumber, entry, false));
          }
        } else {
          result.add(masterReceipt);
        }
      }
    }
    receiptService.saveAll(defaultReciepts);
    result.addAll(defaultReciepts);
    return result;
  }

  /**
   * Prepare the master receipt
   *
   * @param deliveryNumber
   * @param isQty0MasterRecord true will set receipt quantity as 0 NEW master record is not Really a
   *     receive receipt but more of a record for OSDR values and math
   * @return Master Receipt for the PO, PoLine having OSDR values
   */
  private Receipt prepareMasterReceipt(
      Long deliveryNumber,
      Entry<POPOLineKey, ReceivingCountSummary> entry,
      boolean isQty0MasterRecord) {

    POPOLineKey popoLineKey = entry.getKey();
    ReceivingCountSummary receivingCountSummary = entry.getValue();

    Receipt receipt = new Receipt();
    receipt.setCreateTs(Date.from(Instant.now()));
    final int quantity = isQty0MasterRecord ? 0 : receivingCountSummary.getReceiveQty();
    final String purchaseReferenceNumber = popoLineKey.getPurchaseReferenceNumber();
    final Integer purchaseReferenceLineNumber = popoLineKey.getPurchaseReferenceLineNumber();
    LOGGER.info(
        "OSDR Master Receipt with delivery={}, po={}, poLine={}, isQty0MasterRecord={}, quantity={}",
        deliveryNumber,
        purchaseReferenceNumber,
        purchaseReferenceLineNumber,
        isQty0MasterRecord,
        quantity);
    receipt.setQuantity(quantity);
    receipt.setQuantityUom(ReceivingConstants.Uom.VNPK);
    receipt.setDeliveryNumber(deliveryNumber);
    receipt.setPurchaseReferenceNumber(purchaseReferenceNumber);
    receipt.setPurchaseReferenceLineNumber(purchaseReferenceLineNumber);
    receipt.setOsdrMaster(1);

    receipt.setFbOverQty(receivingCountSummary.getOverageQty());
    receipt.setFbOverQtyUOM(receivingCountSummary.getOverageQtyUOM());
    if (receivingCountSummary.isOverage()) {
      receipt.setFbOverReasonCode(OSDRCode.O13);
    }

    receipt.setFbShortQty(receivingCountSummary.getShortageQty());
    receipt.setFbShortQtyUOM(receivingCountSummary.getShortageQtyUOM());
    if (receivingCountSummary.isShortage()) {
      receipt.setFbShortReasonCode(OSDRCode.S10);
    }

    receipt.setFbDamagedQty(receivingCountSummary.getDamageQty());
    receipt.setFbDamagedQtyUOM(receivingCountSummary.getDamageQtyUOM());
    if (receivingCountSummary.getDamageReasonCode() != null) {
      receipt.setFbDamagedReasonCode(
          Enums.getIfPresent(OSDRCode.class, receivingCountSummary.getDamageReasonCode()).orNull());
    }
    receipt.setFbDamagedClaimType(receivingCountSummary.getDamageClaimType());

    receipt.setFbRejectedQty(receivingCountSummary.getRejectedQty());
    receipt.setFbRejectedQtyUOM(receivingCountSummary.getRejectedQtyUOM());
    if (receivingCountSummary.getRejectedReasonCode() != null) {
      receipt.setFbRejectedReasonCode(
          Enums.getIfPresent(OSDRCode.class, receivingCountSummary.getRejectedReasonCode())
              .orNull());
    }

    receipt.setFbProblemQty(receivingCountSummary.getProblemQty());
    receipt.setFbProblemQtyUOM(receivingCountSummary.getProblemQtyUOM());
    receipt.setFbConcealedShortageQty(0);
    receipt =
        populateValidVnpkAndWnpk(
            receipt, receivingCountSummary, purchaseReferenceNumber, purchaseReferenceLineNumber);

    receipt.setEachQty(
        isQty0MasterRecord
            ? 0
            : ReceivingUtils.conversionToEaches(
                receipt.getQuantity(),
                receipt.getQuantityUom(),
                receipt.getVnpkQty(),
                receipt.getWhpkQty()));

    LOGGER.info("Prepared the master receipt from receivingCountSummaryMap is {}", receipt);
    return receipt;
  }

  public FinalizePORequestBody buildFrom(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Map<String, Object> forwardableHeaders,
      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap)
      throws ReceivingException {
    if (Objects.isNull(receivingCountSummaryMap)) {
      return buildFrom(deliveryNumber, purchaseReferenceNumber, forwardableHeaders);
    } else {
      return buildGdmFinalizePoRequestBody(
          deliveryNumber, purchaseReferenceNumber, receivingCountSummaryMap);
    }
  }

  /**
   * Prepare the finalize PO payload
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param forwardableHeaders
   * @return FinalizePORequestBody
   * @throws ReceivingException
   */
  public FinalizePORequestBody buildFrom(
      Long deliveryNumber, String purchaseReferenceNumber, Map<String, Object> forwardableHeaders)
      throws ReceivingException {
    Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap =
        osdrRecordCountAggregator.getReceivingCountSummary(deliveryNumber, forwardableHeaders);
    return buildGdmFinalizePoRequestBody(
        deliveryNumber, purchaseReferenceNumber, receivingCountSummaryMap);
  }

  private FinalizePORequestBody buildGdmFinalizePoRequestBody(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap)
      throws ReceivingException {
    List<Receipt> receipts =
        createReceiptIfNotExists(deliveryNumber, purchaseReferenceNumber, receivingCountSummaryMap);

    Map<POPOLineKey, Receipt> receiptMap = new HashMap<>();
    receipts.forEach(
        receipt -> {
          POPOLineKey popoLineKey =
              new POPOLineKey(
                  receipt.getPurchaseReferenceNumber(), receipt.getPurchaseReferenceLineNumber());

          receiptMap.put(popoLineKey, receipt);
        });

    FinalizePORequestBody finalizePORequestBody = new FinalizePORequestBody();
    finalizePORequestBody.setReasonCode(FinalizePOReasonCode.PURCHASE_ORDER_FINALIZE);
    finalizePORequestBody.setRcvdQtyUom(ReceivingConstants.Uom.VNPK);

    ReceivingCountSummary poReceivingCountSummary = new ReceivingCountSummary();

    List<FinalizePOLine> finalizePOLines = new ArrayList<>();
    for (Entry<POPOLineKey, ReceivingCountSummary> entry : receivingCountSummaryMap.entrySet()) {
      POPOLineKey popoLineKey = entry.getKey();
      if (purchaseReferenceNumber.equals(popoLineKey.getPurchaseReferenceNumber())) {

        Receipt receipt = receiptMap.get(popoLineKey);
        ReceivingCountSummary receivingCountSummary = entry.getValue();

        osdrCalculator.calculate(receivingCountSummary);

        if (!isDamageOrProblemsCntSame(receipt, receivingCountSummary)) {
          ConfirmPurchaseOrderError confirmPOError =
              ConfirmPurchaseOrderErrorCode.getErrorValue(
                  ConfirmPurchaseOrderErrorCode.DATA_OUT_OF_SYNC);
          throw new ReceivingException(
              confirmPOError.getErrorMessage(),
              HttpStatus.BAD_REQUEST,
              confirmPOError.getErrorCode());
        }

        // since GDM has different OSDR calculation logic, recalculating OSDR again
        // team has decided not to persist recalculated values to DB
        gdmosdrCalculator.calculate(receivingCountSummary);

        FinalizePOLine finalizePOLine = new FinalizePOLine();
        finalizePOLine.setLineNumber(popoLineKey.getPurchaseReferenceLineNumber());
        finalizePOLine.setRcvdQty(
            ReceivingUtils.conversionToVendorPack(
                receivingCountSummary.getReceiveQty(),
                receipt.getQuantityUom(),
                receipt.getVnpkQty(),
                receipt.getWhpkQty()));
        finalizePOLine.setRcvdQtyUom(ReceivingConstants.Uom.VNPK);

        String damageResonCode =
            (receipt.getFbDamagedReasonCode() != null)
                ? receipt.getFbDamagedReasonCode().name()
                : null;
        String rejectReasonCode =
            receipt.getFbRejectedReasonCode() != null
                ? receipt.getFbRejectedReasonCode().name()
                : null;

        FinalizePOOSDRInfo damageFinalizePOOSDRInfo =
            build(damageResonCode, receipt.getFbDamagedQty());

        damageFinalizePOOSDRInfo.setClaimType(receipt.getFbDamagedClaimType());
        finalizePOLine.setDamage(damageFinalizePOOSDRInfo);
        poReceivingCountSummary.addDamageQty(receipt.getFbDamagedQty());

        if (receivingCountSummary.isOverage()) {
          FinalizePOOSDRInfo overageFinalizePOOSDRInfo =
              build(OSDRCode.O13.name(), receivingCountSummary.getOverageQty());
          finalizePOLine.setOverage(overageFinalizePOOSDRInfo);
        }

        FinalizePOOSDRInfo rejectFinalizePOOSDRInfo =
            build(rejectReasonCode, receipt.getFbRejectedQty());
        rejectFinalizePOOSDRInfo.setComment(receipt.getFbRejectionComment());
        finalizePOLine.setReject(rejectFinalizePOOSDRInfo);

        poReceivingCountSummary.addRejectedQty(receipt.getFbRejectedQty());

        if (receivingCountSummary.isShortage()) {
          FinalizePOOSDRInfo shortageFinalizePOOSDRInfo =
              build(OSDRCode.S10.name(), receivingCountSummary.getShortageQty());
          finalizePOLine.setShortage(shortageFinalizePOOSDRInfo);
        }

        poReceivingCountSummary.addReceiveQty(finalizePOLine.getRcvdQty());
        poReceivingCountSummary.addTotalFBQty(receivingCountSummary.getTotalFBQty());

        finalizePORequestBody.setTotalBolFbq(receivingCountSummary.getTotalBolFbq());
        finalizePOLines.add(finalizePOLine);

        LOGGER.info(
            "Linelevel Overage={},Shortage={}",
            finalizePOLine.getOverage(),
            finalizePOLine.getShortage());
      }
    }

    // since GDM has different OSDR calculation logic, recalculating OSDR again
    // team has decided not to persist recalculated values to DB
    gdmosdrCalculator.calculate(poReceivingCountSummary);

    // Prepare header OSDR
    LOGGER.info(
        "PO header level Overage={}, Shortage={}",
        poReceivingCountSummary.getOverageQty(),
        poReceivingCountSummary.getShortageQty());
    if (poReceivingCountSummary.getOverageQty() > 0) {
      finalizePORequestBody.setOverage(build(null, poReceivingCountSummary.getOverageQty()));
    }
    if (poReceivingCountSummary.getShortageQty() > 0) {
      finalizePORequestBody.setShortage(build(null, poReceivingCountSummary.getShortageQty()));
    }
    finalizePORequestBody.setDamage(build(null, poReceivingCountSummary.getDamageQty()));
    finalizePORequestBody.setReject(build(null, poReceivingCountSummary.getRejectedQty()));
    finalizePORequestBody.setRcvdQty(poReceivingCountSummary.getReceiveQty());

    finalizePORequestBody.setLines(finalizePOLines);
    return finalizePORequestBody;
  }

  private FinalizePOOSDRInfo build(String code, Integer qty) {

    FinalizePOOSDRInfo finalizePOOSDRInfo = new FinalizePOOSDRInfo();

    if (qty > 0) {
      finalizePOOSDRInfo.setCode(code);
    }
    finalizePOOSDRInfo.setQuantity(qty);
    finalizePOOSDRInfo.setUom(ReceivingConstants.Uom.VNPK);

    return finalizePOOSDRInfo;
  }

  private boolean isDamageOrProblemsCntSame(
      Receipt receipt, ReceivingCountSummary receivingCountSummary) {

    return Objects.equals(receipt.getFbProblemQty(), receivingCountSummary.getProblemQty())
        && Objects.equals(receipt.getFbProblemQtyUOM(), receivingCountSummary.getProblemQtyUOM())
        && Objects.equals(receipt.getFbDamagedQty(), receivingCountSummary.getDamageQty())
        && Objects.equals(receipt.getFbDamagedQtyUOM(), receivingCountSummary.getDamageQtyUOM());
  }
}
