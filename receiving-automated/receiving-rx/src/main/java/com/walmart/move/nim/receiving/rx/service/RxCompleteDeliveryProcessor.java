package com.walmart.move.nim.receiving.rx.service;

import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.CompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.ReceiptService;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RxCompleteDeliveryProcessor implements CompleteDeliveryProcessor {

  @Autowired private InstructionRepository instructionRepository;
  @Autowired private ReceiptService receiptService;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;

  @Override
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {

    boolean hasOpenInstructions =
        instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                deliveryNumber)
            > 0;
    if (!hasOpenInstructions) {
      List<ReceiptSummaryResponse> receiptSummaryResponses =
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.EACHES);

      Map<String, Object> deliveryCompleteHeaders = ReceivingUtils.getForwardablHeader(headers);
      deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
      deliveryCompleteHeaders.put(
          ReceivingConstants.DELIVERY_STATUS, DeliveryStatus.COMPLETE.name());

      DeliveryInfo deliveryInfo =
          deliveryStatusPublisher.publishDeliveryStatus(
              deliveryNumber,
              DeliveryStatus.COMPLETE.name(),
              receiptSummaryResponses,
              deliveryCompleteHeaders);

      List<ReceiptSummaryResponse> receiptSummaryResponsesInVnpk =
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.VNPK);
      deliveryInfo.setReceipts(receiptSummaryResponsesInVnpk);

      return deliveryInfo;
    }
    throw new ReceivingBadDataException(
        ExceptionCodes.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE,
        ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
  }

  @Override
  public void autoCompleteDeliveries(Integer facilityNumber) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DeliveryInfo completeDeliveryAndPO(Long deliveryNumber, HttpHeaders headers) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
