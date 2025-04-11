package com.walmart.move.nim.receiving.core.common.exception;

public class ReceivingBadDataException extends ApplicationBaseException {

  public ReceivingBadDataException(String errorCode, String description) {
    super(errorCode, description);
  }

  public ReceivingBadDataException(String errorCode, String description, Throwable throwable) {
    super(errorCode, description, throwable);
  }

  public ReceivingBadDataException(String errorCode, String description, Object... values) {
    super(errorCode, description, values);
  }
}
