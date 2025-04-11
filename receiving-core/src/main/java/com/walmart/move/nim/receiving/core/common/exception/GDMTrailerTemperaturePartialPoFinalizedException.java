package com.walmart.move.nim.receiving.core.common.exception;

public class GDMTrailerTemperaturePartialPoFinalizedException
    extends GDMTrailerTemperatureBaseException {
  public GDMTrailerTemperaturePartialPoFinalizedException(
      String errorCode, String description, String errorMessage) {
    super(errorCode, description, errorMessage);
  }

  public GDMTrailerTemperaturePartialPoFinalizedException(
      String errorCode, String description, String errorMessage, Throwable throwable) {
    super(errorCode, description, errorMessage, throwable);
  }
}
