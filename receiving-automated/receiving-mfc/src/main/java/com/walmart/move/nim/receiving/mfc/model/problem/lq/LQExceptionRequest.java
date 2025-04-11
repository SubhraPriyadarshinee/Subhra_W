package com.walmart.move.nim.receiving.mfc.model.problem.lq;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class LQExceptionRequest {
  private String createdBy;
  private Contact contact;
  private Container containers;
  private String status;
}
