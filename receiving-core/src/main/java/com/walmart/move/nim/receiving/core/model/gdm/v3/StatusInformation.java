package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusInformation {
  private String operationalStatus;
  private List<String> statusReasonCode;
  private String status;
}
