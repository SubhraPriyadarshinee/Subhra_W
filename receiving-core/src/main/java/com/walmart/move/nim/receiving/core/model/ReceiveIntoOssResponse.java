package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReceiveIntoOssResponse {
  ReceiveIntoOssRequest success;
  private ReceiveIntoOssError error;
}
