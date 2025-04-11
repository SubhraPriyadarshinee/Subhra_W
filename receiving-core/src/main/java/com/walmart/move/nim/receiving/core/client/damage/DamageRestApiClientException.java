package com.walmart.move.nim.receiving.core.client.damage;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import org.springframework.http.HttpStatus;

/**
 * Exception class for all the exceptions thrown from DamageRestApiClient
 *
 * @author v0k00fe
 */
public class DamageRestApiClientException extends Exception {

  private HttpStatus httpStatus;
  private ErrorResponse errorResponse;

  public DamageRestApiClientException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  public DamageRestApiClientException(
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
