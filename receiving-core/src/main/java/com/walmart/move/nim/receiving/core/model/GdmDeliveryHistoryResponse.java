package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import lombok.*;

@Data
public class GdmDeliveryHistoryResponse {
  private int deliveryNumber;
  private String facilityCountryCode;
  private int facilityNumber;
  private List<DeliveryEvent> deliveryEvents;
  private List<PurchaseorderEvent> purchaseorderEvents;
}
