package com.walmart.move.nim.receiving.core.common.exception;

import lombok.Getter;

@Getter
public abstract class GDMTrailerTemperatureBaseException extends RuntimeException {

  private final String errorCode;
  private final String description;
  private final String errorMessage;

  protected GDMTrailerTemperatureBaseException(
      String errorCode, String description, String errorMessage) {
    super(description);
    this.errorCode = errorCode;
    this.description = description;
    this.errorMessage = errorMessage;
  }

  protected GDMTrailerTemperatureBaseException(
      String errorCode, String description, String errorMessage, Throwable throwable) {
    super(description, throwable);
    this.description = description;
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }
}
