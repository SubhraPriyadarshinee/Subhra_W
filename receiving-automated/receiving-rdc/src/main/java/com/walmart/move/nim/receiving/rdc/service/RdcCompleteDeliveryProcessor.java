package com.walmart.move.nim.receiving.rdc.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.entity.DeliveryMetaData;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.helper.ProcessInitiator;
import com.walmart.move.nim.receiving.core.message.common.ReceivingEvent;
import com.walmart.move.nim.receiving.core.model.DeliveryInfo;
import com.walmart.move.nim.receiving.core.model.InstructionDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.model.osdr.OsdrSummary;
import com.walmart.move.nim.receiving.core.model.yms.v2.ProgressUpdateDTO;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.repositories.ReceiptRepository;
import com.walmart.move.nim.receiving.core.service.CompleteDeliveryProcessor;
import com.walmart.move.nim.receiving.core.service.DeliveryServiceImpl;
import com.walmart.move.nim.receiving.rdc.constants.RdcConstants;
import com.walmart.move.nim.receiving.rdc.message.publisher.RdcMessagePublisher;
import com.walmart.move.nim.receiving.rdc.utils.RdcDeliveryStatusUtils;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public class RdcCompleteDeliveryProcessor implements CompleteDeliveryProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(RdcCompleteDeliveryProcessor.class);

  @Autowired private InstructionRepository instructionRepository;
  @Autowired private RdcMessagePublisher rdcMessagePublisher;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  @Autowired private ReceiptRepository receiptRepository;
  @Autowired private Gson gson;
  @Autowired private ProcessInitiator processInitiator;

  @Resource(name = RdcConstants.RDC_INSTRUCTION_SERVICE)
  private RdcInstructionService rdcInstructionService;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  private DeliveryServiceImpl deliveryService;

  @Resource(name = RdcConstants.RDC_OSDR_SERVICE)
  private RdcOsdrService rdcOsdrSummaryService;

  @Resource(name = RdcConstants.RDC_DELIVERY_METADATA_SERVICE)
  private RdcDeliveryMetaDataService deliveryMetaDataService;

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
    LOGGER.debug("Fetching open instructions for the delivery {}", deliveryNumber);
    Long openInstructionCount =
        instructionRepository.countByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
            deliveryNumber);
    if (openInstructionCount == 0) {
      LOGGER.info(
          "Publishing osdr summary and delivery complete message for delivery {}", deliveryNumber);

      OsdrSummary osdrSummaryResponse =
          rdcOsdrSummaryService.getOsdrSummary(deliveryNumber, headers);
      DeliveryInfo deliveryInfo =
          publishDeliveryStatusAndReceipts(deliveryNumber, osdrSummaryResponse, headers);
      // yms update complete hooks
      processYMSUnloadingEvent(deliveryNumber);
      return deliveryInfo;
    }
    throw new ReceivingBadDataException(
        ExceptionCodes.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE,
        ReceivingException.COMPLETE_DELIVERY_OPEN_INSTRUCTION_ERROR_MESSAGE);
  }

  private void processYMSUnloadingEvent(Long deliveryNumber) {
    if (!tenantSpecificConfigReader.getConfiguredFeatureFlag(
        String.valueOf(TenantContext.getFacilityNum()),
        ReceivingConstants.YMS_UNLOADING_PROGRESS_ON_DELIVERY_COMPLETE,
        false)) {
      LOGGER.info("Sending Unloading message during deliveryComplete is not enabled ");
      return;
    }
    LOGGER.info(
        "Sending Unloading message during deliveryComplete is processing for delivery={}",
        deliveryNumber);

    ProgressUpdateDTO progressUpdateDTO =
        ProgressUpdateDTO.builder()
            .deliveryNumber(deliveryNumber)
            .deliveryStatus(DeliveryStatus.COMPLETE)
            .build();

    Map<String, Object> additionalAttribute = new HashMap<>();

    ReceivingEvent receivingEvent =
        ReceivingEvent.builder()
            .payload(ReceivingUtils.stringfyJson(progressUpdateDTO))
            .name(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
            .additionalAttributes(additionalAttribute)
            .processor(ReceivingConstants.BEAN_DELIVERY_PROGRESS_UPDATE_PROCESSOR)
            .build();
    LOGGER.info("Going to initiate delivery update progress for delivery={}", deliveryNumber);
    processInitiator.initiateProcess(receivingEvent, additionalAttribute);

    LOGGER.info(
        "Sending Unloading message during deliveryComplete is completed for delivery={}",
        deliveryNumber);
  }

  /**
   * This method auto-completes the RDC deliveries based on the following eligibility criteria 1.
   * Get valid deliveries in GDM with WORKING status 2. Process auto complete only for valid GDM
   * Life cycle status (ReceivingConstants.stateReasoncodes) 3. Determine delivery ideal duration
   * based on the CCM configured values (Auto complete deliveries older than 48 hours with max ideal
   * duration of 4 hours) 4. Cancel any uncompleted instructions exists on a delivery 5. Get OSDR
   * summary for the delivery; deliveries with no open docktags and no audit pending eligible for
   * auto-complete
   *
   * <p>Publish OSDR delivery receipts and complete delivery status update to GDM for the eligible
   * auto complete deliveries.
   *
   * @param facilityNumber
   * @throws ReceivingException
   */
  public void autoCompleteDeliveries(Integer facilityNumber) throws ReceivingException {
    int pageNumber = 0;
    DeliveryList listOfDeliveries = null;

    String response =
        deliveryService.fetchDeliveriesByStatus(facilityNumber.toString(), pageNumber);
    listOfDeliveries = gson.fromJson(response, DeliveryList.class);

    HttpHeaders httpHeaders = ReceivingUtils.getHeaders();
    for (Delivery deliveryDetails : listOfDeliveries.getData()) {
      if (CollectionUtils.isNotEmpty(deliveryDetails.getLifeCycleInformation())
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

          int maxAllowedDeliveryIdealTimeInHours =
              tenantSpecificConfigReader
                  .getCcmConfigValue(
                      facilityNumber, ReceivingConstants.MAX_DELIVERY_IDLE_DURATION_IN_HOUR)
                  .getAsInt();

          Receipt recentReceipt =
              receiptRepository.findFirstByDeliveryNumberOrderByCreateTsDesc(
                  deliveryDetails.getDeliveryNumber());

          long actualDeliveryIdealDuration =
              Objects.nonNull(recentReceipt)
                  ? ReceivingUtils.convertMiliSecondsInhours(
                      (new Date().getTime() - recentReceipt.getCreateTs().getTime()))
                  : maxAllowedDeliveryIdealTimeInHours + 1;

          if (actualDeliveryIdealDuration > maxAllowedDeliveryIdealTimeInHours) {
            List<InstructionDetails> instructions =
                instructionRepository.getUncompletedInstructionDetailsByDeliveryNumber(
                    deliveryDetails.getDeliveryNumber(), facilityNumber);
            for (InstructionDetails instruction : instructions) {
              if (instruction.getLastChangeUserId() != null) {
                httpHeaders.set(
                    ReceivingConstants.USER_ID_HEADER_KEY, instruction.getLastChangeUserId());
              } else {
                httpHeaders.set(
                    ReceivingConstants.USER_ID_HEADER_KEY, instruction.getCreateUserId());
              }
              rdcInstructionService.cancelInstruction(instruction.getId(), httpHeaders);
            }
            httpHeaders.set(
                ReceivingConstants.USER_ID_HEADER_KEY,
                ReceivingConstants.AUTO_COMPLETE_DELIVERY_USERID);

            OsdrSummary osdrSummaryResponse =
                rdcOsdrSummaryService.getOsdrSummary(
                    deliveryDetails.getDeliveryNumber(), httpHeaders);
            if (!osdrSummaryResponse.getAuditPending()
                && osdrSummaryResponse.getOpenDockTags().getCount().equals(0)) {
              publishDeliveryStatusAndReceipts(
                  deliveryDetails.getDeliveryNumber(), osdrSummaryResponse, httpHeaders);
            }

            LOGGER.info(
                "Delivery {} got auto-completed by scheduler for facilityNum {}.",
                deliveryDetails.getDeliveryNumber(),
                TenantContext.getFacilityNum());
          } else {
            LOGGER.info(
                "FacilityNumber{}, Delivery {} must be in ideal state for at least {} hrs, hence ignoring auto-complete",
                TenantContext.getFacilityNum(),
                deliveryDetails.getDeliveryNumber(),
                maxAllowedDeliveryIdealTimeInHours);
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

  private DeliveryInfo publishDeliveryStatusAndReceipts(
      Long deliveryNumber, OsdrSummary osdrSummaryResponse, HttpHeaders headers) {
    Map<String, Object> deliveryStatusHeaders =
        RdcDeliveryStatusUtils.getDeliveryStatusMessageHeaders(headers, deliveryNumber);

    DeliveryInfo deliveryInfo = null;
    if (CollectionUtils.isNotEmpty(osdrSummaryResponse.getSummary())
        || Boolean.parseBoolean(
            headers.getFirst(ReceivingConstants.GDM_FORCE_COMPLETE_DELIVERY_HEADER))) {
      rdcMessagePublisher.publishDeliveryReceipts(osdrSummaryResponse, deliveryStatusHeaders);
      deliveryInfo =
          rdcMessagePublisher.publishDeliveryStatus(
              deliveryNumber, DeliveryStatus.COMPLETE.name(), deliveryStatusHeaders);
      deliveryInfo.setReceipts(ReceivingUtils.getReceiptSummaryResponseForRDC(osdrSummaryResponse));
      deliveryMetaDataService.updateDeliveryMetaData(
          deliveryNumber, deliveryInfo.getDeliveryStatus());
    } else {
      deliveryInfo = processDeliveryUnloadingCompleteMessage(deliveryNumber, deliveryStatusHeaders);
    }

    return deliveryInfo;
  }

  private DeliveryInfo processDeliveryUnloadingCompleteMessage(
      Long deliveryNumber, Map<String, Object> messageHeaders) {
    LOGGER.info("Publishing unloading complete status update for delivery {}", deliveryNumber);
    DeliveryMetaData deliveryMetaData =
        deliveryMetaDataService.findDeliveryMetaData(deliveryNumber);
    DeliveryInfo deliveryStatusMessage = new DeliveryInfo();
    deliveryStatusMessage.setDeliveryNumber(deliveryNumber);
    deliveryStatusMessage.setDeliveryStatus(DeliveryStatus.UNLOADING_COMPLETE.name());

    if (Objects.nonNull(deliveryMetaData)) {
      String doorNumber =
          Objects.nonNull(deliveryMetaData.getDoorNumber())
              ? deliveryMetaData.getDoorNumber()
              : StringUtils.EMPTY;
      String trailerNumber =
          Objects.nonNull(deliveryMetaData.getTrailerNumber())
              ? deliveryMetaData.getTrailerNumber()
              : StringUtils.EMPTY;
      deliveryStatusMessage.setDoorNumber(doorNumber);
      deliveryStatusMessage.setTrailerNumber(trailerNumber);
      if (!deliveryMetaData
          .getDeliveryStatus()
          .name()
          .equalsIgnoreCase(DeliveryStatus.UNLOADING_COMPLETE.name())) {
        rdcMessagePublisher.publishDeliveryStatus(deliveryStatusMessage, messageHeaders);
        deliveryMetaDataService.updateDeliveryMetaData(
            deliveryNumber, DeliveryStatus.UNLOADING_COMPLETE.name());
      }
    }

    deliveryStatusMessage.setReceipts(new ArrayList<>());
    return deliveryStatusMessage;
  }
}
