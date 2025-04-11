package com.walmart.move.nim.receiving.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.walmart.move.nim.receiving.core.model.gdm.v3.LoadInformation;
import com.walmart.move.nim.receiving.core.model.gdm.v3.StatusInformation;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryWithOSDRResponse {
  private Long deliveryNumber;
  private String doorNumber;
  private String type;
  private StatusInformation statusInformation = new StatusInformation();
  private LoadInformation loadInformation = new LoadInformation();
  private OSDR osdr;
  private List<PurchaseOrderWithOSDRResponse> purchaseOrders = new ArrayList<>();
}
