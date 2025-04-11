package com.walmart.move.nim.receiving.core.handler;

import com.walmart.move.nim.receiving.utils.common.TenantContext;
import io.strati.metrics.annotation.ExceptionCounted;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ErrorHandler;

/**
 * Exception Handler for all JMS related Exception
 *
 * @author sitakant
 */
@Component
public class DefaultJMSExceptionHandler implements ErrorHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJMSExceptionHandler.class);

  @ExceptionCounted(
      name = "jmsListenerExceptionCount",
      level1 = "uwms-receiving",
      level2 = "jmsExceptionHandler")
  @Override
  public void handleError(Throwable throwable) {
    LOGGER.error(
        "Error occured in JMS Listener = {}", ExceptionUtils.getStackTrace(throwable), throwable);
    TenantContext.clear();
  }
}
