package com.walmart.move.nim.receiving.core.common.exception;

public class GDMTrailerTemperatureServiceFailedException
    extends GDMTrailerTemperatureBaseException {
  public GDMTrailerTemperatureServiceFailedException(
      String errorCode, String description, String errorMessage) {
    super(errorCode, description, errorMessage);
  }

  public GDMTrailerTemperatureServiceFailedException(
      String errorCode, String description, String errorMessage, Throwable throwable) {
    super(errorCode, description, errorMessage, throwable);
  }
}
