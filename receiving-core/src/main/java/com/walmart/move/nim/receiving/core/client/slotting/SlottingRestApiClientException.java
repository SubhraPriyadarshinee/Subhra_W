package com.walmart.move.nim.receiving.core.client.slotting;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

/**
 * Exception class for all the exceptions thrown from SlottingRestApiClient
 *
 * @author v0k00fe
 */
@Setter
@Getter
public class SlottingRestApiClientException extends Exception {

  private HttpStatus httpStatus;
  private ErrorResponse errorResponse;

  public SlottingRestApiClientException() {}

  public SlottingRestApiClientException(Object errorMessage, HttpStatus httpStatus) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse("", errorMessage);
  }

  public SlottingRestApiClientException(
      Object errorMessage, HttpStatus httpStatus, String errorCode) {
    super(String.valueOf(errorMessage));
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorCode, errorMessage);
  }

  public SlottingRestApiClientException(
      SlottingPalletBuildErrorResponse response, HttpStatus httpStatus) {
    super(String.valueOf(response.getMessages().get(0).getDesc()));
    this.httpStatus = httpStatus;
    this.errorResponse =
        new ErrorResponse(
            response.getMessages().get(0).getCode(), response.getMessages().get(0).getDesc());
  }

  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  public ErrorResponse getErrorResponse() {
    return errorResponse;
  }
}
