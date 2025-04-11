package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Page {

  private Integer number;
  private Integer size;
  private Integer numberOfElements;
  private Integer totalElements;
  private Integer totalPages;
}
