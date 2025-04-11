package com.walmart.move.nim.receiving.core.common;

import com.walmart.move.nim.receiving.core.config.app.RestTemplateFactory;
import com.walmart.move.nim.receiving.utils.constants.ReceivingConstants;
import java.net.URI;
import java.util.Date;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Utility to expose common rest call functions
 *
 * @deprecated new class is SimpleRestConnector
 * @see com.walmart.move.nim.receiving.core.common.rest.SimpleRestConnector
 */
@Deprecated
@Component
public class RestUtils {
  private static final Logger log = LoggerFactory.getLogger(RestUtils.class);
  @Autowired private RestTemplateFactory restTemplateFactory;
  private RestTemplate restTemplate;

  @PostConstruct
  public void init() {
    this.restTemplate = restTemplateFactory.provideRestTemplate();
  }

  /**
   * @param url request url
   * @param headers headers map that need to be passed
   * @param pathParams path params map that need to be replaced
   * @return response entity containing headers and body
   * @throws ReceivingException
   */
  public ResponseEntity<String> get(
      String url, HttpHeaders headers, Map<String, String> pathParams) {
    HttpEntity<String> entity = new HttpEntity<>(headers);
    ResponseEntity<String> response = null;
    URI uri = replacePathParams(url, pathParams);
    try {
      response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
      if (response != null) {
        log.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, "", response.getBody());
      }
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(e.getRawStatusCode())
          .headers(e.getResponseHeaders())
          .body(e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          "",
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .headers((HttpHeaders) null)
          .body(ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE);
    }
    return response;
  }

  /**
   * @param url request url
   * @param headers headers map that need to be passed
   * @param pathParams path params map that need to be replaced
   * @param body request body
   * @return response entity containing headers and body
   */
  public ResponseEntity<String> post(
      String url, HttpHeaders headers, Map<String, String> pathParams, String body) {
    headers.set("origin_ts", new Date().toString());
    HttpEntity<String> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = null;
    URI uri = replacePathParams(url, pathParams);
    try {
      response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
      if (response != null) {
        log.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, body, response.getBody());
      }
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(e.getRawStatusCode())
          .headers(e.getResponseHeaders())
          .body(e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .headers((HttpHeaders) null)
          .body(ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE);
    }
    return response;
  }

  /**
   * Replaces path params
   *
   * @param url
   * @param pathParams
   * @return URI after replacing path params
   */
  private URI replacePathParams(String url, Map<String, String> pathParams) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
    URI urlAfterPathParamsReplacement = null;
    if (pathParams != null && !pathParams.isEmpty()) {
      urlAfterPathParamsReplacement = builder.buildAndExpand(pathParams).toUri();
    } else {
      urlAfterPathParamsReplacement = builder.build().toUri();
    }
    return urlAfterPathParamsReplacement;
  }

  /**
   * @param url request url
   * @param headers headers map that need to be passed
   * @param pathParams path params map that need to be replaced
   * @param body request body
   * @return response entity containing headers and body
   * @throws ReceivingException
   */
  public ResponseEntity<String> put(
      String url, HttpHeaders headers, Map<String, String> pathParams, String body) {
    HttpEntity<String> request = new HttpEntity<>(body, headers);
    ResponseEntity<String> response = null;
    URI uri = replacePathParams(url, pathParams);
    try {
      response = restTemplate.exchange(uri, HttpMethod.PUT, request, String.class);
      if (response != null) {
        log.info(ReceivingConstants.RESTUTILS_INFO_MESSAGE, uri, body, response.getBody());
      }
    } catch (RestClientResponseException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          e.getResponseBodyAsString(),
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(e.getRawStatusCode())
          .headers(e.getResponseHeaders())
          .body(e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      log.error(
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE,
          uri,
          body,
          ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE,
          ExceptionUtils.getStackTrace(e));
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .headers((HttpHeaders) null)
          .body(ReceivingConstants.RESTUTILS_ERROR_MESSAGE_FOR_NO_RESPONSE);
    }
    return response;
  }
}
