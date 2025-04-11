package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.ReceivingUtils.isKotlinEnabled;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.InstructionHelperService;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.InstructionDetails;
import com.walmart.move.nim.receiving.core.model.ReceiptSummaryResponse;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(value = ReceivingConstants.DEFAULT_COMPLETE_DELIVERY_PROCESSOR)
public class DefaultCompleteDeliveryProcessor implements CompleteDeliveryProcessor {

  @Autowired protected TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired protected ReceiptRepository receiptRepository;
  @Autowired protected InstructionRepository instructionRepository;
  @Autowired protected ReceiptService receiptService;
  @Autowired protected DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired protected Gson gson;

  @Resource(name = ReceivingConstants.DEFAULT_INSTRUCTION_SERVICE)
  @Autowired
  protected InstructionService instructionService;

  @Autowired protected InstructionHelperService instructionHelperService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  protected DeliveryServiceImpl deliveryService;

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultCompleteDeliveryProcessor.class);

  @Override
  public DeliveryInfo completeDeliveryAndPO(Long deliveryNumber, HttpHeaders headers) {
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public DeliveryInfo completeDelivery(
      Long deliveryNumber, boolean performUnload, HttpHeaders headers) throws ReceivingException {
    validateMasterReceiptsForPoConfirmation(deliveryNumber);
    validateForOpenInstructions(deliveryNumber);
    final boolean isKotlinEnabled = isKotlinEnabled(headers, tenantSpecificConfigReader);
    List<ReceiptSummaryResponse> receiptSummaryResponses =
        receiptService.getReceivedQtySummaryByPOForDelivery(
            deliveryNumber, ReceivingConstants.Uom.EACHES);

    Map<String, Object> deliveryCompleteHeaders =
        constructDeliveryCompleteHeaders(deliveryNumber, headers);

    DeliveryInfo deliveryInfo =
        deliveryStatusPublisher.publishDeliveryStatus(
            deliveryNumber,
            DeliveryStatus.COMPLETE.name(),
            receiptSummaryResponses,
            deliveryCompleteHeaders);

    if (isKotlinEnabled) {
      List<ReceiptSummaryResponse> receiptSummaryResponsesInVnpk =
          receiptService.getReceivedQtySummaryByPOForDelivery(
              deliveryNumber, ReceivingConstants.Uom.VNPK);
      deliveryInfo.setReceipts(receiptSummaryResponsesInVnpk);
    }

    return deliveryInfo;
  }

  public void validateForOpenInstructions(Long deliveryNumber) throws ReceivingException {
    boolean hasOpenInstructions =
        instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                deliveryNumber)
            > 0;
    if (hasOpenInstructions) {
      ErrorResponse errorResponse =
          ErrorResponse.builder()
              .errorMessage(ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE)
              .errorCode(ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_CODE)
              .errorKey(ExceptionCodes.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE)
              .build();
      throw ReceivingException.builder()
          .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
          .errorResponse(errorResponse)
          .build();
    }
  }

  public Map<String, Object> constructDeliveryCompleteHeaders(
      Long deliveryNumber, HttpHeaders headers) {
    Map<String, Object> deliveryCompleteHeaders = ReceivingUtils.getForwardablHeader(headers);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_NUMBER, deliveryNumber);
    deliveryCompleteHeaders.put(ReceivingConstants.DELIVERY_STATUS, DeliveryStatus.COMPLETE.name());
    return deliveryCompleteHeaders;
  }

  public void autoCompleteDeliveries(Integer facilityNumber) throws ReceivingException {
    int pageNumber = 0;
    DeliveryList listOfDeliveries = null;

    String response =
        deliveryService.fetchDeliveriesByStatus(facilityNumber.toString(), pageNumber);
    listOfDeliveries = gson.fromJson(response, DeliveryList.class);

    for (Delivery deliveryDetails : listOfDeliveries.getData()) {
      HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
      if (!CollectionUtils.isEmpty(deliveryDetails.getLifeCycleInformation())
          && deliveryDetails
              .getLifeCycleInformation()
              .get(0)
              .getType()
              .equalsIgnoreCase(ReceivingConstants.stateReasoncodes.get(0))) {

        long actualTimeSinceDoorOpenInHrs =
            ReceivingUtils.convertMiliSecondsInhours(
                (new Date()).getTime()
                    - ReceivingUtils.parseIsoTimeFormat(
                            deliveryDetails.getLifeCycleInformation().get(0).getTime())
                        .getTime());

        int maxAllowedTimeSinceDoorOpenForAutoCompleteInHrs =
            tenantSpecificConfigReader
                .getCcmConfigValue(
                    facilityNumber, ReceivingConstants.RUN_AUTO_COMPLETE_DELIVERY_IN_HOUR)
                .getAsInt();

        if (actualTimeSinceDoorOpenInHrs > maxAllowedTimeSinceDoorOpenForAutoCompleteInHrs) {

          int maxAllowedDeliveryIdleTimeInHours =
              tenantSpecificConfigReader
                  .getCcmConfigValue(
                      facilityNumber, ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR)
                  .getAsInt();

          Receipt recentReceipt =
              receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(
                  deliveryDetails.getDeliveryNumber());

          long actualDeliveryIdleDuration =
              Objects.nonNull(recentReceipt)
                  ? ReceivingUtils.convertMiliSecondsInhours(
                      (new Date().getTime() - recentReceipt.getCreateTs().getTime()))
                  : maxAllowedDeliveryIdleTimeInHours + 1;

          if (actualDeliveryIdleDuration > maxAllowedDeliveryIdleTimeInHours) {

            List<InstructionDetails> instructions =
                instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
                    deliveryDetails.getDeliveryNumber(), facilityNumber);
            if (!instructionHelperService.checkIfListContainsAnyPendingInstruction(instructions)) {
              for (InstructionDetails instruction : instructions) {
                if (instruction.getLastChangeUserId() != null) {
                  httpHeaders.set(
                      ReceivingConstants.USER_ID_HEADER_KEY, instruction.getLastChangeUserId());
                } else {
                  httpHeaders.set(
                      ReceivingConstants.USER_ID_HEADER_KEY, instruction.getCreateUserId());
                }
                instructionService.cancelInstruction(instruction.getId(), httpHeaders);
              }
              httpHeaders.set(
                  ReceivingConstants.USER_ID_HEADER_KEY,
                  ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID);
              deliveryService.completeDelivery(
                  deliveryDetails.getDeliveryNumber(), false, httpHeaders);
              LOGGER.info(
                  "Delivery {} got auto-completed by scheduler for facilityNum {}.",
                  deliveryDetails.getDeliveryNumber(),
                  TenantContext.getFacilityNum());
            }
          } else {
            LOGGER.info(
                "FacilityNumber{}, Delivery {} must be in ideal state for at least {} hrs, hence ignoring auto-complete",
                TenantContext.getFacilityNum(),
                deliveryDetails.getDeliveryNumber(),
                maxAllowedDeliveryIdleTimeInHours);
          }
        } else {
          LOGGER.info(
              "For FacilityNumber{} and Delivery {},Door is opened since {} hrs which is less than max-allowed-time {} hrs , hence ignoring auto-complete",
              TenantContext.getFacilityNum(),
              deliveryDetails.getDeliveryNumber(),
              actualTimeSinceDoorOpenInHrs,
              maxAllowedTimeSinceDoorOpenForAutoCompleteInHrs);
        }
      }
    }
  }

  public void validateMasterReceiptsForPoConfirmation(Long deliveryNumber)
      throws ReceivingException {
    if (tenantSpecificConfigReader.isPoConfirmationFlagEnabled(TenantContext.getFacilityNum())) {
      List<Receipt> receipts =
          receiptRepository.findByDeliveryNumberAndOsdrMasterAndFinalizeTsIsNull(deliveryNumber, 1);

      Set<String> poSet = new HashSet<>();
      for (Receipt receipt : receipts) {
        poSet.add(receipt.getPurchaseReferenceNumber());
      }

      if (!poSet.isEmpty()) {
        ErrorResponse errorResponse =
            ErrorResponse.builder()
                .errorMessage(
                    String.format(
                        ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE,
                        poSet.size()))
                .errorCode(ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_CODE)
                .errorHeader(ReceivingException.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_HEADER)
                .errorKey(ExceptionCodes.COMPLETE_DELIVERY_UNCONFIRMED_PO_ERROR_MESSAGE)
                .values(new Object[] {poSet.size()})
                .build();
        throw ReceivingException.builder()
            .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .errorResponse(errorResponse)
            .build();
      }
    }
  }
}
