package com.walmart.move.nim.receiving.core.builder;

import static com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderErrorCode.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CORRELATION_ID_HEADER_KEY;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.Uom.VNPK;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClient;
import com.walmart.move.nim.receiving.core.client.dcfin.DCFinRestApiClientException;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClient;
import com.walmart.move.nim.receiving.core.client.gdm.GDMRestApiClientException;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderError;
import com.walmart.move.nim.receiving.core.common.exception.ConfirmPurchaseOrderErrorCode;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.*;
import com.walmart.move.nim.receiving.core.model.delivery.meta.DocumentMeta;
import com.walmart.move.nim.receiving.core.model.gdm.v3.FinalizePORequestBody;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.core.service.WitronDeliveryMetaDataService;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Builder class for Confirm Po Action Response
 *
 * @author v0k00fe
 */
@Component
public class ConfirmPoResponseBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(ConfirmPoResponseBuilder.class);

  @Autowired Gson gson;
  @Autowired private GDMRestApiClient gdmRestApiClient;
  @Autowired private ReceiptService receiptService;
  @Autowired private DCFinRestApiClient dcFinRestApiClient;

  @Resource(name = ReceivingConstants.WITRON_DELIVERY_METADATA_SERVICE)
  protected WitronDeliveryMetaDataService deliveryMetaDataService;

  /**
   * Mark all the receipts as finalized
   *
   * @param receipts
   * @param userId
   * @param finalizedTimeStamp
   */
  public void updateReceiptsWithFinalizedDetails(
      List<Receipt> receipts, String userId, Date finalizedTimeStamp) {

    for (Receipt receipt : receipts) {
      receipt.setFinalizedUserId(userId);
      receipt.setFinalizeTs(finalizedTimeStamp);
    }
    receiptService.saveAll(receipts);
  }

  /**
   * Post finalize purchase order to GDM
   *
   * @param deliveryNumber
   * @param purchaseReferenceNumber
   * @param headers
   * @param finalizePORequestBody
   * @throws ReceivingException
   */
  public void finalizePO(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Map<String, Object> headers,
      FinalizePORequestBody finalizePORequestBody)
      throws ReceivingException {
    try {
      gdmRestApiClient.finalizePurchaseOrder(
          deliveryNumber, purchaseReferenceNumber, finalizePORequestBody, headers);
    } catch (GDMRestApiClientException gdmE) {
      if (!GDM_ERROR_PO_FINALIZE_NOT_ALLOWED.equalsIgnoreCase(
          gdmE.getErrorResponse().getErrorCode())) {
        ConfirmPurchaseOrderError confirmPoGdmErr = getErrorValue(GDM_ERROR);
        throw new ReceivingException(
            confirmPoGdmErr.getErrorMessage(),
            gdmE.getHttpStatus(),
            confirmPoGdmErr.getErrorCode());
      }
    }
  }

  /**
   * Post close purchase order to dcFin
   *
   * @param dcFinPOCloseRequestBody
   * @param headers
   * @param isAsync
   * @throws ReceivingException
   */
  public void closePO(
      DCFinPOCloseRequestBody dcFinPOCloseRequestBody, Map<String, Object> headers, boolean isAsync)
      throws ReceivingException {
    try {
      if (isAsync) {
        dcFinRestApiClient.poCloseAsync(dcFinPOCloseRequestBody, headers);
      } else {
        dcFinRestApiClient.poClose(dcFinPOCloseRequestBody, headers);
      }
    } catch (DCFinRestApiClientException e) {
      ConfirmPurchaseOrderError confirmPOError =
          getErrorValue(ConfirmPurchaseOrderErrorCode.DCFIN_ERROR);
      throw new ReceivingException(
          confirmPOError.getErrorMessage(), e.getHttpStatus(), confirmPOError.getErrorCode());
    }
  }

  public DCFinPOCloseRequestBody getDcFinPOCloseRequestBody(
      Long deliveryNumber,
      String purchaseReferenceNumber,
      Map<String, Object> headers,
      int totalBolFbq)
      throws ReceivingException {
    String correlationId =
        headers.getOrDefault(CORRELATION_ID_HEADER_KEY, UUID.randomUUID()).toString();

    List<ReceiptSummaryResponse> receivedQtySummaryByPOForDelivery =
        receiptService.getReceivedQtySummaryByPOForDelivery(deliveryNumber, VNPK);

    // Get DELIVERY_METADATA
    DocumentMeta documentMeta =
        deliveryMetaDataService.findPurchaseOrderDetails(
            deliveryNumber.toString(), purchaseReferenceNumber);

    if (documentMeta == null) {
      LOGGER.error(
          "There is no DELIVERY_METADATA for delivery {}, PO {}",
          deliveryNumber,
          purchaseReferenceNumber);
      ConfirmPurchaseOrderError confirmPOError = getErrorValue(DEFAULT_ERROR);
      throw new ReceivingException(
          confirmPOError.getErrorMessage(), BAD_REQUEST, confirmPOError.getErrorCode());
    }

    DCFinPOCloseRequestBody dcFinPOCloseRequestBody = new DCFinPOCloseRequestBody();
    dcFinPOCloseRequestBody.setTxnId(correlationId + "-" + purchaseReferenceNumber);

    DCFinPOCloseDocumentRequestBody document = new DCFinPOCloseDocumentRequestBody();
    document.setDocumentNum(purchaseReferenceNumber);
    document.setDocType(documentMeta.getPoType());
    document.setDeliveryNum(deliveryNumber.toString());
    document.setDeliveryGateInTs(ReceivingUtils.dateConversionToUTC(new Date()));
    document.setDocumentClosed(true);
    document.setFreightBillQty(totalBolFbq);
    document.setFreightBillQtyUom(VNPK);

    List<DCFinPOCloseDocumentLineRequestBody> documentLineItems = new ArrayList<>();
    for (ReceiptSummaryResponse receiptSummaryResponse : receivedQtySummaryByPOForDelivery) {
      if (purchaseReferenceNumber.equals(receiptSummaryResponse.getPurchaseReferenceNumber())) {
        DCFinPOCloseDocumentLineRequestBody dcFinPOCloseDocumentLineRequestBody =
            new DCFinPOCloseDocumentLineRequestBody();

        dcFinPOCloseDocumentLineRequestBody.setDocLineClosedTs(
            ReceivingUtils.dateConversionToUTC(new Date()));
        dcFinPOCloseDocumentLineRequestBody.setDocumentLineNo(
            receiptSummaryResponse.getPurchaseReferenceLineNumber());
        dcFinPOCloseDocumentLineRequestBody.setPrimaryQty(
            receiptSummaryResponse.getReceivedQty().intValue());
        dcFinPOCloseDocumentLineRequestBody.setLineQtyUOM(receiptSummaryResponse.getQtyUOM());
        dcFinPOCloseDocumentLineRequestBody.setDocumentLineClosed(true);

        documentLineItems.add(dcFinPOCloseDocumentLineRequestBody);
      }
    }

    document.setDocumentLineItems(documentLineItems);
    dcFinPOCloseRequestBody.setDocument(document);
    return dcFinPOCloseRequestBody;
  }
}
