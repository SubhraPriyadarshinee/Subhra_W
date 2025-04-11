package com.walmart.move.nim.receiving.core.client.dcfin;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;

/**
 * Exception class for all the exceptions thrown from DCFinRestApiClient
 *
 * @author v0k00fe
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DCFinRestApiClientException extends Exception {

  private static final long serialVersionUID = 1L;

  private final HttpStatus httpStatus;
  private final ErrorResponse errorResponse;

  public DCFinRestApiClientException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  public DCFinRestApiClientException(Object errorMessage, HttpStatus httpStatus, String errorCode) {
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
