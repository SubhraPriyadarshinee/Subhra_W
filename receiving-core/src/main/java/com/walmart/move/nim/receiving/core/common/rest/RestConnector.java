package com.walmart.move.nim.receiving.core.common.rest;

import com.walmart.move.nim.receiving.core.retry.RetryLogger;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;

/**
 * This is an interface for the REST call . Here, the method in and method out execution time will
 * be logged using {@link Timed}. All the method will have a retry policies and the retry error logs
 * can be found in {@link RetryLogger}
 *
 * @author sitakant
 * @see RetryLogger
 * @see Timed
 */
public interface RestConnector {

  /**
   * * This method will do a get call for the relevent endpoint
   *
   * @param url
   * @param responseClass
   * @param <T>
   * @return ResponseEntity
   */
  <T> ResponseEntity<T> get(String url, Class<T> responseClass);

  /**
   * * This method will post the data into endpoint
   *
   * @param url
   * @param request
   * @param headers
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return ResponseEntity
   */
  <T, S> ResponseEntity<S> post(
      String url, @Nullable T request, Map<String, String> headers, Class<S> responseClass);

  /**
   * * This method will post the data into endpoint with {@link HttpHeaders}
   *
   * @param url
   * @param request
   * @param headers
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return ResponseEntity
   */
  <T, S> ResponseEntity<S> post(
      String url, @Nullable T request, HttpHeaders headers, Class<S> responseClass);

  /**
   * * This method will do a put call with the relevent data to endpoint
   *
   * @param url
   * @param t
   * @param headers
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return ResponseEntity
   */
  <T, S> ResponseEntity<S> put(
      String url, @Nullable T t, Map<String, String> headers, Class<S> responseClass);

  /**
   * * This method is reposible for exchange the call between client and server and given the parsed
   * {@link ParameterizedTypeReference}
   *
   * @param url
   * @param httpMethod
   * @param httpEntity
   * @param type
   * @param <T>
   * @return ResponseEntity
   */
  <T> ResponseEntity<T> exchange(
      String url,
      HttpMethod httpMethod,
      HttpEntity<?> httpEntity,
      ParameterizedTypeReference<T> type);

  /**
   * * This method is reposible for exchange the call between client and server
   *
   * @param url
   * @param method
   * @param requestEntity
   * @param responseType
   * @param <T>
   * @return ResponseEntity
   */
  <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, @Nullable HttpEntity<?> requestEntity, Class<T> responseType);

  /**
   * * This method will do a delete call to the endpoint
   *
   * @param url
   * @param otherArgs
   */
  void delete(String url, @Nullable String... otherArgs);
}
