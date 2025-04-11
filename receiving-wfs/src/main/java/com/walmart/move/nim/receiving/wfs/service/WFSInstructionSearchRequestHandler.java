package com.walmart.move.nim.receiving.wfs.service;

import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.service.InstructionSearchRequestHandler;
import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class WFSInstructionSearchRequestHandler implements InstructionSearchRequestHandler {

  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private TenantSpecificConfigReader configUtils;

  @Override
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers) {
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
    return Collections.emptyList();
  }
}
