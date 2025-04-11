package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Vendor {

  private String name;
  private Integer number;
}
