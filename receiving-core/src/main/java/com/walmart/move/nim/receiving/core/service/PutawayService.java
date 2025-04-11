package com.walmart.move.nim.receiving.core.service;

import com.walmart.move.nim.receiving.core.entity.Container;
import org.springframework.http.HttpHeaders;

public interface PutawayService {

  void publishPutaway(Container container, String action, HttpHeaders httpHeaders);

  void publishPutawaySequentially(
      Container deleteContainer, Container addContainer, HttpHeaders httpHeaders);
}
