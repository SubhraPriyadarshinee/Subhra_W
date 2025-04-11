package com.walmart.move.nim.receiving.core.common.exception;

public class ReceivingConflictException extends ApplicationBaseException {
  public ReceivingConflictException(String errorCode, String description) {
    super(errorCode, description);
  }

  public ReceivingConflictException(String errorCode, String description, Throwable throwable) {
    super(errorCode, description, throwable);
  }

  public ReceivingConflictException(String errorCode, String description, Object... values) {
    super(errorCode, description, values);
  }
}
