package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.RDC_LABEL_DATA_PROCESSOR)
public class RdcLabelDataProcessor extends LabelDataProcessor {
  Logger logger = LoggerFactory.getLogger(RdcLabelDataProcessor.class);

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData labelDataForReprint) {
    logger.info(
        "No implementation available for Rdc facility num {}", TenantContext.getFacilityNum());
    return null;
  }
}
