package com.walmart.move.nim.receiving.core.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model to represent container destination(used in ContainerModel model)
 *
 * @author g0k0072
 */
@Getter
@Setter
@NoArgsConstructor
public class Facility {
  private String countryCode;
  private String buNumber;
  private Integer printBatch;
  private Integer shipLaneNumber;
  private String destType;
}
