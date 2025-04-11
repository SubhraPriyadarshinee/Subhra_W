package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component(ReceivingConstants.DEFAULT_LABEL_ID_PROCESSOR)
public class DefaultLabelIdProcessor implements LabelIdProcessor {
  private Logger logger = LoggerFactory.getLogger(DefaultLabelIdProcessor.class);

  @Override
  public Integer getLabelId(String activityName, String containerType) {
    logger.info("No Label configured for facility number: {}", TenantContext.getFacilityNum());
    return null;
  }
}
