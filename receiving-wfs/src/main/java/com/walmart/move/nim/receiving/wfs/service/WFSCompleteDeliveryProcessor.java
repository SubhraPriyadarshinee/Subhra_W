package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.ReceivingUtils;
import com.walmart.move.nim.receiving.core.entity.Receipt;
import com.walmart.move.nim.receiving.core.model.InstructionDetails;
import com.walmart.move.nim.receiving.core.model.gdm.v3.Delivery;
import com.walmart.move.nim.receiving.core.model.gdm.v3.DeliveryList;
import com.walmart.move.nim.receiving.core.service.*;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.DeliveryReasonCodeState;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Component(value = ReceivingConstants.WFS_COMPLETE_DELIVERY_PROCESSOR)
public class WFSCompleteDeliveryProcessor extends DefaultCompleteDeliveryProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(WFSCompleteDeliveryProcessor.class);

  @Override
  public void autoCompleteDeliveries(Integer facilityNumber) throws ReceivingException {

    LOGGER.info("WFSCompleteDeliveryProcessor started for facility {}", facilityNumber);

    int pageNumber = 0;
    DeliveryList listOfDeliveries = null;
    DeliveryList listOfDeliveriesOPN = null;
    List<String> deliveryStatusList = Arrays.asList(DeliveryStatus.OPN.name());
    List<String> statusReasonCodeList =
        Arrays.asList(DeliveryReasonCodeState.DELIVERY_REOPENED.name());

    // 1st Call will retrieve list of WRK deliveries
    String responseWRK =
        deliveryService.fetchDeliveriesByStatus(facilityNumber.toString(), pageNumber);
    listOfDeliveries = gson.fromJson(responseWRK, DeliveryList.class);

    // 2nd call will retrieve list of OPN deliveries with status reason Codes "DELIVERY_REOPENED"
    String responseOPN =
        deliveryService.fetchDeliveriesByStatus(
            deliveryStatusList, statusReasonCodeList, facilityNumber.toString(), pageNumber);
    listOfDeliveriesOPN = gson.fromJson(responseOPN, DeliveryList.class);

    // Merge both WRK + OPN deliveries
    Set<Delivery> deliveryList = new HashSet<>(listOfDeliveries.getData());
    deliveryList.addAll(listOfDeliveriesOPN.getData());

    for (Delivery deliveryDetails : deliveryList) {
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
}
