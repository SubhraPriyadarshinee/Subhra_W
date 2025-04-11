package com.walmart.move.nim.receiving.core.service.v2;

import com.walmart.move.nim.receiving.core.model.v2.ContainerScanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service(DefaultCreateContainerProcessor.DEFAULT_CREATE_CONTAINER_PROCESSOR)
public class DefaultCreateContainerProcessor implements CreateContainerProcessor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultCreateContainerProcessor.class);

  public static final String DEFAULT_CREATE_CONTAINER_PROCESSOR = "defaultCreateContainerProcessor";

  @Override
  public String createContainer(ContainerScanRequest containerScanRequest) {

    LOGGER.info("DefaultCreateContainerProcessor Invoked");

    return null;
  }
}
