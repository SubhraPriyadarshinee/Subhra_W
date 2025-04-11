package com.walmart.move.nim.receiving.core.retry;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.UnknownHttpStatusCodeException;

/**
 * * The retry policy for @{@link org.springframework.retry.annotation.Retryable}
 *
 * @see com.walmart.move.nim.receiving.core.common.rest.RestConnector
 * @author sitakant
 */
@Component
public class RetryPolicy {

  /**
   * Retry will happen only for ResourceAccessException, UnknownHttpStatusCodeException and all 5xx
   * error
   *
   * @param restClientException
   * @return boolean
   */
  public boolean canRetry(RestClientException restClientException) {
    if (restClientException instanceof ResourceAccessException) {
      return true;
    } else if (restClientException instanceof UnknownHttpStatusCodeException) {
      return true;
    } else if (restClientException instanceof HttpStatusCodeException) {
      return ((HttpStatusCodeException) restClientException).getStatusCode().is5xxServerError();
    } else {
      return false;
    }
  }
}
