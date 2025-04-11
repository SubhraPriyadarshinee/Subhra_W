package com.walmart.move.nim.receiving.core.model.printlabel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GdcReprintLabelData {
  private String trackingId;
  private String description;
  private String createUser;
  @JsonIgnore private Date createTs;
  private String containerException;
  private Integer quantity;
  private Integer vnpkQty;
  private Integer whpkQty;
}
