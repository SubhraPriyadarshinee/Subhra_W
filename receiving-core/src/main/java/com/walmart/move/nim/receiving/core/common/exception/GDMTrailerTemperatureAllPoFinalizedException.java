package com.walmart.move.nim.receiving.core.common.exception;

public class GDMTrailerTemperatureAllPoFinalizedException
    extends GDMTrailerTemperatureBaseException {

  public GDMTrailerTemperatureAllPoFinalizedException(
      String errorCode, String description, String errorMessage) {
    super(errorCode, description, errorMessage);
  }

  public GDMTrailerTemperatureAllPoFinalizedException(
      String errorCode, String description, String errorMessage, Throwable throwable) {
    super(errorCode, description, errorMessage, throwable);
  }
}
