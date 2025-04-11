package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** @author v0k00fe */
@Component
public class SlottingErrorHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(SlottingErrorHandler.class);

  public void handle(RestClientResponseException e) {
    LOGGER.error(
        "Exception from Smart Slotting for correlationId:{}. Error Response:{}",
        TenantContext.getCorrelationId(),
        e.getResponseBodyAsString());
    if (HttpStatus.valueOf(e.getRawStatusCode()).is4xxClientError()) {
      throw new ReceivingBadDataException(
          ExceptionCodes.SMART_SLOT_BAD_DATA_ERROR, ReceivingConstants.SMART_SLOT_BAD_DATA_ERROR);
    }
    throw new ReceivingInternalException(
        ExceptionCodes.SMART_SLOTTING_NOT_AVAILABLE,
        String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
  }

  public void handle(ResourceAccessException e) {
    throw new ReceivingInternalException(
        ExceptionCodes.SMART_SLOTTING_NOT_AVAILABLE,
        String.format(ReceivingConstants.SMART_SLOTTING_RESPONSE_ERROR_MSG, e.getMessage()));
  }
}
