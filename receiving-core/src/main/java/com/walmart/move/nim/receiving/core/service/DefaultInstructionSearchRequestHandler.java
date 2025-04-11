package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component(ReceivingConstants.DEFAULT_INSTRUCTION_SEARCH_REQUEST_HANDLER)
public class DefaultInstructionSearchRequestHandler implements InstructionSearchRequestHandler {

  @Autowired private InstructionRepository instructionRepository;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private TenantSpecificConfigReader configUtils;

  @Override
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers) {
    List<Instruction> instructionList;
    List<InstructionSummary> instructionSummaryList;
    // If problem tag id provided in search request
    if (instructionSearchRequest.getProblemTagId() != null) {
      // if delivery number also provided in search request then include it in search
      // criteria
      if (instructionSearchRequest.getDeliveryNumber() != null) {
        instructionList =
            instructionRepository.findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
                instructionSearchRequest.getDeliveryNumber(),
                instructionSearchRequest.getProblemTagId());
      }
      // Otherwise just search by problem tag id
      else {
        instructionList =
            instructionRepository.findByProblemTagIdAndInstructionCodeIsNotNull(
                instructionSearchRequest.getProblemTagId());
      }
    }
    // If problem tag id is not provided then search by delivery number
    else {
      if (!instructionSearchRequest.isIncludeCompletedInstructions()) {
        instructionList =
            instructionRepository
                .findByDeliveryNumberAndCompleteTsIsNullAndInstructionCodeIsNotNull(
                    instructionSearchRequest.getDeliveryNumber());
      } else {
        instructionList =
            instructionRepository.findByDeliveryNumberAndInstructionCodeIsNotNull(
                instructionSearchRequest.getDeliveryNumber());
      }
    }
    instructionSummaryList =
        InstructionUtils.convertToInstructionSummaryResponseList(instructionList);
    if (!configUtils.isFeatureFlagEnabled(
        ReceivingConstants.IS_DELIVERY_STATUS_PUBLISH_IN_INSTRUCTION_SEARCH_DISABLED)) {
      if (Objects.nonNull(instructionSearchRequest.getDeliveryNumber())
          && instructionSearchRequest
              .getDeliveryStatus()
              .equalsIgnoreCase(DeliveryStatus.ARV.toString())) {
        deliveryStatusPublisher.publishDeliveryStatus(
            instructionSearchRequest.getDeliveryNumber(),
            DeliveryStatus.OPEN.toString(),
            null,
            headers);
      }
    }

    return instructionSummaryList;
  }
}
