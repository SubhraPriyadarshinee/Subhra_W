package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Delivery {
  private Long deliveryNumber;
  private String doorNumber;
  private String status;
  @NotEmpty private List<PurchaseOrder> purchaseOrders;
  private StatusInformation statusInformation;
  private LoadInformation loadInformation;
  private Date appointmentTime;
  private Date arrivalTimeStamp;
  private Date scheduled;
  private Date unloadingCompletedTimeStamp;
  private Date receivingCompletedTimeStamp;
  private Boolean hazmat;
  private String type;
  private List<LifeCycleInformation> lifeCycleInformation;
  private Map<String, Object> additionalInformation;
  private String carrierName;
  private String trailerId;
  private String loadNumber;
  private List<Shipment> shipments;

  //  Appointment start date is mapped to scheduled date for get Delivery API(v3)
  private Date appointmentStartDateTime;
  private Date appointmentEndDateTime;
  //  Arrival time is mapped to Arrival timestamp for get Delivery API(v3)
  private Date arrivalTime;
  private List<String> statusReasonCode;
  private Integer priority;
}
