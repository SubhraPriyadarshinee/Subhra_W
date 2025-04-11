package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.exception.ExceptionCodes;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingInternalException;
import com.walmart.move.nim.receiving.core.model.printlabel.PrintJobResponse;
import com.walmart.move.nim.receiving.core.model.printlabel.ReprintLabelResponseBody;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DEFAULT_LABEL_SERVICE)
public class DefaultLabelService implements LabelService {

  Logger logger = LoggerFactory.getLogger(DefaultLabelService.class);

  @Override
  public List<PrintJobResponse> getLabels(
      Long deliveryNumber, String userId, boolean labelsByUser) {
    logger.warn("No implementation for reprint in this tenant {}", TenantContext.getFacilityNum());
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }

  @Override
  public ReprintLabelResponseBody getReprintLabelData(
      Set<String> requestedTrackingIds, HttpHeaders httpHeaders) {
    logger.warn("No implementation for reprint in this tenant {}", TenantContext.getFacilityNum());
    throw new ReceivingInternalException(
        ExceptionCodes.CONFIGURATION_ERROR,
        String.format(
            ReceivingConstants.TENANT_NOT_SUPPORTED_ERROR_MSG, TenantContext.getFacilityNum()));
  }
}
