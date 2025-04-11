package com.walmart.move.nim.receiving.core.common.exception;

import java.io.Serializable;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorModel implements Serializable {
  private String errorCode;
  private String description;
  private String errorMessage;
}
