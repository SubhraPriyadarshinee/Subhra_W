package com.walmart.move.nim.receiving.core.common.exception;

public class GDMTrailerTemperatureBadRequestException extends GDMTrailerTemperatureBaseException {
  public GDMTrailerTemperatureBadRequestException(
      String errorCode, String description, String errorMessage) {
    super(errorCode, description, errorMessage);
  }

  public GDMTrailerTemperatureBadRequestException(
      String errorCode, String description, String errorMessage, Throwable throwable) {
    super(errorCode, description, errorMessage, throwable);
  }
}
