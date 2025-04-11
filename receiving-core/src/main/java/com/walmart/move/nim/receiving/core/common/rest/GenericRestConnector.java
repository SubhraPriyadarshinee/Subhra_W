package com.walmart.move.nim.receiving.core.common.rest;

import com.google.gson.Gson;
import com.walmart.move.nim.receiving.core.config.app.RestTemplateFactory;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.client.RestTemplate;

/**
 * * {@inheritDoc}
 *
 * @see RestConnector
 * @author sitakant
 */
public abstract class GenericRestConnector implements RestConnector {

  private static final Logger LOGGER = LoggerFactory.getLogger(GenericRestConnector.class);

  protected RestTemplate template;

  @Autowired private RestTemplateFactory restTemplateFactory;

  @PostConstruct
  public void init() {
    this.template = restTemplateFactory.provideRestTemplate();
  }

  @Autowired protected Gson gson;

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param responseClass
   * @param <T>
   * @return
   */
  @Override
  public <T> ResponseEntity<T> get(String url, Class<T> responseClass) {
    return template.getForEntity(url, responseClass);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param request
   * @param appHeaders
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return
   */
  @Override
  public <T, S> ResponseEntity<S> post(
      String url, @Nullable T request, Map<String, String> appHeaders, Class<S> responseClass) {

    HttpHeaders headers = new HttpHeaders();

    for (Map.Entry<String, String> header : appHeaders.entrySet()) {
      headers.set(header.getKey(), header.getValue());
    }

    HttpEntity<T> entity = new HttpEntity<>(request, headers);

    return template.exchange(url, HttpMethod.POST, entity, responseClass);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param t
   * @param appHeaders
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return
   */
  @Override
  public <T, S> ResponseEntity<S> put(
      String url, @Nullable T t, Map<String, String> appHeaders, Class<S> responseClass) {

    HttpHeaders headers = new HttpHeaders();

    for (Map.Entry<String, String> header : appHeaders.entrySet()) {
      headers.set(header.getKey(), header.getValue());
    }

    HttpEntity<T> entity = new HttpEntity<>(t, headers);

    return template.exchange(url, HttpMethod.PUT, entity, responseClass);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param otherArgs
   */
  @Override
  public void delete(String url, String... otherArgs) {
    template.delete(url);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param request
   * @param headers
   * @param responseClass
   * @param <T>
   * @param <S>
   * @return
   */
  @Override
  public <T, S> ResponseEntity<S> post(
      String url, T request, HttpHeaders headers, Class<S> responseClass) {
    HttpEntity<T> entity = new HttpEntity<>(request, headers);
    return template.exchange(url, HttpMethod.POST, entity, responseClass);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param httpMethod
   * @param httpEntity
   * @param type
   * @param <T>
   * @return
   */
  @Override
  public <T> ResponseEntity<T> exchange(
      String url,
      HttpMethod httpMethod,
      HttpEntity<?> httpEntity,
      ParameterizedTypeReference<T> type) {

    return template.exchange(url, httpMethod, httpEntity, type);
  }

  /**
   * * {@inheritDoc}
   *
   * @param url
   * @param method
   * @param requestEntity
   * @param responseType
   * @param <T>
   * @return
   */
  @Override
  public <T> ResponseEntity<T> exchange(
      String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) {
    return template.exchange(url, method, requestEntity, responseType);
  }
}
