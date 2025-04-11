package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.DeliveryDocument;
import com.walmart.move.nim.receiving.core.service.InstructionPersisterService;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class WFSInstructionUtils {
  // TODO: rename this to WFSInstructionHelperService, and migrate methods from
  // WFSInsturctionService here
  @Autowired private InstructionPersisterService instructionPersisterService;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  private static final Logger LOGGER = LoggerFactory.getLogger(WFSInstructionUtils.class);

  public Boolean isCancelInstructionAllowed(Instruction instruction) {
    if (Objects.nonNull(instruction.getCompleteTs())) {
      LOGGER.error("Instruction: {} is already complete", instruction.getId());
      return Boolean.FALSE;
    }
    return Boolean.TRUE;
  }

  @Transactional
  @InjectTenantFilter
  public void persistForCancelInstructions(List<Instruction> instructions)
      throws ReceivingException {
    if (CollectionUtils.isNotEmpty(instructions)) {
      instructionPersisterService.saveAllInstruction(instructions);
    }
  }

  /**
   * Sets <code>isHazmat</code> field to True of each <code>DeliveryDocumentLine</code> of each
   * <code>DeliveryDocument</code> inside argument list of delivery documents
   *
   * @param deliveryDocuments
   */
  public void setIsHazmatTrueInDeliveryDocumentLines(List<DeliveryDocument> deliveryDocuments) {
    // TODO: make this static
    if (tenantSpecificConfigReader.isFeatureFlagEnabled(
        ReceivingConstants.WFS_TM_HAZMAT_CHECK_ENABLED)) {
      CollectionUtils.emptyIfNull(deliveryDocuments)
          .stream()
          .filter(Objects::nonNull)
          .forEach(
              deliveryDocument -> {
                CollectionUtils.emptyIfNull(deliveryDocument.getDeliveryDocumentLines())
                    .stream()
                    .filter(Objects::nonNull)
                    .forEach(
                        deliveryDocumentLine -> deliveryDocumentLine.setIsHazmat(Boolean.TRUE));
              });
    }
  }

  public void checkIfDeliveryStatusReceivable(DeliveryDocument deliveryDocuments_gdm)
      throws ReceivingBadDataException {
    String deliveryStatus = deliveryDocuments_gdm.getDeliveryStatus().toString();
    String deliveryLegacyStatus = deliveryDocuments_gdm.getDeliveryLegacyStatus();
    // Delivery which is in Working or Open state without pending problem, can be receivable .
    if (ReceivingUtils.checkIfDeliveryWorkingOrOpen(deliveryStatus, deliveryLegacyStatus)) return;
    LOGGER.error(
        "Delivery {} is in {} state. To continue receiving, please reopen the delivery from GDM and retry",
        deliveryDocuments_gdm.getDeliveryNumber(),
        deliveryDocuments_gdm.getDeliveryStatus());
    String deliveryNumber = String.valueOf(deliveryDocuments_gdm.getDeliveryNumber());

    if (Arrays.asList(DeliveryStatus.FNL.name(), DeliveryStatus.PNDFNL.name())
        .contains(deliveryStatus)) {
      throw new ReceivingBadDataException(
          ExceptionCodes.DELIVERY_NOT_RECEIVABLE_REOPEN,
          String.format(
              ReceivingConstants.DELIVERY_NOT_RECEIVABLE_REOPEN_ERROR_MESSAGE,
              deliveryNumber,
              deliveryStatus),
          deliveryNumber,
          deliveryStatus);
    }
    throw new ReceivingBadDataException(
        ExceptionCodes.DELIVERY_NOT_RECEIVABLE,
        String.format(
            ReceivingConstants.DELIVERY_NOT_RECEIVABLE_ERROR_MESSAGE,
            deliveryNumber,
            deliveryStatus),
        deliveryNumber,
        deliveryStatus);
  }
}
