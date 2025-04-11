package com.walmart.move.nim.receiving.core.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Exception class for all the exceptions thrown from GDMRestApiClient
 *
 * @author v0k00fe
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DCFinRestApiClientExceptionMeta extends Exception {

  private static final long serialVersionUID = 1L;

  private final String status;
  private final String statusCode;
  private final String responseText;
  private final ErrorResponse errorResponse;
}
