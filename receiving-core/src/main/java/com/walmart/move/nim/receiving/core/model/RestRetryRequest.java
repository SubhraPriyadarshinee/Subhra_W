package com.walmart.move.nim.receiving.core.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.http.HttpMethod;

@Getter
@Setter
@ToString
@AllArgsConstructor
public class RestRetryRequest {
  private String url;
  private HttpMethod httpMethodType;
  private String httpHeaders;
  private String body;
}
