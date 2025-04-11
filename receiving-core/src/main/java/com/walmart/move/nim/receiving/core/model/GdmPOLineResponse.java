package com.walmart.move.nim.receiving.core.model;

import com.walmart.move.nim.receiving.core.model.gdm.v3.Shipment;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class GdmPOLineResponse {
  private Long deliveryNumber;
  private String deliveryOwnership;
  private String doorNumber;
  private String deliveryStatus;
  private String sealNumber;
  private String hazmatInd;
  private String trailerId;
  private String carrierCode;
  private String scacCode;
  private List<DeliveryDocument> deliveryDocuments;
  private List<Shipment> shipments;
  private String deliveryTypeCode;
  // This is needed for YMS 2.0 delivery update flow
  private String workingUserId;
  private String workingTimeStamp;
  private String doorOpenUserId;
  private List<String> stateReasonCodes;
}
