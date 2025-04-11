package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;

@Data
public class HazmatInfo {

  private int hazmat_id;
  private int trnsp_mode_code;
  private String dot_haz_class_code;
}
