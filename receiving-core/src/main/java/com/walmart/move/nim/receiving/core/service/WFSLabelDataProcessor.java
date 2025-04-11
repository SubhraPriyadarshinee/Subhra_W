package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.ContainerMetaData;
import com.walmart.move.nim.receiving.utils.common.TenantContext;
import freemarker.template.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WFSLabelDataProcessor extends LabelDataProcessor {
  Logger LOG = LoggerFactory.getLogger(RdcLabelDataProcessor.class);

  @Override
  public String populateLabelData(Template jsonTemplate, ContainerMetaData labelDataForReprint) {
    LOG.info("No reprint implementation available for WFS in {}", TenantContext.getFacilityNum());
    return null;
  }
}
