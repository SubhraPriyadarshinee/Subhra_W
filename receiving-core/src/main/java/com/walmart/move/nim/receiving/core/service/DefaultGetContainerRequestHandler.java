package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingBadDataException;
import com.walmart.move.nim.receiving.core.entity.Container;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/** @author v0k00fe */
@Component(ReceivingConstants.DEFAULT_GET_CONTAINER_REQUEST_HANDLER)
public class DefaultGetContainerRequestHandler implements GetContainerRequestHandler {
  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultGetContainerRequestHandler.class);

  @Autowired private ContainerService containerService;

  @Override
  public Container getContainerByTrackingId(
      String trackingId,
      boolean includeChilds,
      String uom,
      boolean isReEngageDecantFlow,
      HttpHeaders httpHeaders) {
    try {
      return containerService.getContainerWithChildsByTrackingId(trackingId, includeChilds, uom);
    } catch (ReceivingException receivingException) {
      String errorCode =
          StringUtils.isNotEmpty(receivingException.getErrorResponse().getErrorCode())
              ? receivingException.getErrorResponse().getErrorCode()
              : ExceptionCodes.RECEIVING_INTERNAL_ERROR;
      throw new ReceivingBadDataException(
          errorCode, receivingException.getErrorResponse().getErrorMessage().toString());
    }
  }
}
