package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.common.ReceivingException;
import com.walmart.move.nim.receiving.core.common.TenantSpecificConfigReader;
import com.walmart.move.nim.receiving.core.common.exception.ReceivingDataNotFoundException;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

public abstract class AbstractContainerService {
  @Autowired private TenantSpecificConfigReader configUtils;

  protected Integer getLabelId(String activityName, String containerType) {
    LabelIdProcessor labelIdProcessor =
        configUtils.getConfiguredInstance(
            TenantContext.getFacilityNum().toString(),
            ReceivingConstants.LABEL_ID_PROCESSOR,
            LabelIdProcessor.class);
    return labelIdProcessor.getLabelId(activityName, containerType);
  }

  public Map<String, Object> getContainerLabelsByTrackingIds(
      List<String> trackingIds, HttpHeaders httpHeaders) throws ReceivingException {
    throw new ReceivingDataNotFoundException("NOT_SUPPORTED", " No implementation found");
  }
}
