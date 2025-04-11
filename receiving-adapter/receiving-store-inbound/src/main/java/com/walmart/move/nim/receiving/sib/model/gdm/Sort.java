package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Sort {

  private Long deliveryNumbers;
  private Date scheduledTime;
  private Date arrivalTime;
  private String doorNumber;
}
