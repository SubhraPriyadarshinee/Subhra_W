package com.walmart.move.nim.receiving.rx.service;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.advice.InjectTenantFilter;
import com.walmart.move.nim.receiving.core.common.DeliveryStatusPublisher;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import com.walmart.move.nim.receiving.core.repositories.InstructionRepository;
import com.walmart.move.nim.receiving.core.service.SplitPalletInstructionSearchRequestHandler;
import com.walmart.move.nim.receiving.rx.common.RxUtils;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public class RxInstructionSearchRequestHandler extends SplitPalletInstructionSearchRequestHandler {

  @Autowired private InstructionRepository instructionRepository;
  @Autowired private DeliveryStatusPublisher deliveryStatusPublisher;
  @Autowired private Gson gson;

  @Override
  @InjectTenantFilter
  @Transactional(readOnly = true)
  public List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers) {
    List<InstructionSummary> instructionSummaryList =
        super.getInstructionSummary(instructionSearchRequest, headers);
    return RxUtils.updateProjectedQuantyInInstructionSummary(instructionSummaryList);
  }
}
