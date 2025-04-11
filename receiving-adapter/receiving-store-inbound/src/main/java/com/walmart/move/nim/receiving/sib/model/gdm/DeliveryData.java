package com.walmart.move.nim.receiving.sib.model.gdm;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Date;
import java.util.List;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class DeliveryData {

  private Long deliveryNumber;
  private String status;
  private Date arrivalTime;
  private Date scheduledTime;
  private String trailerId;
  private String doorNumber;
  private List<Vendor> vendor;
  private Integer totalPos;
  private Integer totalPoLines;
  private Integer totalFbq;
  private String type;
  private Integer receivedQty;
  private List<String> channels;
  private List<String> freightType;
  private String carrierScacCode;
  private List<LifeCycleInformation> lifeCycleInformation;
}
