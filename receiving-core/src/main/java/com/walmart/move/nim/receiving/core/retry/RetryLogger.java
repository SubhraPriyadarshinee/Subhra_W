package com.walmart.move.nim.receiving.core.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.stereotype.Component;

/**
 * * This is a hook to {@link org.springframework.retry.annotation.Retryable} listener. Here, it
 * will print the a logger for each retry.
 *
 * @author sitakant
 */
@Component
public class RetryLogger extends RetryListenerSupport {

  private static final Logger LOGGER = LoggerFactory.getLogger(RetryLogger.class);

  @Override
  public <T, E extends Throwable> void onError(
      RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
    LOGGER.error(
        "Failed REST call at {}. Error Message = {}   . Number of attempt tried = {}",
        context.getAttribute("context.name"),
        throwable.getMessage(),
        context.getRetryCount());
    super.onError(context, callback, throwable);
  }
}
