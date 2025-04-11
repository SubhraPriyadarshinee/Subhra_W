package com.walmart.move.nim.receiving.core.model;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Distribution implements Serializable {

  private static final long serialVersionUID = 1L;

  @Expose private Integer allocQty;

  @Expose private Map<String, String> item;

  @Expose private String orderId;

  @Expose private String destCC;

  @Expose private Integer destNbr;

  @Expose private String qtyUom;

  @Expose private String orgTrackingNbr;
}
