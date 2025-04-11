package com.walmart.move.nim.receiving.core.common.exception;

import lombok.Getter;

@Getter
public class AzureBlobException extends Exception {

  private AzureBlobErrorCode errorCode;

  public AzureBlobException(AzureBlobErrorCode errorCode, String errorMessage) {
    super(errorMessage);
    this.errorCode = errorCode;
  }
}
