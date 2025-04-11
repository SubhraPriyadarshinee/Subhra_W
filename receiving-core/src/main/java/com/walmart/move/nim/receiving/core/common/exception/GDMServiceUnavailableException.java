/** */
package com.walmart.move.nim.receiving.core.common.exception;

import com.walmart.move.nim.receiving.core.model.ErrorResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.client.ResourceAccessException;

/**
 * The GDMServiceUnavailableException wraps service unavailability({@link ResourceAccessException})
 * and enriches them with a custom error code and user readable error messages.
 *
 * @author m0g028p
 * @since uwms-receiving-1.4
 */
@Getter
@Setter
public class GDMServiceUnavailableException extends RuntimeException {

  private static final long serialVersionUID = -2398956835986115810L;
  private final ErrorResponse errorResponse;

  private static final String DEFAULT_ERROR_MESSAGE = "We’re having trouble reaching GDM now";

  public GDMServiceUnavailableException(String errorCode) {
    super();
    this.errorResponse = new ErrorResponse(errorCode, DEFAULT_ERROR_MESSAGE);
  }

  public GDMServiceUnavailableException(String errorCode, String message) {
    super(message);
    this.errorResponse = new ErrorResponse(errorCode, message);
  }

  public GDMServiceUnavailableException(Object errorMessage, String errorCode, String errorHeader) {
    super(String.valueOf(errorMessage));
    this.errorResponse = new ErrorResponse(errorCode, errorMessage, errorHeader);
  }

  public GDMServiceUnavailableException(String errorCode, Throwable cause) {
    super(cause);
    this.errorResponse = new ErrorResponse(errorCode, DEFAULT_ERROR_MESSAGE);
  }

  public GDMServiceUnavailableException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorResponse = new ErrorResponse(errorCode, message);
  }

  public ErrorResponse getErrorResponse() {
    return this.errorResponse;
  }
}
