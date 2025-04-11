package com.walmart.move.nim.receiving.core.model;

import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TransportationModes implements Serializable {
  private Mode mode;
  private DotHazardousClass dotHazardousClass;
  private String dotRegionCode;
  private List<String> pkgInstruction;
  private String dotIdNbr;
  private Double limitedQty;
  private String properShipping;
}
