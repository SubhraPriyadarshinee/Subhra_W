package com.walmart.move.nim.receiving.core.model.printlabel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import lombok.Data;

@Data
public class PrintJobResponse {

  private String labelIdentifier;
  private String itemDescription;
  private String userId;
  private Integer palletQty;
  private String palletQtyUOM;
  @JsonIgnore private Date createTS;
  private String ssccNumber;
}
