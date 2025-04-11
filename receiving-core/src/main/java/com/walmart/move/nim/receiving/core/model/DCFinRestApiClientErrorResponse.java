package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Exception class for all the exceptions thrown from GDMRestApiClient
 *
 * @author v0k00fe
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DCFinRestApiClientErrorResponse extends Exception {

  private static final long serialVersionUID = 1L;

  private final DCFinRestApiClientExceptionMeta meta;
  private final List<String> body;
}
