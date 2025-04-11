package com.walmart.move.nim.receiving.core.model.sorter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@ToString
@Builder
public class SorterMessageAttribute {

  private Boolean recall;
  private Integer item;
  private String upc;
  private String poEvent;
  private String hazmat;
  private Integer deptNbr;
  private String symbotic;
  private String mfc;
}
