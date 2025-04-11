package com.walmart.move.nim.receiving.core.model;

import java.util.List;
import java.util.Map;
import javax.persistence.Converter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Converter(autoApply = true)
public class ContainerMetaDataForNonNationalPoLabel implements ContainerMetaData {
  private String trackingId;
  private Map<String, String> destination;
  private String outboundChannelMethod;
  private String createUser;
  private Long deliveryNumber;
  private String location;
  private Map<String, String> containerMiscInfo;
  private String purchaseReferenceNumber;
  private Integer quantity;
  private int totalReceivedQty;
  private List<String> poList;

  public ContainerMetaDataForNonNationalPoLabel(
      String trackingId,
      Object destination,
      String outboundChannelMethod,
      String createUser,
      Long deliveryNumber,
      String location,
      Object containerMiscInfo,
      String purchaseReferenceNumber,
      int quantity) {
    this.trackingId = trackingId;
    this.destination = (Map<String, String>) destination;
    this.outboundChannelMethod = outboundChannelMethod;
    this.createUser = createUser;
    this.deliveryNumber = deliveryNumber;
    this.location = location;
    this.containerMiscInfo = (Map<String, String>) containerMiscInfo;
    this.purchaseReferenceNumber = purchaseReferenceNumber;
    this.quantity = quantity;
  }
}
