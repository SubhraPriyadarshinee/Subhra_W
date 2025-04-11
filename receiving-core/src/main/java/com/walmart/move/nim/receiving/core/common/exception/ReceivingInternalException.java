package com.walmart.move.nim.receiving.core.common.exception;

public class ReceivingInternalException extends ApplicationBaseException {
  public ReceivingInternalException(String errorCode, String description) {
    super(errorCode, description);
  }

  public ReceivingInternalException(String errorCode, String description, Throwable throwable) {
    super(errorCode, description, throwable);
  }

  public ReceivingInternalException(String errorCode, String description, Object... values) {
    super(errorCode, description, values);
  }
}
