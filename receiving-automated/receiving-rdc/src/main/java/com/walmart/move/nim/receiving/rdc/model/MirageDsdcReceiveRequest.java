package com.walmart.move.nim.receiving.rdc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MirageDsdcReceiveRequest {
  private String packNumber;
  private String printerId;
  private String equipmentId;
  private String deliveryNumber;
  private String doorNumber;
}
