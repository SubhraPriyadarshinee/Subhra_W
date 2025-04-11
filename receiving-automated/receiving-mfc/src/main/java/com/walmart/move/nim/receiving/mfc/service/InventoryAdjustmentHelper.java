package com.walmart.move.nim.receiving.mfc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.mfc.config.MFCManagedConfig;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.model.common.CommonReceiptDTO;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.DecantItem;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.transformer.HawkeyeReceiptTransformer;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.configuration.annotation.ManagedConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryAdjustmentHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(InventoryAdjustmentHelper.class);

  @Autowired private Gson gson;

  @Autowired private HawkeyeReceiptTransformer hawkeyeReceiptTransformer;

  @Autowired private MFCReceivingService mfcReceivingService;

  @Autowired private DecantAuditService decantAuditService;

  @Autowired private MFCContainerService mfcContainerService;

  @Autowired private AsyncPersister asyncPersister;

  @ManagedConfiguration private MFCManagedConfig mfcConfiguration;

  public void processInventoryAdjustment(String message, String eventType) {

    if (Objects.isNull(eventType) || !StringUtils.equalsIgnoreCase(eventType, "DECANTING")) {
      LOGGER.info("Ignoring the kafka message as the eventType = {}", eventType);
      return;
    }

    HawkeyeAdjustment hawkeyeAdjustment = gson.fromJson(message, HawkeyeAdjustment.class);

    Optional<DecantItem> decantItemOptional =
        hawkeyeAdjustment
            .getItems()
            .stream()
            .filter(
                item ->
                    Objects.nonNull(item.getItemTypeCode())
                        || Objects.nonNull(item.getReplenishSubTypeCode()))
            .filter(
                item ->
                    mfcConfiguration.getItemTypeCodes().contains(item.getItemTypeCode())
                        && mfcConfiguration
                            .getReplenishSubTypeCodes()
                            .contains(item.getReplenishSubTypeCode()))
            .findAny();

    if (decantItemOptional.isPresent()) {
      LOGGER.warn(
          "DSD Item detected and hence skipping the adjustment for payload = {} ",
          gson.toJson(hawkeyeAdjustment));
      return;
    }

    if (decantAuditService.findByCorrelationId(TenantContext.getMessageId()).isPresent()) {
      LOGGER.info(
          "Duplicate Hawkeye Adjustment Detected . Hence Ignoring it . MessageId = {} , payload = {}",
          TenantContext.getMessageId(),
          message);
      return;
    }

    AuditStatus auditStatus = AuditStatus.SUCCESS;
    List<Receipt> receiptList = new ArrayList<>();

    try {
      CommonReceiptDTO receiptDTO = hawkeyeReceiptTransformer.transform(hawkeyeAdjustment);
      receiptList = mfcReceivingService.performReceiving(receiptDTO);
    } catch (ReceivingInternalException receivingInternalException) {
      if (receivingInternalException
          .getErrorCode()
          .equalsIgnoreCase(ExceptionCodes.RECEIVING_INTERNAL_ERROR)) {
        LOGGER.error(
            "Unable to select container for the payload = {} and hence going for exception flow ",
            message);
        try {
          mfcContainerService.initiateAutoException(hawkeyeAdjustment, null);
        } catch (Exception exception) {
          LOGGER.error(
              "Unable to process exception flow payload={}",
              gson.toJson(hawkeyeAdjustment),
              exception);
          auditStatus = AuditStatus.FAILURE;
        }
      }
      if (receivingInternalException
          .getErrorCode()
          .equalsIgnoreCase(ExceptionCodes.HAWK_EYE_ERROR)) {
        LOGGER.error(
            "Unable to process message as quantity cannot be decimal", receivingInternalException);
        asyncPersister.publishMetric(
            "wrongUOM_detected", "uwms-receiving", "auto-mfc", "hawkeye_decant");
      }
    } catch (Exception exception) {
      LOGGER.error("Unable to process hawkeye event.", exception);
      auditStatus = AuditStatus.FAILURE;
    }

    decantAuditService.createHawkeyeDecantAudit(hawkeyeAdjustment, auditStatus, receiptList);
  }
}
