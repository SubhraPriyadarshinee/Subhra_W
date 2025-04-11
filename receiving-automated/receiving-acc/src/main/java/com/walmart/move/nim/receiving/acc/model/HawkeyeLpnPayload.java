package com.walmart.move.nim.receiving.acc.model;

import java.io.Serializable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class HawkeyeLpnPayload implements Serializable {

  private static final long serialVersionUID = 1L;

  private String lpn;
  private String swappedLpn;
  private String destination;
  private String swappedDestination;
  private String groupNumber;
  private int itemNumber;
  private String poNumber;
  private String poType;
}
