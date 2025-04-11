package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.RestUtils;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service(ReceivingConstants.FDE_SERVICE)
public class FdeServiceImpl extends FdeService {

  @Autowired private RestUtils restUtils;
  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;
  private static final Logger LOGGER = LoggerFactory.getLogger(FdeServiceImpl.class);

  /**
   * FDE service which will hit OF endpoint to fetch instruction
   *
   * @param fdeCreateContainerRequest
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "OFrequestTimed",
      level1 = "uwms-receiving",
      level2 = "FdeService",
      level3 = "instructionDetails")
  @ExceptionCounted(
      name = "OFExceptionCountIncludingNoAllocation",
      level1 = "uwms-receiving",
      level2 = "FdeService",
      level3 = "instructionDetails")
  @TimeTracing(
      component = AppComponent.CORE,
      type = Type.REST,
      executionFlow = "OF-GetInstruction",
      externalCall = true)
  public String receive(FdeCreateContainerRequest fdeCreateContainerRequest, HttpHeaders headers)
      throws ReceivingException {
    String errorMessage = null;
    headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
    TenantContext.get().setAtlasRcvOfCallStart(System.currentTimeMillis());
    ResponseEntity<String> response =
        restUtils.post(
            findFulfillmentType(fdeCreateContainerRequest),
            headers,
            new HashMap<>(),
            gson.toJson(fdeCreateContainerRequest));
    TenantContext.get().setAtlasRcvOfCallEnd(System.currentTimeMillis());
    LOGGER.info(
        "Sent request to OP with data: {}, received response: {}",
        fdeCreateContainerRequest,
        response);
    if (response != null
        && response.getBody() != null
        && response.getStatusCode().series() != HttpStatus.Series.CLIENT_ERROR
        && response.getStatusCode().series() != HttpStatus.Series.SERVER_ERROR) {
      return response.getBody();
    } else if (response != null && response.getStatusCode() == HttpStatus.SERVICE_UNAVAILABLE) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_NETWORK_ERROR);
      errorMessage =
          String.format(
              instructionError.getErrorMessage(),
              (response.getBody() != null ? response.getBody() : ""));
      LOGGER.error(errorMessage);
      return InstructionUtils.fdeExceptionExcludedNoAllocation(
          instructionError, ExceptionCodes.OF_NETWORK_ERROR, null);
    } else if (response != null && response.getBody() != null) {
      errorMessage =
          String.format(ReceivingException.FDE_RECEIVE_FDE_CALL_FAILED, response.getBody());
      LOGGER.error(errorMessage);
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)) {
        // instructionError object error message is immutable, so fetch the message and throw in
        // exception
        String allocationDetailedErrorMessage =
            getAllocationDetailedErrorMessage(response.getBody());
        instructionError = getErrorMessageFromResponse(response.getBody());
        if (instructionError.getErrorHeader().equalsIgnoreCase("No Allocations")) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(
                      StringUtils.isEmpty(allocationDetailedErrorMessage)
                          ? instructionError.getErrorMessage()
                          : allocationDetailedErrorMessage)
                  .errorCode(instructionError.getErrorCode())
                  .errorHeader(instructionError.getLocaliseErrorHeader())
                  .errorKey(ExceptionCodes.NO_ALLOCATION)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        } else {
          String errorKey = getInstructionErrorKey(instructionError);
          return InstructionUtils.fdeExceptionExcludedNoAllocationDetailedAllocationErrorMessage(
              StringUtils.isEmpty(allocationDetailedErrorMessage)
                  ? instructionError.getErrorMessage()
                  : allocationDetailedErrorMessage,
              instructionError.getErrorCode(),
              instructionError.getLocaliseErrorHeader(),
              errorKey,
              null);
        }
      } else {
        instructionError = getErrorMessageFromResponse(response.getBody());
        if (instructionError.getErrorHeader().equalsIgnoreCase("No Allocations")) {
          ErrorResponse errorResponse =
              ErrorResponse.builder()
                  .errorMessage(instructionError.getErrorMessage())
                  .errorCode(instructionError.getErrorCode())
                  .errorHeader(instructionError.getLocaliseErrorHeader())
                  .errorKey(ExceptionCodes.NO_ALLOCATION)
                  .build();
          throw ReceivingException.builder()
              .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
              .errorResponse(errorResponse)
              .build();
        } else {
          String errorKey = getInstructionErrorKey(instructionError);
          return InstructionUtils.fdeExceptionExcludedNoAllocation(
              instructionError, errorKey, null);
        }
      }
    } else {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
      errorMessage = String.format(instructionError.getErrorMessage(), response);
      LOGGER.error(errorMessage);
      return InstructionUtils.fdeExceptionExcludedNoAllocation(
          instructionError, ExceptionCodes.OF_GENERIC_ERROR, response);
    }
  }
}
