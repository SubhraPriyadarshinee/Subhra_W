package com.walmart.move.nim.receiving.core.model.gdm.v2;

import java.io.Serializable;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
public class DeliveryDetails implements Serializable {
  private long deliveryNumber;
  private String deliveryStatus;
  private boolean hazmatInd;
  private String trailerId;
  private String doorNumber;
  private String carrierCode;
  private String scacCode;
  private String dcNumber;
  private String countryCode;
  private List<DeliveryDocument> deliveryDocuments;
  private List<String> stateReasonCodes;
  private String deliveryLegacyStatus;
}
