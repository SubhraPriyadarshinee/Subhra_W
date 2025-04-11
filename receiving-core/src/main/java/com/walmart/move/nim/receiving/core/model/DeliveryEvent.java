package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import java.util.List;
import lombok.*;

@Data
public class DeliveryEvent {
  private String user;
  private List<AdditionalInfo> additionalInfo;
  private String event;
  private String status;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
  private Date timestamp;
}
