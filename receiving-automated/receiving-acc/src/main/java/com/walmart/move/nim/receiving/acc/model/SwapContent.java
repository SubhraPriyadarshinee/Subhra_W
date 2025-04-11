package com.walmart.move.nim.receiving.acc.model;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SwapContent implements Serializable {

  private static final long serialVersionUID = 1L;

  @Expose private String purchaseReferenceNumber;
  @Expose private Integer purchaseReferenceLineNumber;
  @Expose private List<SwapDistribution> distributions;
}
