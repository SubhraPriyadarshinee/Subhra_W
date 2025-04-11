package com.walmart.move.nim.receiving.core.model.ei;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
public class Nodes {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private Node fromNode;

  private Node toNode;
  private Node destinationNode;
}
