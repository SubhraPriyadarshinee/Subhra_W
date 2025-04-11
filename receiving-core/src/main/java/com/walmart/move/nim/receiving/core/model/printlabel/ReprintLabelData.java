package com.walmart.move.nim.receiving.core.model.printlabel;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReprintLabelData {
  private String trackingId;
  private String description;
  private String createUser;
  @JsonIgnore private Date createTs;
  private String containerException;
  private String parentTrackingId;
  private String ssccNumber;
}
