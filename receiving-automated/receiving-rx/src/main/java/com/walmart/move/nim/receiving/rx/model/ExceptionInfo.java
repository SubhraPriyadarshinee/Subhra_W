package com.walmart.move.nim.receiving.rx.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ExceptionInfo {
  private String disposition;
  private Instant createdDateTime;
  private String purchaseReference;
  private String vendorNbrDeptSeq;
  private String wmra;
  private String reasonCode;
}
