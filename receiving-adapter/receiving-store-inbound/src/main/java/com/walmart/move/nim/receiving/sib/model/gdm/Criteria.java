package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class Criteria {

  private List<Long> deliveryNumbers;
  private List<String> deliveryStatusList;
  private String deliveryType;
  private ScheduledTime scheduledTime;
  private ArrivalTime arrivalTime;
  private List<String> warehouseArea;
  private List<String> poStatus;
  private List<String> poLineStatus;
  private List<String> upcs;
  private List<String> poNumbers;
  private String doorNumber;
  private String trailerNumber;
  private List<Integer> itemNumbers;
  private List<String> statusReasonCodes;
  private List<String> channels;
  private String carrierScacCode;
  private String sellerId;
}
