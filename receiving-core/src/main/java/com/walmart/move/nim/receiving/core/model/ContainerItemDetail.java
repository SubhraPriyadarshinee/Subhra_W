package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContainerItemDetail implements Serializable {

  private long itemNumber;
  private long venPkQuantity;
  private long warPkQuantity;
  private String itemUPC;
  private String caseUPC;
  private Integer venPkRatio = 0;
  private Integer warPkRatio = 0;
  private String channelType;

  private List<PODetails> podetails;
}
