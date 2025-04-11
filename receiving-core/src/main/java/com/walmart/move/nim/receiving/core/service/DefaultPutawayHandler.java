package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.entity.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

@Service
public class DefaultPutawayHandler implements PutawayService {
  private static final Logger log = LoggerFactory.getLogger(DefaultPutawayHandler.class);

  @Override
  public void publishPutaway(Container container, String action, HttpHeaders httpHeaders) {
    log.info("Default implementation of PutawayService - does not publish Putaway message");
  }

  /**
   * Please use putaway update call instead of delete then add to putaway/rtu
   *
   * @param deleteContainer
   * @param addContainer
   * @param httpHeaders
   */
  @Override
  @Deprecated
  public void publishPutawaySequentially(
      Container deleteContainer, Container addContainer, HttpHeaders httpHeaders) {
    log.info("Default implementation of PutawayService - does not publish Putaway message");
  }
}
