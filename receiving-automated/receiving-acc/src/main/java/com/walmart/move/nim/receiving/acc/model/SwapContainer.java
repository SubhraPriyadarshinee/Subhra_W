package com.walmart.move.nim.receiving.acc.model;

import com.google.gson.annotations.Expose;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class SwapContainer implements Serializable {

  private static final long serialVersionUID = 1L;

  @Expose private String trackingId;
  @Expose private String messageId;
  @Expose private Map<String, String> destination;
  @Expose private List<SwapContent> contents;
}
