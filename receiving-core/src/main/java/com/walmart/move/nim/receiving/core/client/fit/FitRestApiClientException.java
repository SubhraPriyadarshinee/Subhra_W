package com.walmart.move.nim.receiving.core.client.fit;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import org.springframework.http.HttpStatus;

/**
 * Exception class for all the exceptions thrown from DamageRestApiClient
 *
 * @author v0k00fe
 */
public class FitRestApiClientException extends Exception {

  private HttpStatus httpStatus;
  private ErrorResponse errorResponse;

  public FitRestApiClientException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  public FitRestApiClientException(Object errorMessage, HttpStatus httpStatus, String errorCode) {
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
