package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
public class ErrorResponse implements Serializable {
  private String errorCode;
  private Object errorMessage;
  private String errorHeader;
  @JsonIgnore private Object[] values;
  @JsonIgnore private String errorKey;
  private String localisedErrorMessage;
  // Use for any extra info or send fixed values or send json
  // like:- "errorInfo": {"expiryThresholdDate": "11/30/2020"} or array inside a json
  private Object errorInfo;

  public ErrorResponse(
      String errorCode,
      Object errorMessage,
      String errorHeader,
      Object[] values,
      String errorKey,
      String localisedErrorMessage,
      Object errorInfo) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.errorHeader = errorHeader;
    this.values = values;
    this.errorKey = errorKey;
    this.localisedErrorMessage = localisedErrorMessage;
    this.errorInfo = errorInfo;
  }

  public ErrorResponse(String errorCode, Object errorMessage) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
  }

  public ErrorResponse(String errorCode, Object errorMessage, String errorHeader) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.errorHeader = errorHeader;
  }

  public ErrorResponse(String errorCode, Object errorMessage, String errorHeader, Object object) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.errorHeader = errorHeader;
    this.errorInfo = object;
  }

  public ErrorResponse(
      String errorCode,
      Object errorMessage,
      String errorHeader,
      Object errorInfo,
      String errorKey,
      Object... values) {
    this.errorCode = errorCode;
    this.errorMessage = errorMessage;
    this.errorHeader = errorHeader;
    this.errorInfo = errorInfo;
    this.errorKey = errorKey;
    this.values = values;
  }
}
