package com.walmart.move.nim.receiving.rc.model.container;

import java.util.Date;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RcContainer {
  private String trackingId;
  private String messageId;
  private String location;
  private Long deliveryNumber;
  private String containerType;
  private String containerStatus;
  private Double weight;
  private String weightUOM;
  private Double cube;
  private String cubeUOM;
  private Boolean ctrShippable;
  private Boolean ctrReusable;
  private String inventoryStatus;
  private String orgUnitId;
  private Date createTs;
  private Date lastChangedTs;
  private Date completeTs;
  private Date publishTs;
  private String createUser;
  private String lastChangedUser;
  private Boolean hasChildContainers;
  private String destinationParentTrackingId;
  private String destinationParentContainerType;
  private String destinationTrackingId;
  private String destinationContainerType;
  private String destinationContainerTag;
  private String packageBarCodeValue;
  private String packageBarCodeType;
  private String dispositionType;
  private List<RcContainerItem> contents;
}
