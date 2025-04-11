package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.utils.constants.DeliveryStatus;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeliveryDtls {

  private long deliveryNumber;

  private List<DeliveryDocument> deliveryDocuments;

  private DeliveryStatus deliveryStatus;

  private List<String> stateReasonCodes;
}
