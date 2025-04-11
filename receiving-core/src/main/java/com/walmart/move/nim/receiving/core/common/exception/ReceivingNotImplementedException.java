package com.walmart.move.nim.receiving.core.common.exception;

public class ReceivingNotImplementedException extends ApplicationBaseException {
  public ReceivingNotImplementedException(String errorCode, String description, Object... values) {
    super(errorCode, description, values);
  }

  public ReceivingNotImplementedException(String errorCode, String description) {
    super(errorCode, description);
  }

  public ReceivingNotImplementedException(
      String errorCode, String description, Throwable throwable) {
    super(errorCode, description, throwable);
  }
}
