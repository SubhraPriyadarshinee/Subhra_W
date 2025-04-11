package com.walmart.move.nim.receiving.core.model;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReceivingLoadState {
  private Date changeTime;
  private String status;
  private String changeUser;
}
