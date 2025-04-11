package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsdcReceiveRequest {
  private String _id;
  private String pack_nbr;
  private String manifest;
  private String doorNum;
  private String userId;
}
