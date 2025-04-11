package com.walmart.move.nim.receiving.core.common.exception;

public class ReceivingDataNotFoundException extends ApplicationBaseException {
  public ReceivingDataNotFoundException(String errorCode, String description) {
    super(errorCode, description);
  }

  public ReceivingDataNotFoundException(String errorCode, String description, Throwable throwable) {
    super(errorCode, description, throwable);
  }

  public ReceivingDataNotFoundException(String errorCode, String description, Object... values) {
    super(errorCode, description, values);
  }
}
