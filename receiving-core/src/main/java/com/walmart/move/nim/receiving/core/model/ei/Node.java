package com.walmart.move.nim.receiving.core.model.ei;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class Node {

  private Integer nodeId;
  private Integer nodeDiv;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String nodeCountry;
}
