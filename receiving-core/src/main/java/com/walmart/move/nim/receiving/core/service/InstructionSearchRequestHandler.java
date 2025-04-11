package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.model.InstructionSearchRequest;
import com.walmart.move.nim.receiving.core.model.InstructionSummary;
import java.util.List;
import java.util.Map;

public interface InstructionSearchRequestHandler {

  List<InstructionSummary> getInstructionSummary(
      InstructionSearchRequest instructionSearchRequest, Map<String, Object> headers)
      throws ReceivingException;
}
