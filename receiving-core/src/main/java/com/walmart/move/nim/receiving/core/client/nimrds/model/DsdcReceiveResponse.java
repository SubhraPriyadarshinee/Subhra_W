package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DsdcReceiveResponse {

  private String message;
  private String errorCode;
  private String slot;
  private String batch;
  private String store;
  private String div;
  private String pocode;
  private String dccarton;
  private String dept;
  private String event;
  private String hazmat;
  private String rcvr_nbr;
  private String po_nbr;
  private String label_bar_code;
  private String packs;
  private String unscanned;
  private String scanned;
  private String auditFlag;
  private String lane_nbr;
  private String sneEnabled;
  private String manifest;
}
