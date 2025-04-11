package com.walmart.move.nim.receiving.core.model;

import javax.persistence.Converter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Converter(autoApply = true)
public class ContainerMetaDataForCaseLabel implements ContainerMetaData {
  private String trackingId;

  @JsonDeserialize(using = MapDeserializer.class)
  private Object destination;

  private Long itemNumber;
  private String gtin;
  private String description;
  private String secondaryDescription;
  private Integer vnpkQty;
  private Integer whpkQty;
  private String poDeptNumber;
  private String purchaseReferenceNumber;
  private Integer purchaseReferenceLineNumber;
  private String inboundChannelMethod;

  @JsonDeserialize(using = MapDeserializer.class)
  private Object containerItemMiscInfo;

  private Integer vendorNumber;
  private String createUser;
  private Long deliveryNumber;
  private String location;
  private Integer facilityNum;
}
