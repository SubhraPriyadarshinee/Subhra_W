package com.walmart.move.nim.receiving.core.model.gdm.v3;

import java.util.Date;
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
public class AsnDelivery {
  private String deliveryNumber;
  private StatusInformation statusInformation;
  private Date scheduled;
  private Date arrivalTimeStamp;
  private Date finalizedTimeStamp;
  private Date doorOpenTime;
  private Date receivingStartedTimeStamp;
  private String trailerId;
}
