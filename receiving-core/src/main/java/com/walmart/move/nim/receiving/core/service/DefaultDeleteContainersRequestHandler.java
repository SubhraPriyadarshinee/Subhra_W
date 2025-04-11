package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class DefaultDeleteContainersRequestHandler implements DeleteContainersRequestHandler {

  @Override
  public void deleteContainersByTrackingId(List<String> trackingIds, HttpHeaders httpHeaders) {

    String facilityNum = httpHeaders.getFirst(ReceivingConstants.TENENT_FACLITYNUM);
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, facilityNum));
  }
}
