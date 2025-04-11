package com.walmart.move.nim.receiving.core.retry;

import com.walmart.move.nim.receiving.core.common.rest.GenericRestConnector;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * * Rest Connector with Retry in place {@inheritDoc}
 *
 * @author sitakant
 */
@Component("retryableRestConnector")
public class RetryableRestConnector extends GenericRestConnector {

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T> ResponseEntity<T> get(String url, Class<T> responseClass) {
    return super.get(url, responseClass);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T, S> ResponseEntity<S> post(
      String url, T request, HttpHeaders headers, Class<S> responseClass) {
    return super.post(url, request, headers, responseClass);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T, S> ResponseEntity<S> post(
      String url, T request, Map<String, String> appHeaders, Class<S> responseClass) {
    return super.post(url, request, appHeaders, responseClass);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
    return super.exchange(url, method, requestEntity, responseType);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T> ResponseEntity<T> exchange(
      String url,
      HttpMethod httpMethod,
      HttpEntity<?> httpEntity,
      ParameterizedTypeReference<T> type) {
    return super.exchange(url, httpMethod, httpEntity, type);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public void delete(String url, String... otherArgs) {
    super.delete(url, otherArgs);
  }

  @Retryable(
      value = {RestClientException.class},
      maxAttemptsExpression = "${rest.max.retry.count}",
      backoff = @Backoff(delayExpression = "${rest.retry.delay}"),
      exceptionExpression = "#{@retryPolicy.canRetry(#root)}")
  @Override
  public <T, S> ResponseEntity<S> put(
      String url, T t, Map<String, String> appHeaders, Class<S> responseClass) {
    return super.put(url, t, appHeaders, responseClass);
  }
}
