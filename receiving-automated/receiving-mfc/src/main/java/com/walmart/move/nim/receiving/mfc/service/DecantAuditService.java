package com.walmart.move.nim.receiving.mfc.service;

import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.CSM_DATE_FORMAT;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.InventoryAdjustmentTO;
import com.walmart.move.nim.receiving.core.model.ngr.NGRPack;
import com.walmart.move.nim.receiving.core.service.AsyncPersister;
import com.walmart.move.nim.receiving.mfc.entity.DecantAudit;
import com.walmart.move.nim.receiving.mfc.model.common.AuditStatus;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.DecantItem;
import com.walmart.move.nim.receiving.mfc.model.hawkeye.HawkeyeAdjustment;
import com.walmart.move.nim.receiving.mfc.model.inventory.ItemListItem;
import com.walmart.move.nim.receiving.mfc.model.inventory.MFCInventoryAdjustmentDTO;
import com.walmart.move.nim.receiving.mfc.repositories.DecantAuditRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class DecantAuditService {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecantAuditService.class);

  @Autowired private Gson gson;

  @Autowired private DecantAuditRepository decantAuditRepository;

  @Autowired private AsyncPersister asyncPersister;

  @Transactional
  public void createHawkeyeDecantAudit(
      HawkeyeAdjustment hawkeyeAdjustment, AuditStatus auditStatus, List<Receipt> receiptList) {

    DecantAudit decantAudit =
        DecantAudit.builder()
            .alertRequired(Boolean.TRUE)
            .correlationId(TenantContext.getMessageId())
            .decantedTrackingId(hawkeyeAdjustment.getContainerId())
            .processingTs(processing8601Ts(hawkeyeAdjustment.getSourceCreationTimestamp()))
            .status(auditStatus)
            .receiptId(
                receiptList.stream().map(Receipt::getId).collect(Collectors.toList()).toString())
            .upc(hawkeyeAdjustment.getItems().stream().map(DecantItem::getGtin).findAny().get())
            .build();
    decantAuditRepository.save(decantAudit);
  }

  @InjectTenantFilter
  @Transactional
  public Optional<DecantAudit> findByCorrelationId(String corelationId) {
    return decantAuditRepository.findByCorrelationId(corelationId);
  }

  private Date processing8601Ts(String sourceCreationTimestamp) {
    SimpleDateFormat formatter = new SimpleDateFormat(CSM_DATE_FORMAT);
    Date date = null;
    try {
      date = formatter.parse(sourceCreationTimestamp);
    } catch (ParseException e) {
      LOGGER.warn(
          "Unable to parse ts as 8601 so falling back to system timestamp. ts = {}. {}",
          sourceCreationTimestamp,
          e.getClass() + " : " + e.getMessage());
      date = new Date();
    }
    return date;
  }

  public void createInventoryAdjustmentAudit(
      MFCInventoryAdjustmentDTO mfcInventoryAdjustmentDTO,
      InventoryAdjustmentTO inventoryAdjustmentTO,
      AuditStatus auditStatus,
      List<Receipt> receiptList) {

    Boolean isAlerted = Boolean.FALSE;
    if (AuditStatus.FAILURE.equals(auditStatus) && Objects.nonNull(mfcInventoryAdjustmentDTO)) {
      Optional<ItemListItem> item =
          mfcInventoryAdjustmentDTO
              .getEventObject()
              .getItemList()
              .stream()
              .filter(invItem -> Objects.nonNull(invItem.getAdjustmentTO()))
              .findAny();
      if (item.isPresent()) {
        isAlerted = Boolean.TRUE;
        LOGGER.error(
            "Failure while performing inventory adjustment. Alerted for tracking Id {}",
            mfcInventoryAdjustmentDTO.getEventObject().getTrackingId());
        asyncPersister.publishMetric(
            "mfc_inventory_adjustment_failed",
            "uwms-receiving",
            "mfc",
            "mfc_processInventoryAdjustment");
      }
    }
    DecantAudit decantAudit =
        DecantAudit.builder()
            .alertRequired(isAlerted)
            .correlationId(TenantContext.getMessageIdempotencyId())
            .decantedTrackingId(mfcInventoryAdjustmentDTO.getEventObject().getTrackingId())
            .processingTs(processing8601Ts(mfcInventoryAdjustmentDTO.getOccurredOn()))
            .status(auditStatus)
            .receiptId(
                Objects.isNull(receiptList)
                    ? "NA"
                    : receiptList
                        .stream()
                        .map(Receipt::getId)
                        .collect(Collectors.toList())
                        .toString())
            .build();
    decantAuditRepository.save(decantAudit);
  }

  public DecantAudit createAuditDataDuringNgrProcess(
      String idempotencyKey, NGRPack finalizedPack, AuditStatus auditStatus) {

    DecantAudit decantAudit =
        DecantAudit.builder()
            .alertRequired(Boolean.TRUE)
            .correlationId(idempotencyKey)
            .processingTs(new Date())
            .status(auditStatus)
            .decantedTrackingId(finalizedPack.getPackNumber())
            .build();
    return decantAuditRepository.save(decantAudit);
  }

  public DecantAudit save(DecantAudit decantAudit) {
    return decantAuditRepository.save(decantAudit);
  }
}
