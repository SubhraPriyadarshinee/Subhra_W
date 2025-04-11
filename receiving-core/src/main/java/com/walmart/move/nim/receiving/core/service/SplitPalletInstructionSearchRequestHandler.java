package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.utils.common.TenantContext.getFacilityNum;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP;

import com.walmart.move.nim.receiving.core.advice.ReadOnlyTransaction;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.entity.Instruction;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.*;
import javax.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.SPLIT_PALLET_INSTRUCTION_SEARCH_REQUEST_HANDLER)
public class SplitPalletInstructionSearchRequestHandler implements InstructionSearchRequestHandler {

  @Autowired private InstructionRepository instructionRepository;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private DefaultInstructionSearchRequestHandler defaultInstructionSearchRequestHandler;

  @Autowired private TenantSpecificConfigReader configUtils;

  @Resource(name = ReceivingConstants.DELIVERY_SERVICE)
  @Autowired
  private DeliveryService deliveryService;

  private boolean isOpenInstruction(InstructionSummary instructionSummary) {
    return !(Objects.nonNull(instructionSummary.getCompleteTs())
        && instructionSummary.getReceivedQuantity() == 0);
  }

  @Override
  @ReadOnlyTransaction
  public List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers) {
    if (instructionSearchRequest.isIncludeInstructionSet() == false) {
      return defaultInstructionSearchRequestHandler.getInstructionSummary(
          instructionSearchRequest, headers);
    }
    List<Instruction> instructionList = Collections.emptyList();
    List<InstructionSummary> instructionSummaryList = new ArrayList<>();
    if (StringUtils.isNotBlank(instructionSearchRequest.getProblemTagId())) {
      if (Objects.nonNull(instructionSearchRequest.getDeliveryNumber())) {
        instructionList =
            instructionRepository.findByDeliveryNumberAndProblemTagIdAndInstructionCodeIsNotNull(
                instructionSearchRequest.getDeliveryNumber(),
                instructionSearchRequest.getProblemTagId());
      } else {
        instructionList =
            instructionRepository.findByProblemTagIdAndInstructionCodeIsNotNull(
                instructionSearchRequest.getProblemTagId());
      }
    } else {
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
    if (CollectionUtils.isNotEmpty(instructionList)) {
      List<InstructionSummary> rawInstructionSummaryList =
          InstructionUtils.convertToInstructionSummaryResponseList(instructionList);
      MultiValuedMap<Long, InstructionSummary> instructionGroup = new ArrayListValuedHashMap<>();
      for (InstructionSummary rawInstructionSummary : rawInstructionSummaryList) {
        instructionGroup.put(rawInstructionSummary.getInstructionSetId(), rawInstructionSummary);
      }
      for (Long key : instructionGroup.keySet()) {
        Collection<InstructionSummary> instructionSummarySetList = instructionGroup.get(key);
        if (Objects.nonNull(key)) {
          Optional<InstructionSummary> firstOpenInstructionOptional =
              instructionSummarySetList.stream().filter(this::isOpenInstruction).findFirst();
          if (firstOpenInstructionOptional.isPresent()) {
            InstructionSummary instruction = firstOpenInstructionOptional.get();

            InstructionSummary instructionWrapper = new InstructionSummary();
            // client dependency, find
            instructionWrapper.setId(key);
            instructionWrapper.setInstructionCode(ReceivingConstants.SPLIT_PALLET);
            instructionWrapper.setProjectedReceiveQty(0);
            instructionWrapper.setReceivedQuantity(0);
            instructionWrapper.setCreateTs(instruction.getCreateTs());
            instructionWrapper.setCreateUserId(instruction.getCreateUserId());
            instructionWrapper.setLastChangeTs(instruction.getLastChangeTs());
            instructionWrapper.setLastChangeUserId(instruction.getLastChangeUserId());
            instructionWrapper.setCompleteUserId(instruction.getCompleteUserId());
            instructionWrapper.setCompleteTs(instruction.getCompleteTs());
            instructionWrapper.setInstructionSetId(key);

            instructionWrapper.setInstructionSet(new ArrayList<>(instructionSummarySetList));
            instructionSummaryList.add(instructionWrapper);
          }
        } else {
          instructionSummaryList.addAll(new ArrayList<>(instructionSummarySetList));
        }
      }
    }
    if (DeliveryStatus.ARV.toString().equalsIgnoreCase(instructionSearchRequest.getDeliveryStatus())
        && Objects.nonNull(instructionSearchRequest.getDeliveryNumber())) {
      // updating GDM delivery status as OPN through api
      if (configUtils.getConfiguredFeatureFlag(
          getFacilityNum().toString(), IS_DELIVERY_UPDATE_STATUS_ENABLED_BY_HTTP, false)) {
        deliveryService.updateDeliveryStatusToOpen(
            instructionSearchRequest.getDeliveryNumber(), headers);
      } else {
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
