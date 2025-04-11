package com.walmart.move.nim.receiving.core.model.gdm;

import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.ToString;

/**
 * Model to hold request for GDM delivery search headers fields
 *
 * @author sks0013
 */
@ToString
@Builder
public class GdmDeliveryHeaderDetailsSearchFields {

  private List<Long> deliveryNumbers;

  private Instant receivingCompletedStartTime;

  private Instant receivingCompletedEndTime;

  private GdmDeliveryHeaderDetailsPageRequest page;
}
