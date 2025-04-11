package com.walmart.move.nim.receiving.core.client.iqs;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import org.springframework.http.HttpStatus;

public class IqsRestApiClientException extends Exception {

  private final HttpStatus httpStatus;
  private final ErrorResponse errorResponse;

  public IqsRestApiClientException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  public IqsRestApiClientException(Object errorMessage, HttpStatus httpStatus, String errorCode) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorCode, errorMessage);
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public ErrorResponse getErrorResponse() {
    return errorResponse;
  }
}
