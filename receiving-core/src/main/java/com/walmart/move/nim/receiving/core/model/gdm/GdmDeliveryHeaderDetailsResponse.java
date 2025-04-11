package com.walmart.move.nim.receiving.core.model.gdm;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Model to hold response for GDM delivery search headers fields
 *
 * @author sks0013
 */
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode
public class GdmDeliveryHeaderDetailsResponse {

  private Long deliveryNumber;
  private DeliveryStatus status;
  private Instant arrivedTimeStamp;
  private Instant receivingFirstCompletedTimeStamp;
  private Instant receivingCompletedTimeStamp;
  private Instant doorOpenTimeStamp;
  private Instant receivingFirstReceivedTimeStamp;
  private Long los;
  private Long doorOpenTime;
  private Long receivingTime;
  private Long poProcessingTime;
}
