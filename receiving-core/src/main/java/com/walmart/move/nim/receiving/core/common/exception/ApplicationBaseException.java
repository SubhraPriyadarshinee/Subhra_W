package com.walmart.move.nim.receiving.core.common.exception;

import lombok.Getter;

@Getter
public abstract class ApplicationBaseException extends RuntimeException {

  private final String errorCode;
  private final String description;
  private Object[] values;

  public ApplicationBaseException(String errorCode, String description, Object... values) {
    super(description);
    this.errorCode = errorCode;
    this.description = description;
    this.values = values;
  }

  public ApplicationBaseException(String errorCode, String description) {
    super(description);
    this.errorCode = errorCode;
    this.description = description;
  }

  public ApplicationBaseException(String errorCode, String description, Throwable throwable) {
    super(description, throwable);
    this.description = description;
    this.errorCode = errorCode;
  }

  public Throwable getCause() {
    return super.getCause();
  }
}
