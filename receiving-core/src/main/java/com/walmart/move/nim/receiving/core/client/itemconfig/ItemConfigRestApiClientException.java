package com.walmart.move.nim.receiving.core.client.itemconfig;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import org.springframework.http.HttpStatus;

public class ItemConfigRestApiClientException extends Exception {
  private HttpStatus httpStatus;
  private ErrorResponse errorResponse;

  public ItemConfigRestApiClientException(
      Object errorMessage, HttpStatus httpStatus, String errorCode) {
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
