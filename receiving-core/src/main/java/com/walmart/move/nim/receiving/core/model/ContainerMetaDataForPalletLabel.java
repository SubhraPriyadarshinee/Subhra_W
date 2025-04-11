package com.walmart.move.nim.receiving.core.model;

import com.google.gson.internal.LinkedTreeMap;
import java.util.Date;
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
public class ContainerMetaDataForPalletLabel implements ContainerMetaData {
  private String trackingId;
  private Map<String, String> destination;
  private Long itemNumber;
  private String gtin;
  private String description;
  private String createUser;
  private Long deliveryNumber;
  private String location;
  private Integer quantity;
  private Integer vnpkQty;
  private long noOfChildContainers;
  private LinkedTreeMap<String, Object> moveData;
  private String lotNumber;
  private Date expiryDate;

  public ContainerMetaDataForPalletLabel(
      String trackingId,
      Long deliveryNumber,
      String location,
      Object destination,
      String createUser) {
    this.trackingId = trackingId;
    this.deliveryNumber = deliveryNumber;
    this.location = location;
    this.destination = (Map<String, String>) destination;
    this.createUser = createUser;
  }

  public ContainerMetaDataForPalletLabel(
      String parentTrackingId,
      Long itemNumber,
      String gtin,
      String description,
      long noOfChildContainers) {
    this.trackingId = parentTrackingId;
    this.itemNumber = itemNumber;
    this.gtin = gtin;
    this.description = description;
    this.noOfChildContainers = noOfChildContainers;
  }

  public ContainerMetaDataForPalletLabel(
      String trackingId,
      Object destination,
      Long itemNumber,
      String gtin,
      String description,
      String createUser,
      Long deliveryNumber,
      String location,
      Integer quantity,
      Integer vnpkQty,
      Object moveData,
      String lotNumber,
      Date expiryDate) {
    this.trackingId = trackingId;
    this.destination = (Map<String, String>) destination;
    this.itemNumber = itemNumber;
    this.gtin = gtin;
    this.description = description;
    this.createUser = createUser;
    this.deliveryNumber = deliveryNumber;
    this.location = location;
    this.quantity = quantity;
    this.vnpkQty = vnpkQty;
    this.moveData = (LinkedTreeMap<String, Object>) moveData;
    this.lotNumber = lotNumber;
    this.expiryDate = expiryDate;
  }
}
