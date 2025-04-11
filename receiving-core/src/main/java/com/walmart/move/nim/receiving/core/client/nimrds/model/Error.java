package com.walmart.move.nim.receiving.core.client.nimrds.model;

import lombok.Data;

@Data
public class Error {

  private String poNumber;
  private Integer poLine;
  private String message;
  private String errorCode;
  private String item_nbr;
}
