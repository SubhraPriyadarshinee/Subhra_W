package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.advice.AppComponent;
import com.walmart.move.nim.receiving.core.advice.TimeTracing;
import com.walmart.move.nim.receiving.core.advice.Type;
import com.walmart.move.nim.receiving.core.common.InstructionUtils;
import com.walmart.move.nim.receiving.core.common.JacksonParser;
import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.InstructionErrorCode;
import com.walmart.move.nim.receiving.core.common.rest.RestConnector;
import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import com.walmart.move.nim.receiving.core.model.FdeCreateContainerRequest;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import io.strati.metrics.annotation.ExceptionCounted;
import io.strati.metrics.annotation.Timed;
import java.util.Date;
import javax.annotation.Resource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * This class is responsible to call OF using Retryable rest connector.
 *
 * @author r0s01us
 */
@Service(ReceivingConstants.RETRYABLE_FDE_SERVICE)
public class RetryableFDEService extends FdeService {

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryableFDEService.class);

  @Resource(name = "retryableRestConnector")
  private RestConnector restConnector;

  @Autowired private TenantSpecificConfigReader tenantSpecificConfigReader;

  /**
   * FDE service which will hit OF endpoint to fetch instruction
   *
   * @param fdeCreateContainerRequest
   * @param headers
   * @return
   * @throws ReceivingException
   */
  @Timed(
      name = "OFRetryRequestTimed",
      level1 = "uwms-receiving",
      level2 = "RetryableFDEService",
      level3 = "instructionDetails")
  @ExceptionCounted(
      name = "OFRetryRequestExceptionCount",
      level1 = "uwms-receiving",
      level2 = "RetryableFDEService",
      level3 = "instructionDetails")
  @Override
  @TimeTracing(
      component = AppComponent.ACC,
      type = Type.REST,
      executionFlow = "OF(Retry)-GetInstruction",
      externalCall = true)
  public String receive(FdeCreateContainerRequest fdeCreateContainerRequest, HttpHeaders headers)
      throws ReceivingException {
    headers.set("origin_ts", new Date().toString());
    headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
    ResponseEntity<String> response;
    String uri = findFulfillmentType(fdeCreateContainerRequest);
    String body = JacksonParser.writeValueAsStringExcludeNull(fdeCreateContainerRequest);
    try {
      response = restConnector.post(uri, body, headers, String.class);
    } catch (RestClientResponseException e) {
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));

      instructionError = getErrorMessageFromResponse(e.getResponseBodyAsString());
      if (tenantSpecificConfigReader.isFeatureFlagEnabled(
          ReceivingConstants.ENABLE_ALLOCATION_SERVICE_ERROR_MESSAGES)) {
        String allocationDetailedErrorMessage =
            getAllocationDetailedErrorMessage(e.getResponseBodyAsString());
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
          // return InstructionUtils.fdeExceptionExcludedNoAllocation(instructionError);
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
        // in case, the feature flag for fde service error messages is not enabled, go back to the
        // regular ACC flow
        if (instructionError.getErrorHeader().equalsIgnoreCase("No Allocations")) {
          throw new ReceivingException(
              instructionError.getErrorMessage(),
              HttpStatus.INTERNAL_SERVER_ERROR,
              instructionError.getErrorCode(),
              instructionError.getErrorHeader());
        } else {
          return InstructionUtils.fdeExceptionExcludedNoAllocation(instructionError);
        }
      }
    } catch (ResourceAccessException e) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_NETWORK_ERROR);
      LOGGER.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return InstructionUtils.fdeExceptionExcludedNoAllocation(instructionError);
    }
    if (response == null) {
      instructionError = InstructionErrorCode.getErrorValue(ReceivingException.OF_GENERIC_ERROR);
      return InstructionUtils.fdeExceptionExcludedNoAllocation(instructionError);
    }
    LOGGER.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, body, response.getBody());
    return response.getBody();
  }
}
