package com.walmart.move.nim.receiving.core.model;

import lombok.Data;

/** @author v0k00fe */
@Data
public class JmsRetryResetRequest extends ActivityWithTimeRangeRequest {

  private int runtimeStatus;
}
