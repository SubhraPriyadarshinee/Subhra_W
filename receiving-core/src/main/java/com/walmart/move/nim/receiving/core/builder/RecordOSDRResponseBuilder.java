package com.walmart.move.nim.receiving.core.builder;

import static com.walmart.move.nim.receiving.core.common.ReceiptUtils.isPoFinalized;
import static com.walmart.move.nim.receiving.core.common.ReceivingException.*;
import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.REJECT_OSDR_VERSION_MISMATCH;
import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.REJECT_SAVE_BAD_DATA;
import static com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePOReasonCode.RECEIVING_CORRECTION;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.getCorrelationId;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.entity.Rejections;
import com.walmart.move.nim.receiving.core.model.POPOLineKey;
import com.walmart.move.nim.receiving.core.model.ReceiveData;
import com.walmart.move.nim.receiving.core.model.ReceiveEventRequestBody;
import com.walmart.move.nim.receiving.core.model.ReceivingCountSummary;
import com.walmart.move.nim.receiving.core.model.RecordOSDRRequest;
import com.walmart.move.nim.receiving.core.model.RecordOSDRResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.repositories.RejectionsRepository;
import com.walmart.move.nim.receiving.core.service.OSDRCalculator;
import com.walmart.move.nim.receiving.core.service.OSDRRecordCountAggregator;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.constants.OSDRCode;
import io.strati.libs.google.common.base.Enums;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * This class is responsible for building RecordOSDRResponse
 *
 * @author v0k00fe
 */
@Component
public class RecordOSDRResponseBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RecordOSDRResponseBuilder.class);

  @Autowired private OSDRRecordCountAggregator osdrRecordCountAggregator;
  @Autowired private OSDRCalculator osdrCalculator;
  @Autowired private ReceiptService receiptService;
  @Autowired private POHashKeyBuilder poHashKeyBuilder;
  @Autowired private TenantSpecificConfigReader configUtils;
  @Autowired private GDMRestApiClient gdmRestApiClient;
  @Autowired private FinalizePORequestBodyBuilder finalizePORequestBodyBuilder;
  @Autowired private RejectionsRepository rejectionsRepository;

  public RecordOSDRResponse build(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Integer purchaseReferenceLineNumber,
      RecordOSDRRequest request,
      HttpHeaders httpHeaders)
      throws ReceivingException {
    final boolean isKotlinEnabled = isKotlinEnabled(httpHeaders, configUtils);
    final boolean hasValidRejectionDetails = hasValidRejectionDetails(request, isKotlinEnabled);
    Map<String, Object> forwardableHeaders =
        ReceivingUtils.getForwardablHeaderWithTenantData(httpHeaders);

    Map<POPOLineKey, ReceivingCountSummary> receivingCountSummaryMap =
        osdrRecordCountAggregator.getReceivingCountSummary(deliveryNumber, forwardableHeaders);

    POPOLineKey popoLineKey = new POPOLineKey(purchaseReferenceNumber, purchaseReferenceLineNumber);
    ReceivingCountSummary receivingCountSummary = receivingCountSummaryMap.get(popoLineKey);

    if (hasValidRejectionDetails) {
      receivingCountSummary.setRejectedQty(request.getRejectedQty());
      receivingCountSummary.setRejectedQtyUOM(request.getRejectedUOM());
    }

    osdrCalculator.calculate(receivingCountSummary);

    Receipt receipt =
        receiptService
            .findMasterRecieptByDeliveryNumberAndPurchaseReferenceNumberAndPurchaseReferenceLineNumber(
                deliveryNumber, purchaseReferenceNumber, purchaseReferenceLineNumber);
    boolean isDefaultReceiptCreated = false;

    if (receipt == null) {
      LOGGER.info(
          "Creating default RECEIPT with OSDR_MASTER=1 for delivery:{} PO:{} Line:{}",
          deliveryNumber,
          purchaseReferenceNumber,
          purchaseReferenceLineNumber);
      receipt =
          receiptService.prepareMasterReceipt(
              deliveryNumber,
              purchaseReferenceNumber,
              purchaseReferenceLineNumber,
              receivingCountSummary);
      isDefaultReceiptCreated = true;
    }
    Integer version = request.getVersion();

    if (!isDefaultReceiptCreated && isVersionMisMatch(receipt, version)) {
      if (isKotlinEnabled) {
        throw new ReceivingBadDataException(
            REJECT_OSDR_VERSION_MISMATCH, PO_PO_LINE_VERSION_MISMATCH);
      }

      throw new ReceivingException(
          PO_PO_LINE_VERSION_MISMATCH, BAD_REQUEST, RECORD_OSDR_ERROR_CODE);
    }

    if (!isDefaultReceiptCreated && !isDamageOrProblemsCntSame(receipt, receivingCountSummary)) {
      if (isKotlinEnabled) {
        throw new ReceivingBadDataException(
            REJECT_SAVE_BAD_DATA, RECORD_OSDR_FIT_OR_DAMAGE_DATA_OBSOLETE);
      }

      throw new ReceivingException(
          RECORD_OSDR_FIT_OR_DAMAGE_DATA_OBSOLETE, BAD_REQUEST, RECORD_OSDR_ERROR_CODE);
    }

    LOGGER.info("popoLineKey:{} receivingCountSummary:{}", popoLineKey, receivingCountSummary);
    receipt.setFbOverQty(receivingCountSummary.getOverageQty());
    receipt.setFbOverQtyUOM(receivingCountSummary.getOverageQtyUOM());
    if (receivingCountSummary.isOverage()) {
      receipt.setFbOverReasonCode(OSDRCode.O13);
    } else {
      receipt.setFbOverReasonCode(null);
    }

    receipt.setFbShortQty(receivingCountSummary.getShortageQty());
    receipt.setFbShortQtyUOM(receivingCountSummary.getShortageQtyUOM());
    if (receivingCountSummary.isShortage()) {
      receipt.setFbShortReasonCode(OSDRCode.S10);
    } else {
      receipt.setFbShortReasonCode(null);
    }

    receipt.setFbRejectedQty(receivingCountSummary.getRejectedQty());
    receipt.setFbRejectedQtyUOM(receivingCountSummary.getRejectedQtyUOM());
    if (request.getRejectedReasonCode() != null) {
      OSDRCode rejection = OSDRCode.valueOf(request.getRejectedReasonCode());
      if (null != rejection) {
        receipt.setFbRejectedReasonCode(rejection);
      } else {
        throw new ReceivingException("Invalid Reject Reason Code", BAD_REQUEST);
      }
    }
    receipt.setFbRejectionComment(request.getRejectionComment());

    receipt.setVersion(request.getVersion());
    Receipt savedReceipt = receiptService.saveAndFlushReceipt(receipt);
    version = savedReceipt.getVersion();

    RecordOSDRResponse recordOSDRResponse = buildResponse(receivingCountSummary);
    recordOSDRResponse.setVersion(version);
    recordOSDRResponse.setPoHashKey(
        poHashKeyBuilder.build(deliveryNumber, purchaseReferenceNumber));

    // Persist and Send rejections to GDM
    persistRejectionsAndSend(request, receipt, forwardableHeaders);

    // Re-calculate OSDR after PO finalized
    if (isPoFinalized(savedReceipt)) {
      processOsdrCorrection(deliveryNumber, purchaseReferenceNumber, forwardableHeaders);
    }

    return recordOSDRResponse;
  }

  private boolean isVersionMisMatch(Receipt receipt, Integer version) {
    return !receipt.getVersion().equals(version);
  }

  public boolean hasValidRejectionDetails(
      RecordOSDRRequest recordOSDRReasonCodesRequestBody, boolean isKotlinEnabled)
      throws ReceivingException, ReceivingBadDataException {
    if (isBlank(recordOSDRReasonCodesRequestBody.getRejectedUOM())) {
      final String rejectedQtyUOM = isKotlinEnabled ? VNPK : null;
      LOGGER.error(
          "CorrelationId={} rejectedQtyUOM isBlank setting as={} for RecordOSDRRequest={}",
          getCorrelationId(),
          rejectedQtyUOM,
          recordOSDRReasonCodesRequestBody);
      recordOSDRReasonCodesRequestBody.setRejectedUOM(rejectedQtyUOM);
    }

    boolean anyNotNull =
        ObjectUtils.anyNotNull(
            recordOSDRReasonCodesRequestBody.getRejectedQty(),
            recordOSDRReasonCodesRequestBody.getRejectedUOM(),
            recordOSDRReasonCodesRequestBody.getRejectedReasonCode());

    boolean allNotNull =
        ObjectUtils.allNotNull(
            recordOSDRReasonCodesRequestBody.getRejectedQty(),
            recordOSDRReasonCodesRequestBody.getRejectedUOM(),
            recordOSDRReasonCodesRequestBody.getRejectedReasonCode());

    if (anyNotNull && !allNotNull) {
      if (isKotlinEnabled) {
        throw new ReceivingBadDataException(REJECT_SAVE_BAD_DATA, INSUFFICIENT_REJECT_DETAILS);
      }
      throw new ReceivingException(
          INSUFFICIENT_REJECT_DETAILS, BAD_REQUEST, RECORD_OSDR_ERROR_CODE);
    }

    if (!allNotNull) {
      return false;
    }

    OSDRCode rejectReasonCode =
        Enums.getIfPresent(OSDRCode.class, recordOSDRReasonCodesRequestBody.getRejectedReasonCode())
            .orNull();
    if (null == rejectReasonCode) {
      String message =
          String.format(
              ReceivingException.INVALID_OSDR_REASON_CODES,
              recordOSDRReasonCodesRequestBody.getRejectedReasonCode());

      if (isKotlinEnabled) {
        throw new ReceivingBadDataException(REJECT_SAVE_BAD_DATA, message);
      }

      throw new ReceivingException(message, BAD_REQUEST, RECORD_OSDR_ERROR_CODE);
    }
    return allNotNull;
  }

  private RecordOSDRResponse buildResponse(ReceivingCountSummary receivingCountSummary) {
    RecordOSDRResponse recordOSDRReasonCodesResponse = new RecordOSDRResponse();

    recordOSDRReasonCodesResponse.setOverageQty(receivingCountSummary.getOverageQty());
    recordOSDRReasonCodesResponse.setOverageUOM(receivingCountSummary.getOverageQtyUOM());
    recordOSDRReasonCodesResponse.setShortageQty(receivingCountSummary.getShortageQty());
    recordOSDRReasonCodesResponse.setShortageUOM(receivingCountSummary.getShortageQtyUOM());
    recordOSDRReasonCodesResponse.setDamageQty(receivingCountSummary.getDamageQty());
    recordOSDRReasonCodesResponse.setDamageUOM(receivingCountSummary.getDamageQtyUOM());
    recordOSDRReasonCodesResponse.setRejectedQty(receivingCountSummary.getRejectedQty());
    recordOSDRReasonCodesResponse.setRejectedUOM(receivingCountSummary.getRejectedQtyUOM());
    recordOSDRReasonCodesResponse.setProblemQty(receivingCountSummary.getProblemQty());
    recordOSDRReasonCodesResponse.setProblemUOM(receivingCountSummary.getProblemQtyUOM());

    return recordOSDRReasonCodesResponse;
  }

  private boolean isDamageOrProblemsCntSame(
      Receipt receipt, ReceivingCountSummary receivingCountSummary) {

    return Objects.equals(receipt.getFbProblemQty(), receivingCountSummary.getProblemQty())
        && Objects.equals(receipt.getFbProblemQtyUOM(), receivingCountSummary.getProblemQtyUOM())
        && Objects.equals(receipt.getFbDamagedQty(), receivingCountSummary.getDamageQty())
        && Objects.equals(receipt.getFbDamagedQtyUOM(), receivingCountSummary.getDamageQtyUOM());
  }

  private void processOsdrCorrection(Long deliveryNbr, String poNbr, Map<String, Object> headers)
      throws ReceivingException {
    try {
      // Prepare new totals
      FinalizePORequestBody newOsdrPayload = prepareNewOSDRPayload(deliveryNbr, poNbr, headers);

      // Send new totals to GDM
      gdmRestApiClient.persistFinalizePoOsdrToGdm(deliveryNbr, poNbr, newOsdrPayload, headers);
    } catch (GDMRestApiClientException e) {
      throw new ReceivingException(
          e.getErrorResponse().getErrorMessage(),
          e.getHttpStatus(),
          e.getErrorResponse().getErrorCode());
    }
  }

  private FinalizePORequestBody prepareNewOSDRPayload(
      Long deliveryNbr, String poNbr, Map<String, Object> headers) throws ReceivingException {
    LOGGER.info("Prepare new OSDR details for delivery:{} PO:{}", deliveryNbr, poNbr);
    FinalizePORequestBody newOsdrPayload =
        finalizePORequestBodyBuilder.buildFrom(deliveryNbr, poNbr, headers);
    newOsdrPayload.setFinalizedTime(new Date());
    newOsdrPayload.setUserId(headers.get(USER_ID_HEADER_KEY).toString());
    newOsdrPayload.setReasonCode(RECEIVING_CORRECTION);

    return newOsdrPayload;
  }

  private boolean isUpdateRejectionFlow(RecordOSDRRequest request) {
    return ObjectUtils.allNotNull(
        request.getRejectedQty(),
        request.getRejectDisposition(),
        request.getRejectionComment(),
        request.getItemNumber(),
        request.getRejectedReasonCode());
  }

  private void persistRejection(
      RecordOSDRRequest request, Receipt receipt, Map<String, Object> headers) {
    String orgUnitId = configUtils.getOrgUnitId();
    Rejections rejections =
        Rejections.builder()
            .deliveryNumber(receipt.getDeliveryNumber())
            .purchaseReferenceNumber(receipt.getPurchaseReferenceNumber())
            .purchaseReferenceLineNumber(receipt.getPurchaseReferenceLineNumber())
            .itemNumber(
                Objects.nonNull(request.getItemNumber())
                    ? Long.valueOf(request.getItemNumber())
                    : null)
            .entireDeliveryReject(request.isRejectEntireDelivery())
            .quantity(receipt.getQuantity())
            .reason(request.getRejectionReason())
            .disposition(request.getRejectDisposition())
            .createUser(headers.get(USER_ID_HEADER_KEY).toString())
            .orgUnitId(nonNull(orgUnitId) ? Integer.parseInt(orgUnitId) : null)
            .claimType(request.getRejectedReasonCode())
            .fullLoadProduceRejection(request.isFullLoadProduceRejection())
            .build();
    LOGGER.info(
        "Persist Rejections {} correlation {}", rejections, headers.get(CORRELATION_ID_HEADER_KEY));
    rejectionsRepository.saveAndFlush(rejections);
  }

  private void sendRejectionEventToGdm(
      RecordOSDRRequest request, Receipt receipt, Map<String, Object> headers)
      throws ReceivingException {
    try {
      // Prepare receive event body
      ReceiveData receiveData =
          ReceiveData.builder()
              .disposition(request.getRejectDisposition())
              .rejectReason(request.getRejectionReason())
              .eventType(RECEIVE_REJECTION)
              .itemNumber(request.getItemNumber())
              .itemDescription(request.getItemDescription())
              .qty(receipt.getFbRejectedQty())
              .claimType(request.getRejectedReasonCode())
              .build();
      ReceiveEventRequestBody receiveEventRequestBody =
          ReceiveEventRequestBody.builder()
              .eventType(RECEIVE_REJECTION)
              .deliveryNumber(receipt.getDeliveryNumber())
              .poNumber(receipt.getPurchaseReferenceNumber())
              .line(String.valueOf(receipt.getPurchaseReferenceLineNumber()))
              .receiveData(receiveData)
              .build();
      // Send event to GDM
      gdmRestApiClient.receivingToGDMEvent(receiveEventRequestBody, headers);
    } catch (GDMRestApiClientException e) {
      LOGGER.error(
          "Failed to call GDM to Rejection event correlation {}",
          headers.get(CORRELATION_ID_HEADER_KEY).toString());
    }
  }

  private void persistRejectionsAndSend(
      RecordOSDRRequest request, Receipt receipt, Map<String, Object> headers)
      throws ReceivingException {
    if (isUpdateRejectionFlow(request)) {
      // Save rejections
      persistRejection(request, receipt, headers);
      // Send Rejection to GDM for History
      sendRejectionEventToGdm(request, receipt, headers);
    }
  }
}
