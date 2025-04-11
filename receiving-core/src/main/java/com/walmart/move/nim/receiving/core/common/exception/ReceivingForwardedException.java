package com.walmart.move.nim.receiving.core.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClientResponseException;

@Getter
public class ReceivingForwardedException extends RuntimeException {
  private HttpStatus httpStatus;
  private String errorResponseBody;
  private String receivingDescription;

  public ReceivingForwardedException(
      HttpStatus httpStatus, String errorResponseBody, String receivingDescription) {
    super(receivingDescription);
    this.httpStatus = httpStatus;
    this.errorResponseBody = errorResponseBody;
    this.receivingDescription = receivingDescription;
  }

  public ReceivingForwardedException(RestClientResponseException e, String receivingDescription) {
    super(receivingDescription);
    this.httpStatus = HttpStatus.valueOf(e.getRawStatusCode());
    this.errorResponseBody = e.getResponseBodyAsString();
    this.receivingDescription = receivingDescription;
  }

  public ReceivingForwardedException(
      RestClientResponseException e, String receivingDescription, Throwable throwable) {
    super(receivingDescription, throwable);
    this.httpStatus = HttpStatus.valueOf(e.getRawStatusCode());
    this.errorResponseBody = e.getResponseBodyAsString();
    this.receivingDescription = receivingDescription;
  }
}
