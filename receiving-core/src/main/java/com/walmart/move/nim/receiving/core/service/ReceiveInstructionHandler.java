package com.walmart.move.nim.receiving.core.service;

import static com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes.CONFIGURATION_ERROR;
import static com.walmart.move.nim.receiving.utils.common.TenantContext.*;
import static com.walmart.move.nim.receiving.utils.constants.ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG;
import static java.lang.String.format;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.InstructionResponse;
import com.walmart.move.nim.receiving.core.model.ReceiveAllRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveAllResponse;
import com.walmart.move.nim.receiving.core.model.ReceiveInstructionRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveIntoOssRequest;
import com.walmart.move.nim.receiving.core.model.ReceiveIntoOssResponse;
import org.springframework.http.HttpHeaders;

public interface ReceiveInstructionHandler {

  InstructionResponse receiveInstruction(
      Long instructionId,
      ReceiveInstructionRequest receiveInstructionRequest,
      HttpHeaders httpHeaders)
      throws ReceivingException;

  InstructionResponse receiveInstruction(
      ReceiveInstructionRequest receiveInstructionRequest, HttpHeaders httpHeaders)
      throws ReceivingException;

  default ReceiveAllResponse receiveAll(
      Long instructionId, ReceiveAllRequest receiveAllRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    throw new ReceivingInternalException(
        CONFIGURATION_ERROR, format(TENANT_NOT_SUPPORTED_ERROR_MSG, getFacilityNum()));
  }

  default ReceiveIntoOssResponse receiveIntoOss(
      Long deliveryNumber, ReceiveIntoOssRequest receiveIntoOssRequest, HttpHeaders httpHeaders)
      throws ReceivingException {
    throw new ReceivingInternalException(
        CONFIGURATION_ERROR, format(TENANT_NOT_SUPPORTED_ERROR_MSG, getFacilityNum()));
  }
}
