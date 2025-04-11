package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.model.OsdrConfigSpecification;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(ReceivingConstants.DEFAULT_OSDR_PROCESSOR)
public class DefaultOsdrProcessor implements OsdrProcessor {
  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOsdrProcessor.class);

  @Override
  public void process(OsdrConfigSpecification osdrConfigSpecification) {
    LOGGER.info("Default implementation of Osdr processor {}", osdrConfigSpecification);
  }
}
