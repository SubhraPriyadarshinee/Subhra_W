package com.walmart.move.nim.receiving.acc.model;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SwapDistribution implements Serializable {

  private static final long serialVersionUID = 1L;

  @Expose private Integer allocQty;
  @Expose private Map<String, String> item;
  @Expose private String orderId;
}
