package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

public class DefaultReceiveInstructionHandler implements ReceiveInstructionHandler {
  private static final Logger logger =
      LoggerFactory.getLogger(DefaultReceiveInstructionHandler.class);

  @Override
  public InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);
    return null;
  }

  @Override
  public InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders) {
    logger.info(
        "Default implementation of receive instruction for instruction request {}",
        receiveInstructionRequest);
    return null;
  }
}
