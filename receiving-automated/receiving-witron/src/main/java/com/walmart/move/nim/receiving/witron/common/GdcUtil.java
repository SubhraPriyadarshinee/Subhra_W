package com.walmart.move.nim.receiving.witron.common;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.*;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.NEGATIVE_RECEIVE_QUANTITY_ERROR;
import static com.walmart.move.nim.receiving.witron.constants.GdcConstants.NEGATIVE_REJECT_QUANTITY_ERROR;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.google.gson.JsonObject;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.model.DeliveryDocumentLine;
import com.walmart.move.nim.receiving.core.model.PoLine;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Witron specific Util
 *
 * @author vn50o7n
 */
public class GdcUtil {

  private static final Logger LOG = LoggerFactory.getLogger(GdcUtil.class);

  /**
   * @param inventoryAdjustment_json
   * @return
   */
  public static boolean isValidInventoryAdjustment(JsonObject inventoryAdjustment_json) {
    // Eg: "event": "container.updated",
    final boolean hasEvent = inventoryAdjustment_json.has(INVENTORY_ADJUSTMENT_EVENT);
    if (!hasEvent) return false;
    final boolean isEvent_ContainerUpdated =
        inventoryAdjustment_json
            .get(INVENTORY_ADJUSTMENT_EVENT)
            .getAsString()
            .equalsIgnoreCase(INVENTORY_ADJUSTMENT_EVENT_CONTAINER_UPDATED);
    if (!isEvent_ContainerUpdated) return false;

    // Eg: has json object for "eventObject": {}
    return inventoryAdjustment_json.has(INVENTORY_ADJUSTMENT_EVENT_OBJECT);
  }

  public static String convertDateToUTCZone(Date localDateTime) {
    if (nonNull(localDateTime))
      return String.valueOf(localDateTime.toInstant().atOffset(ZoneOffset.UTC));

    return null;
  }

  public static boolean isAtlasConvertedItem(DeliveryDocumentLine documentLine) {
    return Objects.nonNull(documentLine.getAdditionalInfo())
        ? documentLine.getAdditionalInfo().isAtlasConvertedItem()
        : false;
  }

  public static void validateRequestLineIntoOss(PoLine poLineReq) {
    final Integer receiveQty = poLineReq.getReceiveQty();
    if (nonNull(receiveQty) && receiveQty < 0) {
      throw new ReceivingBadDataException(
          "", String.format(NEGATIVE_RECEIVE_QUANTITY_ERROR, receiveQty));
    }
    final Integer rejectQty = poLineReq.getRejectQty();
    if (nonNull(rejectQty) && rejectQty < 0) {
      throw new ReceivingBadDataException(
          "", String.format(NEGATIVE_REJECT_QUANTITY_ERROR, rejectQty));
    }
  }

  public static void checkIfDeliveryStatusReceivable(DeliveryDocument deliveryDocument)
      throws ReceivingException {
    String errorMessage = null;
    String deliveryStatus = deliveryDocument.getDeliveryStatus().toString();
    String deliveryLegacyStatus = deliveryDocument.getDeliveryLegacyStatus();
    // Delivery which is in Working or Open state and deliveryLegacyStatus with pending problem and
    // ready to receive can be receivable .
    if (GdcUtil.checkIfDeliveryWorkingOrOpenForGdc(deliveryStatus, deliveryLegacyStatus)) return;
    if (DeliveryStatus.PNDFNL.name().equals(deliveryStatus)) {
      errorMessage =
          String.format(
              ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE_REOPEN, deliveryLegacyStatus);
      LOG.error(errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE_REOPEN,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    } else {
      errorMessage =
          String.format(ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE, deliveryLegacyStatus);
      LOG.error(errorMessage);
      throw new ReceivingException(
          errorMessage,
          BAD_REQUEST,
          ReceivingException.DELIVERY_STATE_NOT_RECEIVABLE,
          ReceivingException.CREATE_INSTRUCTION_ERROR_CODE);
    }
  }

  public static boolean checkIfDeliveryWorkingOrOpenForGdc(
      String deliveryStatus, String deliveryLegacyStatus) {
    return (DeliveryStatus.WRK.name().equals(deliveryStatus))
        || (DeliveryStatus.OPN.name().equals(deliveryStatus));
  }

  // Either DC One atlas enabled or Automated DC
  public static boolean isDCCanReceiveOssPO(
      DeliveryDocument deliveryDocument,
      DeliveryDocumentLine deliveryDocumentLine,
      TenantSpecificConfigReader configUtils,
      GDCFlagReader gdcFlagReader) {

    if (isAtlasConvertedItem(deliveryDocumentLine) || gdcFlagReader.isAutomatedDC()) {
      return ReceivingUtils.isOssTransfer(
          deliveryDocument.getPoTypeCode(),
          deliveryDocumentLine.getFromPoLineDCNumber(),
          configUtils);
    }
    return false;
  }

  // Build unique txnId for DCFin posting
  public static String getTxnId(String correlationId, Integer txId) {
    return isNull(correlationId)
        ? String.valueOf(UUID.randomUUID())
        : correlationId + (isNull(txId) ? EMPTY_STRING : (DELIM_DASH + txId));
  }
}
